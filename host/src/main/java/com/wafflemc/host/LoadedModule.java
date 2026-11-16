package com.wafflemc.host;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * One hot-reloadable jar (core.jar or a file in modules/). The jar is copied to a private run
 * directory before loading, so the original can be replaced on disk while the server runs.
 */
final class LoadedModule {
    final String name;
    final File source;
    final String mainClass;
    final List<String> depends;
    final String version;

    private final File runDir;
    private URLClassLoader loader;
    private Module instance;
    private File running;

    private LoadedModule(String name, File source, String mainClass, List<String> depends, String version, File runDir) {
        this.name = name;
        this.source = source;
        this.mainClass = mainClass;
        this.depends = depends;
        this.version = version;
        this.runDir = runDir;
    }

    /** The legacy single-jar core, whose class name is fixed. */
    static LoadedModule core(File source, File runDir) {
        return new LoadedModule("core", source, "com.wafflemc.core.WaffleCore", List.of(), "legacy", runDir);
    }

    /** Reads module.properties from a jar in modules/. */
    static LoadedModule fromJar(File jar, File runDir) throws IOException {
        try (JarFile jf = new JarFile(jar)) {
            var entry = jf.getEntry("module.properties");
            if (entry == null) throw new IOException(jar.getName() + " has no module.properties");
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(entry)) {
                p.load(in);
            }
            String main = p.getProperty("main");
            if (main == null || main.isBlank()) throw new IOException(jar.getName() + " module.properties has no main=");
            String name = p.getProperty("name", jar.getName().replaceFirst("\\.jar$", "")).toLowerCase();
            String deps = p.getProperty("depends", "");
            List<String> list = deps.isBlank()
                    ? List.of()
                    : java.util.Arrays.stream(deps.split(",")).map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).toList();
            return new LoadedModule(name, jar, main.trim(), list, p.getProperty("version", "?"), runDir);
        }
    }

    boolean running() {
        return instance != null;
    }

    Module instance() {
        return instance;
    }

    /** Starts from the current jar on disk. Returns null on success, or an error message. */
    String start(JavaPlugin host) {
        if (!source.isFile()) return "missing " + source.getPath();
        File copy = new File(runDir, name + "-" + System.nanoTime() + ".jar");
        try {
            Files.copy(source.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return "could not copy " + source.getName() + ": " + e;
        }
        return startFrom(host, copy);
    }

    private String startFrom(JavaPlugin host, File jar) {
        URLClassLoader cl = null;
        Module m = null;
        try {
            cl = new URLClassLoader(new URL[] {jar.toURI().toURL()}, host.getClass().getClassLoader());
            Class<?> c = Class.forName(mainClass, true, cl);
            m = (Module) c.getDeclaredConstructor().newInstance();
            m.start(host);
            File old = running;
            loader = cl;
            instance = m;
            running = jar;
            if (old != null) old.delete();
            return null;
        } catch (Throwable t) {
            host.getLogger().severe("Module '" + name + "' failed to start: " + t);
            t.printStackTrace();
            try {
                if (m != null) m.stop();
            } catch (Throwable ignored) {
            }
            try {
                if (cl != null) cl.close();
            } catch (Throwable ignored) {
            }
            jar.delete();
            return String.valueOf(t);
        }
    }

    void stop(JavaPlugin host) {
        if (instance != null) {
            try {
                instance.stop();
            } catch (Throwable t) {
                host.getLogger().warning("Module '" + name + "' failed to stop cleanly: " + t);
            }
            instance = null;
        }
        if (loader != null) {
            try {
                loader.close();
            } catch (Throwable ignored) {
            }
            loader = null;
        }
    }

    /** Stops, restarts from disk, and falls back to the previous copy if the new one fails. */
    String reload(JavaPlugin host) {
        File previous = running;
        stop(host);
        String error = start(host);
        if (error == null) return null;
        if (previous != null && previous.isFile()) {
            running = null;
            if (startFrom(host, previous) == null) return error + " (kept the previous version running)";
        }
        return error + " (module is not running)";
    }

    String runningFile() {
        return running == null ? "?" : running.getName();
    }
}
