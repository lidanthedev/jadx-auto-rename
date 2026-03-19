package jadx.plugins.renamer;

import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class AutoRenameOptions extends BasePluginOptionsBuilder {

	private boolean sourceFileRename;
	private boolean toStringRename;
	private boolean tagRename;
	private boolean logRename;
	private boolean intrinsicsRename;
	private boolean intrinsicsClassRename;
	private boolean kotlinMetadataComment;
	private boolean constArgRename;
	private boolean getterSetterRename;
	private boolean getterSetterMethodRename;
	private boolean constArgNullCheckRules;
	private boolean constArgJsonRules;
	private boolean constArgLogRules;
	private boolean constArgObfuscatedNullCheck;
	private boolean constArgKotlinIntrinsicsClassRename;

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
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".intrinsics_rename.enable")
				.description("Enable Kotlin Intrinsics detection and rename pass")
				.defaultValue(true)
				.setter(v -> intrinsicsRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".intrinsics_rename.class_rename.enable")
				.description("Enable class rename to Intrinsics when marker strings are detected")
				.defaultValue(true)
				.setter(v -> intrinsicsClassRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".kotlin_metadata_comment.enable")
				.description("Add Kotlin @Metadata summary as class comments")
				.defaultValue(false)
				.setter(v -> kotlinMetadataComment = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.enable")
				.description("Enable Auto Rename by constant invoke arguments")
				.defaultValue(true)
				.setter(v -> constArgRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".getter_setter_rename.enable")
				.description("Enable Auto Rename by getX()/setY() method patterns")
				.defaultValue(true)
				.setter(v -> getterSetterRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".getter_setter_method_rename.enable")
				.description("Enable Auto Rename of getter/setter methods based on renamed fields")
				.defaultValue(true)
				.setter(v -> getterSetterMethodRename = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.null_check.enable")
				.description("Enable const-arg rules for null-check methods")
				.defaultValue(true)
				.setter(v -> constArgNullCheckRules = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.json.enable")
				.description("Enable const-arg rules for JSONObject get*/opt* methods")
				.defaultValue(true)
				.setter(v -> constArgJsonRules = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.log.enable")
				.description("Enable const-arg rules for Log tag calls")
				.defaultValue(false)
				.setter(v -> constArgLogRules = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.obfuscated_null_check.enable")
				.description("Enable heuristic const-arg renaming for obfuscated null-check wrappers")
				.defaultValue(true)
				.setter(v -> constArgObfuscatedNullCheck = v);
		boolOption(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.kotlin_intrinsics_class_rename.enable")
				.description("Enable legacy Kotlin Intrinsics handling inside ConstArgRename pass")
				.defaultValue(false)
				.setter(v -> constArgKotlinIntrinsicsClassRename = v);
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

	public boolean isIntrinsicsRename() {
		return intrinsicsRename;
	}

	public boolean isIntrinsicsClassRename() {
		return intrinsicsClassRename;
	}

	public boolean isKotlinMetadataComment() {
		return kotlinMetadataComment;
	}

	public boolean isConstArgRename() {
		return constArgRename;
	}

	public boolean isGetterSetterRename() {
		return getterSetterRename;
	}

	public boolean isGetterSetterMethodRename() {
		return getterSetterMethodRename;
	}

	public boolean isConstArgNullCheckRules() {
		return constArgNullCheckRules;
	}

	public boolean isConstArgJsonRules() {
		return constArgJsonRules;
	}

	public boolean isConstArgLogRules() {
		return constArgLogRules;
	}

	public boolean isConstArgObfuscatedNullCheck() {
		return constArgObfuscatedNullCheck;
	}

	public boolean isConstArgKotlinIntrinsicsClassRename() {
		return constArgKotlinIntrinsicsClassRename;
	}
}
