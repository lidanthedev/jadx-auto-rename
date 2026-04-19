package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import java.util.Locale
import java.util.logging.Logger

class GetterSetterRenamePass : JadxDecompilePass {
	private val logger = Logger.getLogger("GetterSetterRenamePass")
	private lateinit var root: RootNode

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"GetterSetterRename",
			"Rename variables from getX/setY method patterns"
		).after("SimplifyVisitor")
	}

	override fun init(root: RootNode) {
		this.root = root
	}

	override fun visit(cls: ClassNode?): Boolean {
		return true
	}

	override fun visit(mth: MethodNode) {
		if (mth.contains(AFlag.DONT_RENAME) || mth.isNoCode) {
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
				applyRenameByPattern(insn)
			}
		}
	}

	private fun applyRenameByPattern(invoke: InvokeNode) {
		val methodName = invoke.callMth.name
		if (methodName.length < 4) {
			return
		}
		val inferredName = when {
			methodName.startsWith("get") -> extractFieldName(methodName)
			methodName.startsWith("set") -> extractFieldName(methodName)
			else -> null
		} ?: return

		val target = determineRenameTarget(invoke, methodName) ?: return
		when (target) {
			is RenameTarget.Assignee -> renameAssignee(invoke, inferredName)
			is RenameTarget.Argument -> renameArgument(invoke, target.argIndex, inferredName)
		}
	}

	private fun determineRenameTarget(invoke: InvokeNode, methodName: String): RenameTarget? {
		val paramCount = invoke.callMth.argsCount
		val isStatic = resolveIsStatic(invoke)
		return if (methodName.startsWith("get")) {
			when {
				paramCount == 0 -> RenameTarget.Assignee
				isStatic && paramCount == 1 -> RenameTarget.Argument(0)
				else -> null
			}
		} else {
			when {
				paramCount == 1 -> RenameTarget.Argument(if (isStatic) 0 else 1)
				isStatic && paramCount == 2 -> RenameTarget.Argument(1)
				else -> null
			}
		}
	}

	private fun resolveIsStatic(invoke: InvokeNode): Boolean {
		val resolvedMth = root.resolveMethod(invoke.callMth)
		return resolvedMth?.accessFlags?.isStatic ?: invoke.isStaticCall
	}

	private fun renameAssignee(invoke: InvokeNode, name: String) {
		val result = invoke.result ?: return
		renameRegister(result, name)
	}

	private fun renameArgument(invoke: InvokeNode, argIndex: Int, name: String) {
		if (argIndex < 0 || argIndex >= invoke.argsCount) {
			return
		}
		val arg = invoke.getArg(argIndex)
		if (arg is RegisterArg) {
			renameRegister(arg, name)
		}
	}

	private fun renameRegister(arg: RegisterArg, name: String) {
		val sVar = arg.sVar ?: return
		val codeVar = sVar.codeVar
		if (codeVar.isThis || codeVar.name == name) {
			return
		}
		codeVar.name = name
		logger.fine("Rename var to '$name' from get/set pattern")
	}

	private fun extractFieldName(methodName: String): String? {
		if (methodName.length <= 3) {
			return null
		}
		val first = methodName[3]
		if (!first.isLetter()) {
			return null
		}
		val name = buildString {
			append(first.lowercaseChar())
			append(methodName.substring(4))
		}
		if (!NameMapper.isValidAndPrintable(name)) {
			return null
		}
		return name.replaceFirstChar { ch -> ch.lowercase(Locale.ROOT) }
	}

	private sealed interface RenameTarget {
		data object Assignee : RenameTarget
		data class Argument(val argIndex: Int) : RenameTarget
	}
}


