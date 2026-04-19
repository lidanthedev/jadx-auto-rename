package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Logger

/**
 * Reverse accessor rename:
 * when a field already has a meaningful alias, try to rename obfuscated getter/setter methods.
 */
class GetterSetterMethodRenamePass : JadxDecompilePass {
	private val logger = Logger.getLogger("GetterSetterMethodRenamePass")

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"GetterSetterMethodRename",
			"Rename get/set methods based on renamed fields"
		)
			// Keep this pass late so all field renames have already been applied.
			.after("ProcessVariables")
	}

	override fun init(root: RootNode) {
		// no-op
	}

	override fun visit(cls: ClassNode?): Boolean {
		if (cls == null || cls.contains(AFlag.DONT_RENAME)) {
			return true
		}
		for (field in cls.fields) {
			if (!isRenamedField(field.fieldInfo)) {
				continue
			}
			val fieldName = field.alias
			if (!NameMapper.isValidIdentifier(fieldName)) {
				continue
			}
			val getterName = "get" + fieldName.replaceFirstChar { it.uppercaseChar() }
			val setterName = "set" + fieldName.replaceFirstChar { it.uppercaseChar() }
			for (mth in cls.methods) {
				if (!canRenameMethod(mth)) {
					continue
				}
				if (isGetterForField(mth, field.fieldInfo)) {
					renameMethod(cls, mth, getterName, "getter")
				} else if (isSetterForField(mth, field.fieldInfo)) {
					renameMethod(cls, mth, setterName, "setter")
				}
			}
		}
		return true
	}

	override fun visit(mth: MethodNode) {
		// no-op
	}

	private fun isRenamedField(fieldInfo: FieldInfo): Boolean {
		return fieldInfo.hasAlias()
	}

	private fun canRenameMethod(mth: MethodNode): Boolean {
		if (mth.contains(AFlag.DONT_RENAME) || mth.isNoCode) {
			return false
		}
		if (mth.methodInfo.isConstructor || mth.methodInfo.isClassInit) {
			return false
		}
		// Avoid overriding already meaningful accessor names.
		if (mth.name.startsWith("get") || mth.name.startsWith("set") || mth.name.startsWith("is")) {
			return false
		}
		try {
			if (mth.methodInfo.hasAlias() && RenameUtils.isMethodUserRenamed(mth)) {
				return false
			}
		} catch (_: Exception) {
			// ignore
		}
		return true
	}

	private fun isGetterForField(mth: MethodNode, fieldInfo: FieldInfo): Boolean {
		if (mth.methodInfo.argsCount != 0 || mth.methodInfo.returnType == ArgType.VOID) {
			return false
		}
		val insns = collectInsns(mth)
		var hasReturn = false
		var hasWrite = false
		var hasTargetRead = false
		for (insn in insns) {
			if (insn.type == InsnType.RETURN) {
				hasReturn = true
			}
			if (containsWriteAccess(insn)) {
				hasWrite = true
			}
			if (containsFieldRead(insn, fieldInfo)) {
				hasTargetRead = true
			}
		}
		return hasReturn && hasTargetRead && !hasWrite
	}

	private fun isSetterForField(mth: MethodNode, fieldInfo: FieldInfo): Boolean {
		if (mth.methodInfo.argsCount != 1 || mth.methodInfo.returnType != ArgType.VOID) {
			return false
		}
		val insns = collectInsns(mth)
		val argRegs = runCatching { mth.argRegs }.getOrNull() ?: return false
		// MethodNode.getArgRegs() contains only method parameters (no `this`).
		val valueReg = argRegs.getOrNull(0)?.regNum ?: return false

		var hasWriteToTarget = false
		for (insn in insns) {
			if (insn.type != InsnType.IPUT && insn.type != InsnType.SPUT) {
				continue
			}
			val putInsn = insn as? IndexInsnNode ?: continue
			val putField = putInsn.index as? FieldInfo ?: continue
			if (putField != fieldInfo || putInsn.argsCount == 0) {
				continue
			}
			val valueArg = putInsn.getArg(0)
			if (valueArg is RegisterArg && valueArg.regNum == valueReg) {
				hasWriteToTarget = true
			}
		}
		return hasWriteToTarget
	}

	private fun collectInsns(mth: MethodNode): List<InsnNode> {
		mth.instructions?.let { insnArr ->
			return insnArr.filterNotNull()
		}
		if (mth.basicBlocks.isNotEmpty()) {
			return mth.basicBlocks.flatMap { it.instructions }
		}
		runCatching { mth.load() }.onFailure { return emptyList() }
		mth.instructions?.let { insnArr ->
			return insnArr.filterNotNull()
		}
		if (mth.basicBlocks.isNotEmpty()) {
			return mth.basicBlocks.flatMap { it.instructions }
		}
		return emptyList()
	}

	private fun containsFieldRead(insn: InsnNode, fieldInfo: FieldInfo): Boolean {
		if (isFieldReadInsn(insn, fieldInfo)) {
			return true
		}
		for (arg in insn.argList) {
			val wrapped = arg.unwrap() ?: continue
			if (containsFieldRead(wrapped, fieldInfo)) {
				return true
			}
		}
		return false
	}

	private fun containsWriteAccess(insn: InsnNode): Boolean {
		if (insn.type == InsnType.IPUT || insn.type == InsnType.SPUT) {
			return true
		}
		for (arg in insn.argList) {
			val wrapped = arg.unwrap() ?: continue
			if (containsWriteAccess(wrapped)) {
				return true
			}
		}
		return false
	}

	private fun isFieldReadInsn(insn: InsnNode, fieldInfo: FieldInfo): Boolean {
		if (insn.type != InsnType.IGET && insn.type != InsnType.SGET) {
			return false
		}
		val readField = (insn as? IndexInsnNode)?.index as? FieldInfo ?: return false
		return readField == fieldInfo
	}

	private fun renameMethod(cls: ClassNode, mth: MethodNode, newName: String, kind: String) {
		if (!NameMapper.isValidIdentifier(newName) || mth.name == newName) {
			return
		}
		if (hasNameCollision(cls, mth, newName)) {
			return
		}
		logger.info("Rename method '$mth' to '$newName' as $kind for renamed field")
		mth.rename(newName)
		RenameReasonAttr.forNode(mth).append("from GetterSetterMethodRenamePass: $kind")
	}

	private fun hasNameCollision(cls: ClassNode, target: MethodNode, newName: String): Boolean {
		return cls.methods.any { mth ->
			mth != target
				&& mth.name == newName
				&& mth.methodInfo.argsCount == target.methodInfo.argsCount
				&& mth.methodInfo.returnType == target.methodInfo.returnType
		}
	}
}

