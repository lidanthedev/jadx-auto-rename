package jadx.plugins.renamer.util

import jadx.api.data.ICodeData
import jadx.api.data.IJavaNodeRef
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode

class RenameUtils {
	companion object {
		@JvmStatic
		fun isFieldUserRenamed(fld: FieldNode): Boolean {
            try {
                val root = fld.root()
                val codeData: ICodeData = root.args.codeData ?: return false
                val clsRaw = fld.parentClass.classInfo.rawName
                val shortId = fld.fieldInfo.shortId
                return codeData.renames.any { r ->
                    val nodeRef = r.nodeRef
                    nodeRef.type == IJavaNodeRef.RefType.FIELD &&
                            nodeRef.declaringClass == clsRaw &&
                            nodeRef.shortId == shortId
                }
            } catch (_: Exception) {
                return false
            }
        }

		// Check project code data renames to see if this class was renamed by user (persisted)
		@JvmStatic
		fun isClassUserRenamed(cls: ClassNode): Boolean {
			try {
				val root = cls.root()
				val codeData: ICodeData = root.args.codeData ?: return false
				val clsRaw = cls.classInfo.rawName
				return codeData.renames.any { r ->
					val nodeRef = r.nodeRef
					nodeRef.type == IJavaNodeRef.RefType.CLASS &&
						nodeRef.declaringClass == clsRaw
				}
			} catch (_: Exception) {
				return false
			}
		}
    }
}
