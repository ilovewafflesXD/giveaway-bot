package com.wafflemc.xrayalert;

import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Text;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionDefault;

public final class XrayAlert extends ModuleBase implements Listener, TabExecutor {
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final class Track {
        final Map<String, Deque<Long>> hidden = new HashMap<>();
        final Deque<Long> blocks = new ArrayDeque<>();
        long lastAlert;
    }

    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Set<UUID> muted = new HashSet<>();
    private final Map<String, Integer> thresholds = new HashMap<>();

    @Override
    protected void enable() {
        permission("wafflecore.xray", PermissionDefault.OP, "Use /xray");
        permission("wafflecore.xray.alerts", PermissionDefault.OP, "See xray alerts");
        var section = config().getConfigurationSection("thresholds");
        if (section != null) {
            for (String key : section.getKeys(false)) thresholds.put(key.toUpperCase(Locale.ROOT), section.getInt(key));
        }
        listen(this);
        command("xray", "Inspect hidden-ore mining or toggle xray alerts.", "wafflecore.xray", List.of(), this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (config().getBoolean("ignore-creative", true) && p.getGameMode() != GameMode.SURVIVAL) return;
        long now = System.currentTimeMillis();
        long window = config().getLong("window-minutes", 10) * 60_000L;
        Track t = tracks.computeIfAbsent(p.getUniqueId(), k -> new Track());
        t.blocks.addLast(now);
        trim(t.blocks, now, window);

        String ore = oreKind(e.getBlock().getType());
        if (ore == null || !isHidden(e.getBlock())) return;

        Deque<Long> times = t.hidden.computeIfAbsent(ore, k -> new ArrayDeque<>());
        times.addLast(now);
        trim(times, now, window);

        int limit = thresholds.get(ore);
        long cooldown = config().getLong("alert-cooldown-minutes", 5) * 60_000L;
        if (times.size() >= limit && now - t.lastAlert >= cooldown) {
            t.lastAlert = now;
            alert(p, ore, times.size(), t.blocks.size());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        tracks.remove(e.getPlayer().getUniqueId());
        muted.remove(e.getPlayer().getUniqueId());
    }

    private String oreKind(Material m) {
        String n = m.name();
        if (!n.endsWith("_ORE") && !n.equals("ANCIENT_DEBRIS")) return null;
        for (String key : thresholds.keySet()) {
            if (n.contains(key)) return key;
        }
        return null;
    }

    /** An ore is hidden when no face touches air, water, lava or another open space. */
    private static boolean isHidden(Block b) {
        for (BlockFace f : FACES) {
            Block n = b.getRelative(f);
            if (n.isPassable() || n.isLiquid()) return false;
        }
        return true;
    }

    private void alert(Player p, String ore, int count, int blocks) {
        long minutes = config().getLong("window-minutes", 10);
        String text = Text.fill(config().getString("messages.alert"), "player", p.getName(), "count", count,
                "ore", ore.toLowerCase(Locale.ROOT).replace('_', ' '), "minutes", minutes, "blocks", blocks);
        Component line = Text.color(text)
                .clickEvent(ClickEvent.runCommand("/tp " + p.getName()))
                .hoverEvent(HoverEvent.showText(Text.color("&7" + Text.fill(config().getString("messages.hover"), "player", p.getName()))));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("wafflecore.xray.alerts") && !muted.contains(staff.getUniqueId())) staff.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
        for (String cmd : config().getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
    }

    private static void trim(Deque<Long> q, long now, long window) {
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("alerts") && sender instanceof Player p) {
            boolean off = muted.add(p.getUniqueId());
            if (!off) muted.remove(p.getUniqueId());
            Text.send(sender, "Xray alerts " + (off ? "&cmuted" : "&aenabled") + "&7.");
            return true;
        }
        if (args.length != 1) {
            Text.send(sender, "&cUsage: /xray <player> | /xray alerts");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(sender, "&cThat player isn't online.");
            return true;
        }
        Track t = tracks.get(target.getUniqueId());
        long now = System.currentTimeMillis();
        long window = config().getLong("window-minutes", 10) * 60_000L;
        Text.send(sender, "&6Xray check: &e" + target.getName() + " &8(last " + (window / 60_000) + " minutes)");
        if (t == null) {
            Text.raw(sender, "&7No tracked mining yet.");
            return true;
        }
        trim(t.blocks, now, window);
        Text.raw(sender, "&7Blocks broken: &f" + t.blocks.size());
        if (t.hidden.isEmpty()) Text.raw(sender, "&7Hidden ores: &anone");
        t.hidden.forEach((ore, q) -> {
            trim(q, now, window);
            if (!q.isEmpty()) Text.raw(sender, "&7Hidden " + ore.toLowerCase(Locale.ROOT).replace('_', ' ') + ": &c" + q.size() + " &8/ " + thresholds.get(ore));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String t = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new java.util.ArrayList<>();
        if ("alerts".startsWith(t)) out.add("alerts");
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(t)) out.add(p.getName());
        });
        return out;
    }
}
