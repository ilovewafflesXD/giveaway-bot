package com.wafflemc.host.api;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A YAML data file that is serialised on the calling thread but written to disk on a background
 * thread, using a temp file and an atomic rename so a crash can never leave a half-written file.
 *
 * <p>Read and modify {@link #cfg()} from the main thread only.
 */
public final class AtomicYaml {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WaffleCore-IO");
        t.setDaemon(true);
        return t;
    });

    private final File file;
    private final Logger log;
    private YamlConfiguration cfg;
    private boolean dirty;

    public AtomicYaml(File file, Logger log) {
        this.file = file;
        this.log = log;
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration cfg() {
        return cfg;
    }

    public void mark() {
        dirty = true;
    }

    /** Queues a background write if anything changed since the last save. */
    public void saveIfDirty() {
        if (dirty) {
            dirty = false;
            String data = cfg.saveToString();
            IO.execute(() -> write(data));
        }
    }

    /** Writes synchronously; use on shutdown so nothing is lost. */
    public void flush() {
        // Wait for queued async writes first so ordering is preserved.
        try {
            IO.submit(() -> { }).get();
        } catch (Exception ignored) {
            Thread.currentThread().interrupt();
        }
        if (dirty) {
            dirty = false;
            write(cfg.saveToString());
        }
    }

    private void write(String data) {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            parent.mkdirs();
            Path tmp = new File(parent, file.getName() + ".tmp").toPath();
            Files.writeString(tmp, data, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
