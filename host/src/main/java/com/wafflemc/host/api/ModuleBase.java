package com.wafflemc.host.api;

import com.wafflemc.host.Module;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Base class for WaffleCore modules. It owns everything a module registers (commands, listeners,
 * tasks, permissions, data files) and tears it all down on {@link #stop()}, so a hot reload can
 * never leak a task or a listener.
 */
public abstract class ModuleBase implements Module {
    protected JavaPlugin host;
    protected Logger log;
    protected File dataFolder;
    private FileConfiguration config;
    private String moduleName = "module";

    private final List<Listener> listeners = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<String> permissions = new ArrayList<>();
    private final List<AtomicYaml> files = new ArrayList<>();

    /** Called once after the config is loaded. Register commands, listeners and tasks here. */
    protected abstract void enable() throws Exception;

    /** Optional hook before everything is torn down. */
    protected void disable() {
    }

    @Override
    public final void start(JavaPlugin plugin) throws Exception {
        this.host = plugin;
        this.log = plugin.getLogger();
        this.moduleName = readName();
        this.dataFolder = new File(new File(plugin.getDataFolder(), "modules"), moduleName);
        this.dataFolder.mkdirs();
        loadConfig();
        try {
            enable();
        } catch (Throwable t) {
            stop();
            throw t;
        }
    }

    @Override
    public final void stop() {
        try {
            disable();
        } catch (Throwable t) {
            if (log != null) log.warning("[" + moduleName + "] disable failed: " + t);
        }
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        CommandMap map = Bukkit.getCommandMap();
        for (Command c : commands) {
            map.getKnownCommands().values().removeIf(x -> x == c);
            c.unregister(map);
        }
        boolean hadCommands = !commands.isEmpty();
        commands.clear();
        permissions.forEach(p -> Bukkit.getPluginManager().removePermission(p));
        permissions.clear();
        files.forEach(AtomicYaml::flush);
        files.clear();
        if (hadCommands) syncCommands();
    }

    // ---- helpers for subclasses -------------------------------------------------------------

    protected final String name() {
        return moduleName;
    }

    protected final FileConfiguration config() {
        return config;
    }

    protected final void listen(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, host);
        listeners.add(listener);
    }

    /** Registers a command without needing plugin.yml. {@code permission} may be null. */
    protected final void command(String label, String description, String permission, List<String> aliases, TabExecutor handler) {
        Command cmd = new Command(label, description, "/" + label, aliases == null ? List.of() : aliases) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (permission != null && !sender.hasPermission(permission)) {
                    Text.send(sender, "&cYou don't have permission.");
                    return true;
                }
                try {
                    if (!handler.onCommand(sender, this, commandLabel, args)) {
                        Text.send(sender, "&cUsage: &e" + getUsage());
                    }
                } catch (Throwable t) {
                    Text.send(sender, "&cSomething went wrong running that command.");
                    log.severe("[" + moduleName + "] /" + label + " failed: " + t);
                    t.printStackTrace();
                }
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (permission != null && !sender.hasPermission(permission)) return List.of();
                List<String> out = handler.onTabComplete(sender, this, alias, args);
                return out == null ? List.of() : out;
            }
        };
        if (permission != null) cmd.setPermission(permission);
        Bukkit.getCommandMap().register("wafflecore", cmd);
        commands.add(cmd);
    }

    protected final void permission(String node, PermissionDefault def, String description) {
        if (Bukkit.getPluginManager().getPermission(node) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(node, description, def));
            permissions.add(node);
        }
    }

    protected final BukkitTask timer(Runnable run, long delayTicks, long periodTicks) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(host, run, delayTicks, periodTicks);
        tasks.add(t);
        return t;
    }

    protected final BukkitTask asyncTimer(Runnable run, long delayTicks, long periodTicks) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(host, run, delayTicks, periodTicks);
        tasks.add(t);
        return t;
    }

    protected final BukkitTask later(Runnable run, long delayTicks) {
        BukkitTask t = Bukkit.getScheduler().runTaskLater(host, run, delayTicks);
        tasks.add(t);
        return t;
    }

    /** Runs on the main thread; safe to call from async code. */
    protected final void sync(Runnable run) {
        if (Bukkit.isPrimaryThread()) run.run();
        else Bukkit.getScheduler().runTask(host, run);
    }

    /** A data file saved off-thread; flushed automatically when the module stops. */
    protected final AtomicYaml data(String fileName) {
        AtomicYaml y = new AtomicYaml(new File(dataFolder, fileName), log);
        files.add(y);
        return y;
    }

    /** Saves every tracked data file that has pending changes. */
    protected final void saveAll() {
        files.forEach(AtomicYaml::saveIfDirty);
    }

    protected final void autosave(long periodTicks) {
        timer(this::saveAll, periodTicks, periodTicks);
    }

    protected final void msg(CommandSender to, String text) {
        Text.send(to, text);
    }

    // ---- internals --------------------------------------------------------------------------

    private String readName() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("module.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("name", getClass().getSimpleName().toLowerCase());
            }
        } catch (IOException ignored) {
        }
        return getClass().getSimpleName().toLowerCase();
    }

    private void loadConfig() {
        File file = new File(dataFolder, "config.yml");
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("module-config.yml")) {
                if (in != null) Files.copy(in, file.toPath());
            } catch (IOException e) {
                log.warning("[" + moduleName + "] could not write default config: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("module-config.yml")) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
        }
    }

    private static void syncCommands() {
        try {
            Object server = Bukkit.getServer();
            server.getClass().getMethod("syncCommands").invoke(server);
        } catch (Throwable ignored) {
        }
        for (var p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Called by the host after registering commands so clients get fresh tab completion. */
    public static void refreshCommands() {
        syncCommands();
    }

    protected static Map<String, Command> knownCommands() {
        return Bukkit.getCommandMap().getKnownCommands();
    }
}
