package com.wafflemc.host;

import com.wafflemc.host.api.EconomyBridge;
import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Services;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WaffleHost 2.1: loads the legacy core.jar plus any number of module jars from
 * plugins/WaffleCore/modules/, each in its own class loader, and can reload them one by one.
 */
public final class WaffleHost extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private File runDir;
    private YamlConfiguration settings = new YamlConfiguration();
    private File modulesDir;
    private LoadedModule core;
    private final Map<String, LoadedModule> modules = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        loadSettings();
        runDir = new File(getDataFolder(), ".run");
        runDir.mkdirs();
        File[] stale = runDir.listFiles();
        if (stale != null) Arrays.stream(stale).forEach(File::delete);
        modulesDir = new File(getDataFolder(), "modules");
        modulesDir.mkdirs();

        startAll();
    }

    @Override
    public void onDisable() {
        stopAll();
    }

    // ---- lifecycle --------------------------------------------------------------------------

    /**
     * host.yml holds host-only settings. The chat prefix is read from the core's own config.yml so
     * there is a single place to change it.
     */
    private void loadSettings() {
        File file = new File(getDataFolder(), "host.yml");
        if (!file.exists()) saveResource("host.yml", false);
        settings = YamlConfiguration.loadConfiguration(file);
        String prefix = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")).getString("prefix");
        Services.prefix(prefix != null ? prefix : settings.getString("prefix"));
    }

    private void startAll() {
        core = LoadedModule.core(new File(getDataFolder(), "core.jar"), runDir);
        if (core.source.isFile()) {
            String err = core.start(this);
            if (err != null) getLogger().severe("core.jar failed to load: " + err);
            else if (Services.economy().isPresent()) getLogger().info("Core registered its own economy API.");
            else linkEconomy();
        } else {
            getLogger().info("No core.jar found; starting without the legacy core.");
        }
        discover();
        Set<String> disabled = settings.getStringList("disabled-modules").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int ok = 0;
        for (LoadedModule m : ordered()) {
            if (disabled.contains(m.name)) {
                getLogger().info("Module '" + m.name + "' is disabled in config.yml.");
                continue;
            }
            if (!dependenciesReady(m)) continue;
            if (m.start(this) == null) ok++;
        }
        getLogger().info("WaffleCore enabled: core " + (core.running() ? "running" : "off") + ", " + ok + "/" + modules.size() + " modules running.");
        ModuleBase.refreshCommands();
    }

    private void stopAll() {
        List<LoadedModule> reverse = new ArrayList<>(ordered());
        java.util.Collections.reverse(reverse);
        reverse.forEach(m -> m.stop(this));
        if (core != null) core.stop(this);
        Services.economy(null);
    }

    /** core.jar cancels every task of this plugin when it stops, so a core reload restarts everything. */
    private String reloadAll() {
        loadSettings();
        stopAll();
        modules.clear();
        startAll();
        return core.running() || !core.source.isFile() ? null : "core failed to start";
    }

    private void discover() {
        modules.clear();
        File[] jars = modulesDir.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null) return;
        Arrays.sort(jars, Comparator.comparing(File::getName));
        for (File jar : jars) {
            try {
                LoadedModule m = LoadedModule.fromJar(jar, runDir);
                if (modules.putIfAbsent(m.name, m) != null) {
                    getLogger().warning("Duplicate module name '" + m.name + "' in " + jar.getName() + "; skipped.");
                }
            } catch (IOException e) {
                getLogger().warning("Skipping " + jar.getName() + ": " + e.getMessage());
            }
        }
    }

    /** Topological order by depends=, falling back to name order on cycles. */
    private List<LoadedModule> ordered() {
        List<LoadedModule> out = new ArrayList<>();
        Set<String> placed = new java.util.HashSet<>();
        List<LoadedModule> left = new ArrayList<>(modules.values());
        boolean progress = true;
        while (!left.isEmpty() && progress) {
            progress = false;
            for (var it = left.iterator(); it.hasNext(); ) {
                LoadedModule m = it.next();
                if (placed.containsAll(m.depends) || m.depends.stream().noneMatch(modules::containsKey)) {
                    out.add(m);
                    placed.add(m.name);
                    it.remove();
                    progress = true;
                }
            }
        }
        out.addAll(left);
        return out;
    }

    private boolean dependenciesReady(LoadedModule m) {
        for (String d : m.depends) {
            LoadedModule dep = modules.get(d);
            if (dep == null || !dep.running()) {
                getLogger().warning("Module '" + m.name + "' needs '" + d + "', which is not running; skipped.");
                return false;
            }
        }
        return true;
    }

    private void linkEconomy() {
        try {
            EconomyBridge.install(core.instance());
            getLogger().info("Linked the core economy for modules.");
        } catch (Throwable t) {
            Services.economy(null);
            getLogger().warning("Could not link the core economy; modules that pay money will run without it: " + t);
        }
    }

    // ---- /wafflecore ------------------------------------------------------------------------

    private void say(CommandSender to, String text) {
        to.sendMessage(LEGACY.deserialize(Services.prefix() + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("wafflecore.admin")) {
            say(sender, "&cYou don't have permission.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> reloadCommand(sender, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all");
            case "status", "modules", "list" -> status(sender);
            default -> say(sender, "&cUsage: /wafflecore reload [all|core|<module>] | status");
        }
        return true;
    }

    private void reloadCommand(CommandSender sender, String target) {
        long start = System.currentTimeMillis();
        String error;
        if (target.equals("all") || target.equals("core")) {
            error = reloadAll();
        } else {
            LoadedModule m = modules.get(target);
            if (m == null) {
                say(sender, "&cNo module named &e" + target + "&c. Use /wafflecore status.");
                return;
            }
            error = m.reload(this);
            ModuleBase.refreshCommands();
        }
        if (error == null) {
            say(sender, "Reloaded &e" + target + "&7 in &e" + (System.currentTimeMillis() - start) + "ms&7.");
        } else {
            say(sender, "&cReload failed: " + error + ". Check the console.");
        }
    }

    private void status(CommandSender sender) {
        say(sender, "&6WaffleHost &e" + getPluginMeta().getVersion());
        say(sender, "core: " + (core != null && core.running() ? "&arunning &8(" + core.runningFile() + ")" : "&cnot running"));
        if (modules.isEmpty()) {
            say(sender, "&7No extra modules installed in plugins/WaffleCore/modules/.");
        }
        for (LoadedModule m : modules.values()) {
            say(sender, m.name + " &8v" + m.version + ": " + (m.running() ? "&arunning" : "&cstopped"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("wafflecore.admin")) return List.of();
        if (args.length == 1) return filter(List.of("reload", "status"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            List<String> names = new ArrayList<>(List.of("all", "core"));
            names.addAll(modules.keySet());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(t)).toList();
    }

}
