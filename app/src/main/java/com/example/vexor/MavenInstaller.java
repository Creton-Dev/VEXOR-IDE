package com.example.vexor;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class MavenInstaller {

    public interface OutputCallback {
        void onOutput(String text);
    }

    public static File getJavaLibsDir(Context context) {
        File dir = new File(context.getFilesDir(), "java_libs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<File> getInstalledJars(Context context) {
        List<File> jarList = new ArrayList<>();
        File dir = getJavaLibsDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".jar") && isValidJar(f)) {
                    jarList.add(f);
                }
            }
        }
        return jarList;
    }

    public static boolean isValidJar(File file) {
        if (file == null || !file.exists() || file.length() < 100) {
            return false;
        }
        try (ZipFile zip = new ZipFile(file)) {
            return zip.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean installDependency(Context context, String dependency, OutputCallback callback) {
        Set<String> visited = new HashSet<>();
        return installDependencyInternal(context, dependency, callback, visited);
    }

    private static boolean installDependencyInternal(Context context, String dependency, OutputCallback callback, Set<String> visited) {
        String cleanDep = dependency.trim();
        if (cleanDep.startsWith("!") || cleanDep.startsWith("//") || cleanDep.startsWith("%")) {
            cleanDep = cleanDep.substring(1).trim();
        }
        if (cleanDep.toLowerCase().startsWith("maven:")) {
            cleanDep = cleanDep.substring(6).trim();
        } else if (cleanDep.toLowerCase().startsWith("maven")) {
            cleanDep = cleanDep.substring(5).trim();
        }

        String[] parts = cleanDep.split(":");
        if (parts.length < 3) {
            callback.onOutput("Invalid Maven coordinate format: '" + dependency + "'. Expected groupId:artifactId:version (e.g. com.google.code.gson:gson:2.13.2)\n");
            return false;
        }

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();

        String canonicalCoord = groupId + ":" + artifactId + ":" + version;
        if (visited.contains(canonicalCoord)) {
            return true;
        }
        visited.add(canonicalCoord);

        String groupPath = groupId.replace('.', '/');
        String jarName = artifactId + "-" + version + ".jar";
        File targetFile = new File(getJavaLibsDir(context), jarName);

        if (!targetFile.exists() || !isValidJar(targetFile)) {
            if (targetFile.exists()) {
                targetFile.delete(); // Delete corrupt cached file
            }

            String urlStr = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version + "/" + jarName;
            callback.onOutput("Downloading " + canonicalCoord + " (" + jarName + ")...\n");

            boolean success = downloadFileWithRedirects(urlStr, targetFile, callback);
            if (!success || !isValidJar(targetFile)) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                callback.onOutput("Error: Failed to download valid JAR for " + canonicalCoord + "\n");
                return false;
            }

            callback.onOutput("Successfully downloaded " + jarName + "\n");
        } else {
            callback.onOutput("Requirement satisfied: " + jarName + "\n");
        }

        // Fetch POM and resolve transitive dependencies
        fetchAndInstallTransitiveDependencies(context, groupId, artifactId, version, callback, visited);

        return true;
    }

    private static boolean downloadFileWithRedirects(String urlStr, File targetFile, OutputCallback callback) {
        int maxRedirects = 5;
        String currentUrl = urlStr;

        for (int i = 0; i < maxRedirects; i++) {
            try {
                URL url = new URL(currentUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Vexor-Android-IDE/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        currentUrl = loc;
                        continue;
                    }
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    callback.onOutput("HTTP " + code + " error downloading " + currentUrl + "\n");
                    return false;
                }

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                return true;
            } catch (Exception e) {
                callback.onOutput("Network error downloading " + currentUrl + ": " + e.getMessage() + "\n");
                return false;
            }
        }
        return false;
    }

    private static void fetchAndInstallTransitiveDependencies(Context context, String groupId, String artifactId, String version, OutputCallback callback, Set<String> visited) {
        try {
            String groupPath = groupId.replace('.', '/');
            String pomUrlStr = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";

            URL url = new URL(pomUrlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Vexor-Android-IDE/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                ByteArrayOutputStream pomBytes = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        pomBytes.write(buffer, 0, len);
                    }
                }
                String pomXml = pomBytes.toString("UTF-8");

                Map<String, String> properties = extractPomProperties(pomXml);
                properties.put("project.version", version);
                properties.put("pom.version", version);

                Matcher matcher = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL).matcher(pomXml);
                while (matcher.find()) {
                    String depBlock = matcher.group(1);
                    if (depBlock == null || depBlock.contains("<scope>test</scope>") ||
                        depBlock.contains("<scope>provided</scope>") || depBlock.contains("<optional>true</optional>")) {
                        continue;
                    }

                    String dGroup = extractXmlTag(depBlock, "groupId");
                    String dArtifact = extractXmlTag(depBlock, "artifactId");
                    String dVersion = extractXmlTag(depBlock, "version");

                    if (dVersion != null && dVersion.startsWith("${")) {
                        String propKey = dVersion.substring(2, dVersion.length() - 1);
                        if (properties.containsKey(propKey)) {
                            dVersion = properties.get(propKey);
                        }
                    }

                    if (dGroup != null && dArtifact != null && dVersion != null && !dVersion.contains("${")) {
                        installDependencyInternal(context, dGroup + ":" + dArtifact + ":" + dVersion, callback, visited);
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort POM parsing
        }
    }

    private static Map<String, String> extractPomProperties(String pomXml) {
        Map<String, String> props = new HashMap<>();
        Matcher propsBlockMatcher = Pattern.compile("<properties>(.*?)</properties>", Pattern.DOTALL).matcher(pomXml);
        if (propsBlockMatcher.find()) {
            String propsContent = propsBlockMatcher.group(1);
            if (propsContent != null) {
                Matcher propMatcher = Pattern.compile("<([a-zA-Z0-9._-]+)>(.*?)</\\1>").matcher(propsContent);
                while (propMatcher.find()) {
                    props.put(propMatcher.group(1), propMatcher.group(2).trim());
                }
            }
        }
        return props;
    }

    private static String extractXmlTag(String xml, String tag) {
        if (xml == null || tag == null) return null;
        Pattern p = Pattern.compile("<" + Pattern.quote(tag) + ">(.*?)</" + Pattern.quote(tag) + ">");
        Matcher m = p.matcher(xml);
        return m.find() && m.group(1) != null ? m.group(1).trim() : null;
    }
}
