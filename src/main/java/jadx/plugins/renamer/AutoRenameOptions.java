package jadx.plugins.renamer;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AutoRenameOptions extends BasePluginOptionsBuilder {

	private boolean sourceFileRename;
	private boolean toStringRename;

	@Override
	public void registerOptions() {
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".source_rename.enable")
				.description("Enable Auto Rename by SourceFile (.source)")
				.defaultValue(true)
				.setter(v -> sourceFileRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".to_string_rename.enable")
				.description("Enable Auto Rename by toString() method")
				.defaultValue(true)
				.setter(v -> toStringRename = v);
	}

	public boolean isSourceFileRename() {
		return sourceFileRename;
	}

	public boolean isToStringRename() {
		return toStringRename;
	}
}
