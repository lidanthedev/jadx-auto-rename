package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.plugins.renamer.AutoRenameOptions
import java.util.logging.Logger

class ToStringRenamePass() : JadxDecompilePass {
	val logger = Logger.getLogger("ToStringRename")

	override fun init(root: RootNode) {
	}

	override fun visit(cls: ClassNode?): Boolean {
		return true
	}

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"SourceFileRename",
			"Rename files according to SourceFile attribute"
		)
			.after("SimplifyVisitor")
	}

	override fun visit(mth: MethodNode) {
		if (mth.contains(AFlag.DONT_RENAME)) {
			return
		}
		if (mth.methodInfo.shortId == "toString()Ljava/lang/String;") {
			val returnBlock = mth.exitBlock.predecessors.firstOrNull { it.contains(AFlag.RETURN) }
			val lastInsn = returnBlock?.instructions?.lastOrNull()
			if (lastInsn != null && lastInsn.type == InsnType.RETURN) {
				val arg = lastInsn.getArg(0)
				if (arg.isInsnWrap) {
					val wrapInsn = (arg as InsnWrapArg).wrapInsn
					if (wrapInsn.type == InsnType.STR_CONCAT) {
						logger.info("Renaming using 'toString' in class: ${mth.parentClass}")
						processArgs(mth, wrapInsn)
					}
				}
			}
		}
	}

	val clsSepRgx = Regex("[ ({:]")

	private fun processArgs(mth: MethodNode, wrapInsn: InsnNode): Boolean {
		try {
			var fldName: String? = null
			for ((i, arg) in wrapInsn.arguments.withIndex()) {
				val insn = arg.unwrap() ?: return false
				if (i % 2 == 0) {
					if (insn !is ConstStringNode) {
						return false
					}
					var str = insn.string
					if (i == 0) {
						// class and first field name
						val parts = str.split(clsSepRgx)
						if (parts.size < 2) {
							return false
						}
						val clsName = parts[0]
						if (NameMapper.isValidIdentifier(clsName)) {
							// skip if class already manually renamed
							try {
								val info = mth.parentClass.getClassInfo()
								if (info != null && info.hasAlias()) {
									// don't override user alias
								} else {
									logger.info("rename class '${mth.parentClass.name}' to '$clsName'")
									mth.parentClass.rename(clsName)
									RenameReasonAttr.forNode(mth.parentClass).append("from toString()")
								}
							} catch (e: Exception) {
								// ignore
							}
						}
						str = parts[1]
					}
					fldName = str.trim('\'', '=', ',', ' ', ':')
				} else {
					if (insn.type != InsnType.IGET) {
						return false
					}
					if (insn !is IndexInsnNode) {
						return false
					}
					val iget = insn
					val fldInfo = iget.index as FieldInfo
					val fld = mth.parentClass.searchField(fldInfo)
					if (fld != null && NameMapper.isValidIdentifier(fldName)) {
						// skip if field already manually renamed
						try {
							val finfo = fld.getFieldInfo()
							if (finfo != null && finfo.hasAlias()) {
								// don't override
							} else {
								logger.info("rename field '${fld.name}' to '$fldName'")
								fld.rename(fldName)
								RenameReasonAttr.forNode(fld).append("from toString()")
							}
						} catch (_: Exception) {
							// ignore
						}
					}
				}
			}
			return true
		} catch (e: Exception) {
			logger.severe("$e: Args process failed")
			return false
		}
	}
}
