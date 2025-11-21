package jadx.plugins.renamer.passes;

import jadx.api.data.CommentStyle;
import jadx.api.plugins.input.data.attributes.JadxAttrType;
import jadx.api.plugins.input.data.attributes.types.SourceFileAttr;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxDecompilePass;
import jadx.api.plugins.pass.types.JadxPreparePass;
import jadx.core.Jadx;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;

public class SourceFileRenamePass implements JadxPreparePass {

	private String comment;

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

		SourceFileAttr sourceFile = cls.get(JadxAttrType.SOURCE_FILE);
		if (sourceFile == null){
			return;
		}
		String fileName = sourceFile.getFileName();
		if (fileName.startsWith("SourceFile")) {
			return;
		}
		if (fileName.startsWith("r8") || fileName.startsWith("R8")) {
			return;
		}
		String origName = fileName.split("\\.")[0]; // remove extension

		cls.rename(origName);
	}
}
