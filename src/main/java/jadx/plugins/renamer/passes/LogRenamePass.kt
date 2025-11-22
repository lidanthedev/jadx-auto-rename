package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.utils.InsnUtils
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import java.util.logging.Logger

class LogRenamePass : JadxDecompilePass {
    private val logger = Logger.getLogger("LogRenamePass")

    // Android Log methods to check (common)
    private val LOG_METHODS = setOf("d", "i", "w", "e", "v", "wtf", "println")

    override fun getInfo(): JadxPassInfo {
        return OrderedJadxPassInfo(
            "LogRename",
            "Rename classes using TAG string found in android.util.Log calls"
        ).after("SimplifyVisitor")
    }

    override fun init(root: RootNode) {
        // no-op
    }

    override fun visit(cls: ClassNode?): Boolean {
        // class-level visit unused; we process per-method in visit(mth)
        return true
    }


    override fun visit(mth: MethodNode) {
        if (mth.parentClass != null && mth.parentClass.contains(AFlag.DONT_RENAME)) return
        if (mth.contains(AFlag.DONT_RENAME)) return
        if (mth.isNoCode) return

        for (block in mth.basicBlocks) {
            for (insn in block.instructions) {
                // check invoke instructions
                if (insn.type != InsnType.INVOKE) continue
                if (insn !is InvokeNode) continue
                val call = insn.callMth
                val decl = call.declClass
                if (decl != null && decl.type != null) {
                    val clsType = decl.type.getObject()
                    if (clsType == "Landroid/util/Log;" || clsType == "android.util.Log" || clsType.endsWith("android/util/Log")) {
                        val mthName = call.name
                        if (!LOG_METHODS.contains(mthName)) continue
                        // first arg is TAG
                        if (insn.argsCount == 0) continue
                        val tagArg: InsnArg = insn.getArg(0)
                        val const = InsnUtils.getConstValueByArg(mth.root(), tagArg)
                        if (const is String) {
                            val tag = const
                            if (tag.isNotEmpty() && NameMapper.isValidIdentifier(tag)) {
                                val parentCls = mth.parentClass
                                if (parentCls.contains(AFlag.DONT_RENAME)) continue
                                // skip if class already has alias (manually renamed)
                                try {
                                    val clsInfo = parentCls.getClassInfo()
                                    if (clsInfo != null && clsInfo.hasAlias()) continue
                                } catch (_: Exception) {
                                    // ignore
                                }
                                val currentName = parentCls.name
                                // avoid renaming if class already matches or too short
                                if (currentName == tag) continue
                                if (tag.length < 3) continue
                                logger.info("Rename class $parentCls to '$tag' from Log call in $mth")
                                parentCls.rename(tag)
                                RenameReasonAttr.forNode(parentCls).append("from LogRenamePass: $tag")
                            }
                        }
                    }
                }
            }
        }
    }
}
