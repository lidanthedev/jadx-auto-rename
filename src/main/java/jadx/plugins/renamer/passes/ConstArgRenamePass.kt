package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.InsnUtils
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Logger

class ConstArgRenamePass : JadxDecompilePass {
	private val logger = Logger.getLogger("ConstArgRenamePass")

	private lateinit var root: RootNode
	private val callerMethods = HashSet<MethodNode>()

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"ConstArgRename",
			"Rename nodes from constant invoke arguments"
		).after("SimplifyVisitor")
	}

	override fun init(root: RootNode) {
		this.root = root
		callerMethods.clear()
		for (rule in RULES) {
			for (resolved in resolveRuleMethods(root, rule)) {
				callerMethods.addAll(resolved.useIn)
			}
		}
	}

	override fun visit(cls: ClassNode?): Boolean {
		return true
	}

	override fun visit(mth: MethodNode) {
		if (mth.contains(AFlag.DONT_RENAME) || mth.isNoCode) {
			return
		}
		if (callerMethods.isNotEmpty() && !callerMethods.contains(mth)) {
			return
		}
		val parentCls = mth.parentClass ?: return
		if (parentCls.contains(AFlag.DONT_RENAME)) {
			return
		}

		for (block in mth.basicBlocks) {
			for (insn in block.instructions) {
				if (insn.type != InsnType.INVOKE || insn !is InvokeNode) {
					continue
				}
				for (rule in RULES) {
					if (!matchesRule(insn.callMth, rule)) {
						continue
					}
					applyRule(mth, parentCls, insn, rule)
				}
			}
		}
	}

	private fun applyRule(mth: MethodNode, parentCls: ClassNode, invoke: InvokeNode, rule: Rule) {
		if (rule.nameArgIndex < 0 || invoke.argsCount <= rule.nameArgIndex) {
			return
		}
		val nameArg = invoke.getArg(rule.nameArgIndex)
		val constName = InsnUtils.getConstValueByArg(root, nameArg) as? String ?: return
		val normalizedName = normalizeName(constName) ?: return

		when (rule.target) {
			RenameTarget.CLASS -> renameClass(parentCls, normalizedName, rule)
			RenameTarget.METHOD -> renameMethod(mth, normalizedName, rule)
			RenameTarget.ASSIGNEE -> renameAssignee(invoke, normalizedName)
			RenameTarget.ARGUMENT -> renameArgument(invoke, rule.otherArgIndex, normalizedName)
		}
	}

	private fun renameClass(cls: ClassNode, newName: String, rule: Rule) {
		if (!NameMapper.isValidIdentifier(newName) || cls.name == newName || newName.length < 3) {
			return
		}
		try {
			if (cls.classInfo.hasAlias() && RenameUtils.isClassUserRenamed(cls)) {
				return
			}
		} catch (_: Exception) {
			// ignore
		}
		logger.info("Rename class '$cls' to '$newName' by const arg rule ${rule.id}")
		cls.rename(newName)
		RenameReasonAttr.forNode(cls).append("from ConstArgRenamePass: ${rule.id}")
	}

	private fun renameMethod(mth: MethodNode, newName: String, rule: Rule) {
		if (!NameMapper.isValidIdentifier(newName) || mth.name == newName) {
			return
		}
		if (mth.methodInfo.isConstructor || mth.methodInfo.isClassInit) {
			return
		}
		try {
			if (mth.methodInfo.hasAlias() && RenameUtils.isMethodUserRenamed(mth)) {
				return
			}
		} catch (_: Exception) {
			// ignore
		}
		logger.info("Rename method '$mth' to '$newName' by const arg rule ${rule.id}")
		mth.rename(newName)
		RenameReasonAttr.forNode(mth).append("from ConstArgRenamePass: ${rule.id}")
	}

	private fun renameAssignee(invoke: InvokeNode, newName: String) {
		val result = invoke.result ?: return
		renameRegister(result, newName)
	}

	private fun renameArgument(invoke: InvokeNode, argIndex: Int, newName: String) {
		if (argIndex < 0 || invoke.argsCount <= argIndex) {
			return
		}
		val arg = invoke.getArg(argIndex)
		if (arg is RegisterArg) {
			renameRegister(arg, newName)
		}
	}

	private fun renameRegister(arg: RegisterArg, newName: String) {
		val sVar = arg.sVar ?: return
		val codeVar = sVar.codeVar
		if (codeVar.isThis || codeVar.name == newName) {
			return
		}
		codeVar.name = newName
	}

	private fun normalizeName(input: String): String? {
		val trimmed = input.trim()
		if (trimmed.isEmpty()) {
			return null
		}
		if (NameMapper.isValidAndPrintable(trimmed)) {
			return trimmed
		}
		val token = IDENTIFIER_REGEX.find(trimmed)?.value ?: return null
		if (!NameMapper.isValidAndPrintable(token)) {
			return null
		}
		return token
	}

	private fun matchesRule(callMth: MethodInfo, rule: Rule): Boolean {
		if (rule.classNames.isNotEmpty() && !rule.classNames.contains(callMth.declClass.fullName)) {
			return false
		}
		if (!rule.methodNames.contains(callMth.name)) {
			return false
		}
		if (rule.minArgs >= 0 && callMth.argsCount < rule.minArgs) {
			return false
		}
		return true
	}

	private fun resolveRuleMethods(root: RootNode, rule: Rule): List<MethodNode> {
		if (rule.classNames.isEmpty()) {
			return emptyList()
		}
		val resolved = ArrayList<MethodNode>()
		for (className in rule.classNames) {
			val clsNode = root.resolveRawClass(className) ?: continue
			for (method in clsNode.methods) {
				if (!rule.methodNames.contains(method.methodInfo.name)) {
					continue
				}
				if (rule.minArgs >= 0 && method.methodInfo.argsCount < rule.minArgs) {
					continue
				}
				resolved.add(method)
			}
		}
		return resolved
	}

	private enum class RenameTarget {
		CLASS,
		METHOD,
		ASSIGNEE,
		ARGUMENT,
	}

	private data class Rule(
		val id: String,
		val classNames: Set<String>,
		val methodNames: Set<String>,
		val target: RenameTarget,
		val nameArgIndex: Int,
		val otherArgIndex: Int = -1,
		val minArgs: Int = -1,
	)

	companion object {
		private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

		private val RULES = listOf(
			Rule(
				id = "generic.checkNotNullParameter",
				classNames = emptySet(),
				methodNames = setOf("checkNotNullParameter"),
				target = RenameTarget.ARGUMENT,
				nameArgIndex = 1,
				otherArgIndex = 0,
				minArgs = 2,
			),
			Rule(
				id = "generic.notNull",
				classNames = emptySet(),
				methodNames = setOf("notNull"),
				target = RenameTarget.ARGUMENT,
				nameArgIndex = 1,
				otherArgIndex = 0,
				minArgs = 2,
			),
			Rule(
				id = "preconditions.checkNotNull",
				classNames = setOf(
					"com.google.common.base.Preconditions",
					"androidx.core.util.Preconditions",
					"java.util.Objects",
				),
				methodNames = setOf("checkNotNull", "requireNonNull"),
				target = RenameTarget.ARGUMENT,
				nameArgIndex = 1,
				otherArgIndex = 0,
				minArgs = 2,
			),
			Rule(
				id = "generic.get",
				classNames = emptySet(),
				methodNames = setOf("get"),
				target = RenameTarget.ASSIGNEE,
				nameArgIndex = 0,
				minArgs = 1,
			),
			Rule(
				id = "json.get",
				classNames = setOf("org.json.JSONObject"),
				methodNames = setOf("get", "opt", "optJSONObject", "optJSONArray", "optString"),
				target = RenameTarget.ASSIGNEE,
				nameArgIndex = 0,
				minArgs = 1,
			),
			Rule(
				id = "log.tag",
				classNames = setOf("android.util.Log"),
				methodNames = setOf("d", "i", "w", "e", "v", "wtf", "println"),
				target = RenameTarget.CLASS,
				nameArgIndex = 0,
				minArgs = 1,
			),
		)
	}
}


