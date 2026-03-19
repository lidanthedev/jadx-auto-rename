package jadx.plugins.renamer;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JadxAutoRenamePluginTest {
	private JadxDecompiler jadx;

	@BeforeAll
	public void setUp() throws Exception {
		jadx = createAndInitDecompiler("hello.smali");
	}

	@AfterAll
	public void tearDown() {
		if (jadx != null) {
			jadx.close();
		}
	}

	@Test
	public void integrationTest() {
		JavaClass cls = jadx.getClasses().get(0);
		String clsCode = cls.getCode();
		System.out.println(clsCode);
		assertThat(clsCode).contains("class HelloWorld");
		assertThat(clsCode).contains("System.out.println(\"Hello, World\")");
	}

	@Test
	public void constArgRenameEnabledTest() throws Exception {
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali")) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).contains("demo(Object innerPadding, Object activity)");
			assertThat(code).contains("checkNotNullParameter(name, \"name\")");
		}
	}

	@Test
	public void constArgRenameDisabledTest() throws Exception {
		Map<String, String> pluginOptions = new HashMap<>();
		pluginOptions.put(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.enable", "false");
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali", pluginOptions)) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).doesNotContain("demo(Object innerPadding, Object activity)");
			assertThat(code).doesNotContain("demoObf(Object name)");
		}
	}

	@Test
	public void constArgNullCheckRuleDisabledTest() throws Exception {
		Map<String, String> pluginOptions = new HashMap<>();
		pluginOptions.put(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.null_check.enable", "false");
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali", pluginOptions)) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).doesNotContain("demo(Object innerPadding, Object activity)");
		}
	}

	@Test
	public void constArgIntrinsicsClassDiscoveryTest() throws Exception {
		Map<String, String> pluginOptions = new HashMap<>();
		pluginOptions.put(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.obfuscated_null_check.enable", "false");
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali", pluginOptions)) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).contains("checkNotNullParameter(name, \"name\")");
		}
	}

	@Test
	public void constArgLogMethodNameRuleEnabledTest() throws Exception {
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali")) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).contains("void refreshData()")
					.contains("void syncState()");
		}
	}

	@Test
	public void constArgLogMethodNameRuleDisabledTest() throws Exception {
		Map<String, String> pluginOptions = new HashMap<>();
		pluginOptions.put(JadxAutoRenamePlugin.PLUGIN_ID + ".const_arg_rename.rule.log_method_name.enable", "false");
		try (JadxDecompiler decompiler = createAndInitDecompiler("const_args.smali", pluginOptions)) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("ConstArgsSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).doesNotContain("void refreshData()")
					.doesNotContain("void syncState()");
		}
	}

	@Test
	public void getterSetterRenameEnabledTest() throws Exception {
		try (JadxDecompiler decompiler = createAndInitDecompiler("getter_setter.smali")) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("GetterSetterSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).contains("useSet(Object title)");
			assertThat(code).contains("useGetStatic(Object name)");
			assertThat(code).contains("useSetStatic(Object obj, Object name)");
		}
	}

	@Test
	public void getterSetterRenameDisabledTest() throws Exception {
		Map<String, String> pluginOptions = new HashMap<>();
		pluginOptions.put(JadxAutoRenamePlugin.PLUGIN_ID + ".getter_setter_rename.enable", "false");
		try (JadxDecompiler decompiler = createAndInitDecompiler("getter_setter.smali", pluginOptions)) {
			JavaClass cls = decompiler.searchJavaClassByOrigFullName("GetterSetterSample");
			assertThat(cls).isNotNull();
			String code = cls.getCode();
			assertThat(code).doesNotContain("useSet(Object title)");
			assertThat(code).doesNotContain("useGetStatic(Object name)");
			assertThat(code).doesNotContain("useSetStatic(Object obj, Object name)");
		}
	}

	private JadxDecompiler createAndInitDecompiler(String sampleFileName) throws Exception {
		return createAndInitDecompiler(sampleFileName, Collections.emptyMap());
	}

	private JadxDecompiler createAndInitDecompiler(String sampleFileName, Map<String, String> pluginOptions) throws Exception {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(getSampleFile(sampleFileName));
		args.setPluginOptions(pluginOptions);
		JadxDecompiler jadx = new JadxDecompiler(args);
		jadx.registerPlugin(new JadxAutoRenamePlugin());
		jadx.load();
		return jadx;
	}

	private File getSampleFile(String fileName) throws URISyntaxException {
		URL file = getClass().getClassLoader().getResource("samples/" + fileName);
		assertThat(file).isNotNull();
		return new File(file.toURI());
	}
}
