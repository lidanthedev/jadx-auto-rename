package jadx.plugins.renamer;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AutoRenameOptions extends BasePluginOptionsBuilder {

	private boolean sourceFileRename;

	@Override
	public void registerOptions() {
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".source_rename.enable")
				.description("Enable Auto Rename by SourceFile (.source)")
				.defaultValue(true)
				.setter(v -> sourceFileRename = v);
	}

	public boolean isSourceFileRename() {
		return sourceFileRename;
	}
}
