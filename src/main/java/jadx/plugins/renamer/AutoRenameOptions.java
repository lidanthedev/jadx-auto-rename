package jadx.plugins.renamer;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AutoRenameOptions extends BasePluginOptionsBuilder {

	private boolean enable;

	@Override
	public void registerOptions() {
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".enable")
				.description("enable comment")
				.defaultValue(true)
				.setter(v -> enable = v);
	}

	public boolean isEnable() {
		return enable;
	}
}
