# JADX Auto Rename Plugin

![JADX Plugin](https://img.shields.io/badge/JADX-Plugin-blue) ![License](https://img.shields.io/badge/License-Apache%202.0-green)

**JADX Auto Rename** is a plugin for the [JADX Decompiler](https://github.com/skylot/jadx) that automatically improves the readability of obfuscated Android code.

It intelligently renames classes and fields by analyzing common Android conventions and metadata left behind in the bytecode, effectively reversing some effects of obfuscation (ProGuard/R8).

## 🚀 Features

This plugin employs three primary strategies to recover meaningful names for obfuscated classes:

### 1. SourceFile Attribute Analysis
Many obfuscators remove the class name but optionally leave the `.source` attribute (the original filename) in the bytecode for stack trace debugging.
* **Action:** The plugin reads the `SourceFile` attribute.
* **Result:** If a class is named `a.b.c.a` but the source attribute says `MainActivity.java`, the plugin renames the class to `MainActivity`.

### 2. `toString()` Implementation Analysis
Developers often implement `toString()` methods that return the class name or a descriptive string for debugging purposes.
* **Action:** The plugin parses the implementation of `toString()`.
* **Result:** If `toString()` returns a string literal like `"UserSession{id=..."`, the plugin infers the class name is likely `UserSession`.

### 3. Android `Log` Call Analysis
Classes often define a `TAG` constant for Android logging or pass a string literal to `Log.d()`, `Log.e()`, etc.
* **Action:** The plugin scans for calls to `android.util.Log`.
* **Result:** If a class frequently logs using the tag `"NetworkManager"`, the plugin uses this hint to rename the class to `NetworkManager`.

---

## 📦 Installation

You can install this plugin directly into JADX-GUI or use it with the CLI version.

### Method 1: JADX-GUI (Recommended)
1.  Launch **JADX-GUI**.
2.  Navigate to the menu bar: **Plugins** -> **Install plugin**.
3.  Select the `jadx-auto-rename.jar` file (downloaded from Releases or built locally).
4.  Restart JADX or reload the current APK to apply the changes.

### Method 2: Command Line Interface (CLI)
Use the `jadx` command to install the plugin jar:

    jadx plugins --install-jar path/to/jadx-auto-rename.jar

Once installed, the plugin will automatically run during the decompilation pass.

---

## 🛠️ Building from Source

To build the plugin locally, you will need JDK 11 or higher.

1.  Clone the repository:

        git clone https://github.com/lidanthedev/jadx-auto-rename.git

2.  Build the plugin using Gradle:

        ./gradlew build

3.  The output JAR will be located in:

        build/libs/jadx-auto-rename-dev.jar

---

## 📝 Usage

Once installed, no manual configuration is required.
1.  Open an APK, DEX, or JAR file in JADX.
2.  The plugin runs automatically during the **Decompilation** stage.
3.  on classes that were renamed. You will see entries such as:
    > `Renamed class 'a.b.c' reason: from SourceFile`

---

## 🤝 Contributing

Contributions are welcome! Please submit a Pull Request if you have ideas for new renaming heuristics (e.g., parsing Gson annotations or Dagger components).

1.  Fork the project.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

---

**License**
Distributed under the MIT License. See `LICENSE` for more information.
