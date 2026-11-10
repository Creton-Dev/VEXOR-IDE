package com.example.vexor;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;

public class JavaRunner {

    public interface OutputCallback {
        void onOutput(String text);
    }

    public interface InputCallback {
        String readInput();
    }

    private final Context context;

    public JavaRunner(Context context) {
        this.context = context;
    }

    public void runInteractive(String code, InputCallback inputCallback, OutputCallback outputCallback) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        File workDir = new File(context.getCacheDir(), "java_exec_" + System.currentTimeMillis());
        File srcDir = new File(workDir, "src");
        File binDir = new File(workDir, "bin");
        File dexDir = new File(workDir, "dex");
        File optDir = new File(workDir, "opt");

        srcDir.mkdirs();
        binDir.mkdirs();
        dexDir.mkdirs();
        optDir.mkdirs();

        try {
            // 1. Preprocess inline Maven dependencies (e.g. // maven: com.google.code.gson:gson:2.13.2)
            StringBuilder cleanCodeBuilder = new StringBuilder();
            String[] lines = code.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("// maven:") || trimmed.startsWith("!maven") ||
                    trimmed.startsWith("%maven") || trimmed.startsWith("//maven:")) {

                    String dep = trimmed.replace("// maven:", "")
                            .replace("//maven:", "")
                            .replace("!maven", "")
                            .replace("%maven", "").trim();

                    if (!dep.isEmpty()) {
                        MavenInstaller.installDependency(context, dep, outputCallback::onOutput);
                    }
                    cleanCodeBuilder.append("// ").append(line).append("\n");
                } else {
                    cleanCodeBuilder.append(line).append("\n");
                }
            }
            String javaCode = cleanCodeBuilder.toString();

            // Auto-inject common imports if not present
            StringBuilder autoImports = new StringBuilder();
            if (!javaCode.contains("import java.util.")) {
                autoImports.append("import java.util.*;\n");
            }
            if (!javaCode.contains("import java.io.")) {
                autoImports.append("import java.io.*;\n");
            }
            if (!javaCode.contains("import java.math.")) {
                autoImports.append("import java.math.*;\n");
            }
            if (!javaCode.contains("import java.net.")) {
                autoImports.append("import java.net.*;\n");
            }

            // Check if user provided class Main or wrap raw statements in Main class
            String className = extractClassName(javaCode);
            if (className == null) {
                className = "Main";
                javaCode = autoImports + "public class Main {\n" +
                           "    public static void main(String[] args) {\n" +
                           javaCode + "\n" +
                           "    }\n" +
                           "}\n";
            } else {
                javaCode = autoImports.toString() + javaCode;
            }

            File javaSourceFile = new File(srcDir, className + ".java");
            try (FileOutputStream fos = new FileOutputStream(javaSourceFile)) {
                fos.write(javaCode.getBytes());
            }

            List<File> mavenJars = MavenInstaller.getInstalledJars(context);
            if (mavenJars.isEmpty()) {
                outputCallback.onOutput(">>> Compiling Java source code (Janino Compiler)...\n");
            } else {
                List<String> jarNames = new ArrayList<>();
                for (File f : mavenJars) {
                    jarNames.add(f.getName());
                }
                outputCallback.onOutput(">>> Compiling Java source code with " + mavenJars.size() + " installed JAR(s): " + jarNames + "...\n");
            }

            // 2. Configure Janino Compiler with URLClassLoader pointing to all installed Maven JARs
            try {
                URL[] jarUrls = new URL[mavenJars.size()];
                for (int i = 0; i < mavenJars.size(); i++) {
                    jarUrls[i] = mavenJars.get(i).toURI().toURL();
                }

                ClassLoader jarClassLoader = new URLClassLoader(jarUrls, context.getClassLoader());
                org.codehaus.janino.IClassLoader iClassLoader = new org.codehaus.janino.ClassLoaderIClassLoader(jarClassLoader);

                org.codehaus.janino.Compiler compiler = new org.codehaus.janino.Compiler();
                compiler.setDestinationDirectory(binDir, false);
                compiler.setIClassLoader(iClassLoader);

                compiler.compile(new File[]{javaSourceFile});
            } catch (org.codehaus.commons.compiler.CompileException e) {
                String msg = e.getMessage();
                outputCallback.onOutput("\n[Compilation Error]\n" + msg + "\n");
                if (msg != null && msg.contains("Assignment conversion not possible from type \"java.lang.Object\"")) {
                    outputCallback.onOutput("\n💡 Tip: Generic methods (like gson.fromJson) require an explicit cast in Janino:\n");
                    outputCallback.onOutput("   String text = (String) gson.fromJson(json, String.class);\n");
                }
                return;
            } catch (Exception e) {
                outputCallback.onOutput("\n[Compiler Error]: " + e.getMessage() + "\n");
                return;
            }

            outputCallback.onOutput(">>> Converting bytecode to Android DEX (D8 Compiler)...\n");

            // 3. Convert .class files and installed Maven JARs to .dex using D8
            List<Path> classFiles = new ArrayList<>();
            findClassFiles(binDir, classFiles);

            if (classFiles.isEmpty()) {
                outputCallback.onOutput("Error: No compiled class files generated.\n");
                return;
            }

            com.android.tools.r8.D8Command.Builder d8Builder = com.android.tools.r8.D8Command.builder();
            for (Path p : classFiles) {
                d8Builder.addProgramFiles(p);
            }
            for (File jar : mavenJars) {
                d8Builder.addProgramFiles(jar.toPath());
            }

            d8Builder.setOutput(dexDir.toPath(), com.android.tools.r8.OutputMode.DexIndexed);
            d8Builder.setMinApiLevel(26);
            com.android.tools.r8.D8.run(d8Builder.build());

            File dexJar = new File(dexDir, "output.jar");
            createZipWithAllDexFiles(dexDir, dexJar);

            dexJar.setReadOnly();
            dexJar.setWritable(false, false);

            outputCallback.onOutput(">>> Executing Java binary on Android ART...\n\n");

            // 4. Load DEX into ART and invoke main method
            DexClassLoader classLoader = new DexClassLoader(
                    dexJar.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    null,
                    context.getClassLoader()
            );

            OutputStreamCallback streamCallback = new OutputStreamCallback(outputCallback);
            PrintStream customPrintStream = new PrintStream(streamCallback, true);
            System.setOut(customPrintStream);
            System.setErr(customPrintStream);

            Class<?> mainClass = classLoader.loadClass(className);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) new String[0]);

            outputCallback.onOutput("\n[Process completed]\n");

        } catch (Exception e) {
            outputCallback.onOutput("\nExecution Error:\n" + e.getMessage() + "\n");
            if (e.getCause() != null) {
                outputCallback.onOutput("Cause: " + e.getCause().getMessage() + "\n");
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            deleteRecursive(workDir);
        }
    }

    private String extractClassName(String code) {
        Matcher matcher = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)").matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("class\\s+([A-Za-z0-9_]+)").matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void findClassFiles(File dir, List<Path> list) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    findClassFiles(f, list);
                } else if (f.getName().endsWith(".class")) {
                    list.add(f.toPath());
                }
            }
        }
    }

    private void createZipWithAllDexFiles(File dexDir, File targetJar) throws Exception {
        File[] dexFiles = dexDir.listFiles((dir, name) -> name.endsWith(".dex"));
        if (dexFiles == null || dexFiles.length == 0) {
            throw new RuntimeException("No .dex files generated in " + dexDir.getAbsolutePath());
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetJar))) {
            byte[] buf = new byte[8192];
            for (File dexFile : dexFiles) {
                ZipEntry ze = new ZipEntry(dexFile.getName());
                zos.putNextEntry(ze);
                try (FileInputStream fis = new FileInputStream(dexFile)) {
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDir.delete();
    }

    private static class OutputStreamCallback extends OutputStream {
        private final OutputCallback callback;

        public OutputStreamCallback(OutputCallback callback) {
            this.callback = callback;
        }

        @Override
        public void write(int b) {
            callback.onOutput(String.valueOf((char) b));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            callback.onOutput(new String(b, off, len));
        }
    }
}
