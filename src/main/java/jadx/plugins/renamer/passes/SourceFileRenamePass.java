package jadx.plugins.renamer.passes;

import jadx.api.plugins.input.data.attributes.JadxAttrType;
import jadx.api.plugins.input.data.attributes.types.SourceFileAttr;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxPreparePass;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.nodes.RenameReasonAttr;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.RootNode;
import jadx.plugins.renamer.util.RenameUtils;
import kotlin.text.StringsKt;

public class SourceFileRenamePass implements JadxPreparePass {

	@Override
	public JadxPassInfo getInfo() {
		return new OrderedJadxPassInfo(
					"SourceFileRename",
					"Rename files according to SourceFile attribute")
				.before("RenameVisitor");
	}

	@Override
	public void init(RootNode root) {
		for (ClassNode cls: root.getClasses()) {
			renameBySourceMetadata(cls);
		}
	}

	private void renameBySourceMetadata(ClassNode cls) {
		if (cls.contains(AFlag.DONT_RENAME)) {
			return;
		}

		// skip if class already manually renamed by user
		try {
			if (cls.getClassInfo() != null && cls.getClassInfo().hasAlias()) {
				if (RenameUtils.isClassUserRenamed(cls)) return;
			}
		} catch (Exception ignored) {
			// ignore
		}

		SourceFileAttr sourceFile = cls.get(JadxAttrType.SOURCE_FILE);
		if (sourceFile == null){
			return;
		}
		String fileName = sourceFile.getFileName();
		if (fileName.isEmpty() || fileName.startsWith("SourceFile") || fileName.startsWith("r8") || fileName.startsWith("R8")) {
			return;
		}
		String[] split = fileName.split("\\.");
		if (split.length > 2) {
			return; // unexpected format
		}
		String origName = split[0]; // remove extension

		cls.rename(origName);
		RenameReasonAttr.forNode(cls).append("from SourceFile attribute");
	}
}
