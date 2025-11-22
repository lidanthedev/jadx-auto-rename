package jadx.plugins.renamer;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AutoRenameOptions extends BasePluginOptionsBuilder {

	private boolean sourceFileRename;
	private boolean toStringRename;
	private boolean tagRename;
	private boolean logRename;

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
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".tag_rename.enable")
				.description("Enable Auto Rename by TAG field")
				.defaultValue(true)
				.setter(v -> tagRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".log_rename.enable")
				.description("Enable Auto Rename by Log TAGs")
				.defaultValue(true)
				.setter(v -> logRename = v);
	}

	public boolean isSourceFileRename() {
		return sourceFileRename;
	}

	public boolean isToStringRename() {
		return toStringRename;
	}

	public boolean isTagRename() {
		return tagRename;
	}

	public boolean isLogRename() {
		return logRename;
	}
}
