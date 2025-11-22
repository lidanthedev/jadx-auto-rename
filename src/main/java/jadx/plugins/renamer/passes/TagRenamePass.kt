package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.FieldInitInsnAttr
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.EncodedValueUtils
import jadx.core.utils.InsnUtils
import java.util.logging.Logger
import jadx.plugins.renamer.util.RenameUtils.Companion.isClassUserRenamed
import jadx.plugins.renamer.util.RenameUtils.Companion.isFieldUserRenamed

class TagRenamePass : JadxDecompilePass {
    private val logger = Logger.getLogger("TagRenamePass")

    override fun getInfo(): JadxPassInfo {
        return OrderedJadxPassInfo(
            "TagRename",
            "Rename private/final String fields with constant values starting with uppercase to TAG"
        ).after("ExtractFieldInit")
    }

    override fun init(root: RootNode) {
        // no-op; processing will happen in visit(cls)
    }

    override fun visit(cls: ClassNode?): Boolean {
        if (cls == null) return true
        processClass(cls)
        return true
    }

    // required by interface, no-op
    override fun visit(mth: MethodNode) {
    }

    private fun processClass(cls: ClassNode) {
        if (cls.contains(AFlag.DONT_RENAME)) {
            return
        }
        // skip classes already manually renamed by user
        try {
            val classInfo = cls.getClassInfo()
            if (classInfo != null && classInfo.hasAlias()) {
                // only skip if alias was set by user (persisted rename), not by other passes
                if (isClassUserRenamed(cls)) {
                    return
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        for (fld in cls.fields) {
            try {
                processField(cls, fld)
            } catch (_: Exception) {
                logger.severe("Error processing field ${fld.getName()} in class ${cls}")
            }
        }
    }

    private fun processField(cls: ClassNode, fld: FieldNode) {
        if (fld.contains(AFlag.DONT_RENAME)) {
            return
        }
        // skip fields already manually renamed by user
        try {
            val fInfo = fld.getFieldInfo()
            if (fInfo != null && fInfo.hasAlias()) {
                // only skip if alias was set by user (persisted rename), not by other passes
                if (isFieldUserRenamed(fld)) {
                    return
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        val acc = fld.accessFlags
        if (!acc.isPrivate || !acc.isFinal) {
            return
        }
        if (fld.type != ArgType.STRING) {
            return
        }

        // 1) try constant value attribute (usually for static final fields)
        var strValue: String? = null
        val constVal = fld.get(JadxAttrType.CONSTANT_VALUE)
        if (constVal != null) {
            val v = EncodedValueUtils.convertToConstValue(constVal)
            if (v is String) {
                strValue = v
            }
        }

        // 2) if not found, try field init instruction attribute (moved from <init> or <clinit>)
        if (strValue == null) {
            val initAttr = fld.get(AType.FIELD_INIT_INSN)
            if (initAttr is FieldInitInsnAttr) {
                val insn = initAttr.getInsn()
                val v = InsnUtils.getConstValueByInsn(cls.root(), insn)
                if (v is String) {
                    strValue = v
                }
            }
        }

        if (strValue == null) {
            return
        }
        if (strValue.isEmpty()) {
            return
        }
        val first = strValue[0]
        if (!first.isUpperCase()) {
            return
        }
        // avoid name conflict
        if (cls.searchFieldByName("TAG") != null) {
            return
        }
        // perform rename
        logger.info("Renaming field '${fld.name}' in class $cls to TAG (value='$strValue')")
        fld.rename("TAG")
        RenameReasonAttr.forNode(fld).append("from TagRenamePass")
        if (cls.name.length > 3 && cls.name.endsWith(strValue)) {
            // avoid redundant class rename
            return
        }
        // skip class rename if class already has alias
        try {
            val classInfo = cls.getClassInfo()
            if (classInfo != null && classInfo.hasAlias()) {
                // only skip if class alias was set by user
                if (isClassUserRenamed(cls)) {
                    return
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        cls.rename(strValue)
        RenameReasonAttr.forNode(cls).append("from TagRenamePass")
    }
}
