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

	private JadxDecompiler createAndInitDecompiler(String sampleFileName) throws Exception {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(getSampleFile(sampleFileName));
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
