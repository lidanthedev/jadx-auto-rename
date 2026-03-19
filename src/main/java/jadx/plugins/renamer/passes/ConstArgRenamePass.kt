package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.InsnUtils
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Logger

class ConstArgRenamePass(
	private val ruleOptions: RuleOptions = RuleOptions(),
) : JadxDecompilePass {
	private val logger = Logger.getLogger("ConstArgRenamePass")

	private lateinit var root: RootNode
	private val callerMethods = HashSet<MethodNode>()
	private val nullCheckCache = HashMap<MethodInfo, Boolean>()
	private val throwHelperCache = HashMap<MethodInfo, Boolean>()
	private var enabledRules = emptyList<Rule>()

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"ConstArgRename",
			"Rename nodes from constant invoke arguments"
		).after("SimplifyVisitor")
	}

	override fun init(root: RootNode) {
		this.root = root
		callerMethods.clear()
		nullCheckCache.clear()
		throwHelperCache.clear()
		enabledRules = RULES.filter(::isRuleEnabled)
		for (rule in enabledRules) {
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
		val parentCls = mth.parentClass
		if (parentCls.contains(AFlag.DONT_RENAME)) {
			return
		}

		for (block in mth.basicBlocks) {
			for (insn in block.instructions) {
				if (insn.type != InsnType.INVOKE || insn !is InvokeNode) {
					continue
				}
				for (rule in enabledRules) {
					if (!matchesRule(insn.callMth, rule)) {
						continue
					}
					applyRule(mth, parentCls, insn, rule)
				}
				if (ruleOptions.obfuscatedNullCheckRules) {
					applyObfuscatedNullCheckRule(insn)
				}
			}
		}
	}

	private fun applyObfuscatedNullCheckRule(invoke: InvokeNode) {
		if (!invoke.isStaticCall || invoke.argsCount < 2) {
			return
		}
		if (!isLikelyObfuscatedNullCheck(invoke.callMth)) {
			return
		}
		val constName = InsnUtils.getConstValueByArg(root, invoke.getArg(1)) as? String ?: return
		val normalizedName = normalizeName(constName) ?: return
		renameArgument(invoke, 0, normalizedName)
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

	private fun isLikelyObfuscatedNullCheck(callMth: MethodInfo): Boolean {
		return nullCheckCache.getOrPut(callMth) {
			val mth = root.resolveMethod(callMth) ?: return@getOrPut false
			if (mth.isNoCode || !mth.accessFlags.isStatic || mth.methodInfo.returnType != ArgType.VOID) {
				return@getOrPut false
			}
			val argTypes = mth.methodInfo.argumentsTypes
			if (argTypes.size < 2 || argTypes[1] != ArgType.STRING) {
				return@getOrPut false
			}
			val insns = prepareMethodAndGetInsns(mth) ?: return@getOrPut false
			val argRegs = runCatching { mth.argRegs }.getOrNull() ?: return@getOrPut false
			if (argRegs.size < 2) {
				return@getOrPut false
			}
			val firstArgReg = argRegs[0].regNum
			val secondArgReg = argRegs[1].regNum

			val hasFirstArgNullCheck = insns.any { insn ->
				if (insn.type != InsnType.IF || insn.argsCount < 2) {
					false
				} else {
					(isRegNum(insn.getArg(0), firstArgReg) && insn.getArg(1).isZeroConst()) ||
						(isRegNum(insn.getArg(1), firstArgReg) && insn.getArg(0).isZeroConst())
				}
			}
			if (!hasFirstArgNullCheck) {
				return@getOrPut false
			}

			var secondArgUsed = false
			var hasThrowingPath = false
			for (insn in insns) {
				if (insn.type == InsnType.THROW) {
					hasThrowingPath = true
				}
				if (insn is InvokeNode && insn.getArgList().any { arg -> isRegNum(arg, secondArgReg) }) {
					secondArgUsed = true
					if (isThrowHelper(insn.callMth)) {
						hasThrowingPath = true
					}
				}
			}
			secondArgUsed && hasThrowingPath
		}
	}

	private fun isThrowHelper(callMth: MethodInfo): Boolean {
		return throwHelperCache.getOrPut(callMth) {
			val mth = root.resolveMethod(callMth) ?: return@getOrPut false
			val insns = prepareMethodAndGetInsns(mth) ?: return@getOrPut false
			insns.any { it.type == InsnType.THROW }
		}
	}

	private fun prepareMethodAndGetInsns(mth: MethodNode): List<jadx.core.dex.nodes.InsnNode>? {
		mth.instructions?.let { insnArr ->
			return insnArr.filterNotNull()
		}
		mth.basicBlocks?.let { blocks ->
			if (blocks.isNotEmpty()) {
				return blocks.flatMap { block -> block.instructions }
			}
		}
		if (!mth.isLoaded) {
			runCatching { mth.load() }.onFailure { return null }
		}
		return mth.instructions?.filterNotNull()
	}

	private fun isRegNum(arg: InsnArg, regNum: Int): Boolean {
		if (arg is RegisterArg) {
			return arg.regNum == regNum
		}
		if (arg is InsnWrapArg) {
			return arg.wrapInsn.argList.any { nested -> isRegNum(nested, regNum) }
		}
		return false
	}

	private fun matchesRule(callMth: MethodInfo, rule: Rule): Boolean {
		if (rule.classNames.isNotEmpty() && !rule.classNames.contains(callMth.declClass.fullName)) {
			return false
		}
		if (!rule.matchesMethodName(callMth.name)) {
			return false
		}
		if (rule.minArgs >= 0 && callMth.argsCount < rule.minArgs) {
			return false
		}
		return true
	}

	private fun isRuleEnabled(rule: Rule): Boolean {
		return when (rule.group) {
			RuleGroup.NULL_CHECK -> ruleOptions.nullCheckRules
			RuleGroup.JSON -> ruleOptions.jsonRules
			RuleGroup.LOG -> ruleOptions.logRules
		}
	}

	private fun resolveRuleMethods(root: RootNode, rule: Rule): List<MethodNode> {
		if (rule.classNames.isEmpty()) {
			return emptyList()
		}
		val resolved = ArrayList<MethodNode>()
		for (className in rule.classNames) {
			val clsNode = root.resolveRawClass(className) ?: continue
			for (method in clsNode.methods) {
				if (!rule.matchesMethodName(method.methodInfo.name)) {
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
		val group: RuleGroup,
		val classNames: Set<String>,
		val methodNames: Set<String>,
		val methodPrefixes: Set<String> = emptySet(),
		val target: RenameTarget,
		val nameArgIndex: Int,
		val otherArgIndex: Int = -1,
		val minArgs: Int = -1,
	) {
		fun matchesMethodName(name: String): Boolean {
			if (methodNames.contains(name)) {
				return true
			}
			return methodPrefixes.any { prefix -> name.startsWith(prefix) }
		}
	}

	private enum class RuleGroup {
		NULL_CHECK,
		JSON,
		LOG,
	}

	data class RuleOptions(
		val nullCheckRules: Boolean = true,
		val jsonRules: Boolean = true,
		val logRules: Boolean = false,
		val obfuscatedNullCheckRules: Boolean = true,
	)

	companion object {
		private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

		private val RULES = listOf(
			Rule(
				id = "generic.checkNotNullParameter",
				group = RuleGroup.NULL_CHECK,
				classNames = emptySet(),
				methodNames = setOf("checkNotNullParameter"),
				target = RenameTarget.ARGUMENT,
				nameArgIndex = 1,
				otherArgIndex = 0,
				minArgs = 2,
			),
			Rule(
				id = "generic.notNull",
				group = RuleGroup.NULL_CHECK,
				classNames = emptySet(),
				methodNames = setOf("notNull"),
				target = RenameTarget.ARGUMENT,
				nameArgIndex = 1,
				otherArgIndex = 0,
				minArgs = 2,
			),
			Rule(
				id = "preconditions.checkNotNull",
				group = RuleGroup.NULL_CHECK,
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
				id = "json.get",
				group = RuleGroup.JSON,
				classNames = setOf("org.json.JSONObject"),
				methodNames = emptySet(),
				methodPrefixes = setOf("get", "opt"),
				target = RenameTarget.ASSIGNEE,
				nameArgIndex = 0,
				minArgs = 1,
			),
			Rule(
				id = "log.tag",
				group = RuleGroup.LOG,
				classNames = setOf("android.util.Log"),
				methodNames = setOf("d", "i", "w", "e", "v", "wtf", "println"),
				target = RenameTarget.CLASS,
				nameArgIndex = 0,
				minArgs = 1,
			),
		)
	}
}


