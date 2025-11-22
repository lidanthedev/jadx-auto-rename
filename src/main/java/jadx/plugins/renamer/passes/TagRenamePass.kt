package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.FieldInitInsnAttr
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.EncodedValueUtils
import jadx.plugins.renamer.AutoRenameOptions
import java.util.logging.Logger

class TagRenamePass(
    private val options: AutoRenameOptions
) : JadxPreparePass {
    private val logger = Logger.getLogger("TagRenamePass")

    override fun getInfo(): JadxPassInfo {
        return OrderedJadxPassInfo(
            "TagRename",
            "Rename private/final String fields with constant values starting with uppercase to TAG"
        ).before("RenameVisitor")
    }

    override fun init(root: RootNode) {
        for (cls in root.classes) {
            processClass(cls)
        }
    }

    private fun processClass(cls: ClassNode) {
        if (cls.contains(AFlag.DONT_RENAME)) {
            return
        }
        for (fld in cls.fields) {
            try {
                processField(cls, fld)
            } catch (e: Exception) {
                logger.severe("Error processing field ${fld.getName()} in class ${cls}: $e")
            }
        }
    }

    private fun processField(cls: ClassNode, fld: FieldNode) {
		if (cls.name.endsWith("MainActivity")){
			logger.info("Processing MainActivity class")
		}
        if (fld.contains(AFlag.DONT_RENAME)) {
            return
        }
        val acc = fld.accessFlags
        if (!acc.isPrivate || !acc.isFinal) {
            return
        }
        if (fld.type != ArgType.STRING) {
            return
        }
        // try to get constant value attribute (works for static final fields and other encoded constants)
		val constVal = fld.get(JadxAttrType.CONSTANT_VALUE) ?: return
		val value = EncodedValueUtils.convertToConstValue(constVal)
        if (value !is String) {
            return
        }
        if (value.isEmpty()) {
            return
        }
        val first = value[0]
        if (!first.isUpperCase()) {
            return
        }
        // avoid name conflict
        if (cls.searchFieldByName("TAG") != null) {
            return
        }
        // perform rename
        logger.info("Renaming field '${fld.name}' in class $cls to TAG (value='$value')")
        fld.rename("TAG")
        RenameReasonAttr.forNode(fld).append("from TagRenamePass: value='$value'")
    }
}
