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
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Level
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
			"ToStringRename",
			"Rename classes and fields according to toString() output"
		)
			.after("SimplifyVisitor")
	}

	override fun visit(mth: MethodNode) {
		// bind parent class once to avoid repeated dereferences and NPEs
		val parentCls = mth.parentClass ?: return
		if (parentCls.contains(AFlag.DONT_RENAME)) {
			return
		}
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
						logger.info("Renaming using 'toString' in class: $parentCls")
						processArgs(parentCls, wrapInsn)
					}
				}
			}
		}
	}

	val clsSepRgx = Regex("[ ({:]")

	private fun processArgs(parentCls: ClassNode, wrapInsn: InsnNode): Boolean {
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
								val info = parentCls.getClassInfo()
								if (info != null && info.hasAlias() && RenameUtils.isClassUserRenamed(parentCls)) {
									// don't override
								} else {
									logger.info("rename class '${parentCls.name}' to '$clsName'")
									parentCls.rename(clsName)
									RenameReasonAttr.forNode(parentCls).append("from toString()")
								}
							} catch (e: Exception) {
								// Log exception and continue (do not rethrow) so errors are not silently suppressed
								logger.log(
									Level.SEVERE,
									"Error during class rename check in ToStringRenamePass for class ${parentCls.name}",
									e
								)
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
					val fldInfo = iget.index as? FieldInfo ?: return false
					val fld = parentCls.searchField(fldInfo)
					if (fld != null && fldName != null && NameMapper.isValidIdentifier(fldName)) {
						// skip if field already manually renamed
						try {
							val finfo = fld.getFieldInfo()
							if (finfo != null && finfo.hasAlias() && RenameUtils.isFieldUserRenamed(fld)) {
								// don't override
							} else {
								logger.info("rename field '${fld.name}' to '$fldName'")
								fld.rename(fldName)
								RenameReasonAttr.forNode(fld).append("from toString()")
							}
						} catch (e: Exception) {
							// Log exception and continue (do not rethrow) so errors are not silently suppressed
							logger.log(
								Level.SEVERE,
								"Error during field rename in ToStringRenamePass for field ${fld.name} in class ${parentCls.name}",
								e
							)
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
