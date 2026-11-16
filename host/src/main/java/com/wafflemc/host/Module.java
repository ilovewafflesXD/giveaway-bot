package com.wafflemc.host;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Contract between WaffleHost and a hot-reloadable module.
 *
 * <p>This interface is deliberately identical to the one shipped in WaffleHost 2.0.0 so the existing
 * {@code core.jar} keeps loading unchanged. New modules should extend
 * {@link com.wafflemc.host.api.ModuleBase} instead of implementing it directly.
 */
public interface Module {
    void start(JavaPlugin host) throws Exception;

    void stop();
}
