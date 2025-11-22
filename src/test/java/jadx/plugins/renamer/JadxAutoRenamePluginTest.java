package jadx.plugins.renamer;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class JadxAutoRenamePluginTest {

	private File getSampleFile(String fileName) throws URISyntaxException {
		URL file = getClass().getClassLoader().getResource("samples/" + fileName);
		assertThat(file).isNotNull();
		return new File(file.toURI());
	}
}
