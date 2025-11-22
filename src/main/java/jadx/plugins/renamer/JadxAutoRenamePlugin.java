package jadx.plugins.renamer;

import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.plugins.renamer.passes.LogRenamePass;
import jadx.plugins.renamer.passes.SourceFileRenamePass;
import jadx.plugins.renamer.passes.TagRenamePass;
import jadx.plugins.renamer.passes.ToStringRenamePass;

public class JadxAutoRenamePlugin implements JadxPlugin {
	public static final String PLUGIN_ID = "jadx-auto-rename";

	private final AutoRenameOptions options = new AutoRenameOptions();

	@Override
	public JadxPluginInfo getPluginInfo() {
		return JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
				.name("Auto Rename Plugin")
				.description("Rename classes automatically and make reversing easier")
				.homepage("https://github.com/lidanthedev/jadx-auto-rename")
				.requiredJadxVersion("1.5.1, r2333")
				.build();
	}

	@Override
	public void init(JadxPluginContext context) {
		context.registerOptions(options);
		if (options.isSourceFileRename()) {
			context.addPass(new SourceFileRenamePass());
		}
		if (options.isToStringRename()){
			context.addPass(new ToStringRenamePass());
		}
		if (options.isTagRename()){
			context.addPass(new TagRenamePass());
		}
		if (options.isLogRename()) {
			context.addPass(new LogRenamePass());
		}
	}
}
