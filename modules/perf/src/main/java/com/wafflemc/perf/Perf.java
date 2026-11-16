package com.wafflemc.perf;

import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Text;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;

public final class Perf extends ModuleBase implements TabExecutor {
    private long nextClearAt;
    private long lastTpsAlert;
    private long lastLeft = Long.MAX_VALUE;

    @Override
    protected void enable() {
        permission("wafflecore.perf", PermissionDefault.OP, "Use /perf");
        permission("wafflecore.perf.alerts", PermissionDefault.OP, "See low-TPS alerts");
        command("perf", "Server performance report.", "wafflecore.perf", List.of("health"), this);

        if (config().getBoolean("autoclear.enabled")) {
            long interval = Math.max(1, config().getLong("autoclear.interval-minutes", 10)) * 60_000L;
            nextClearAt = System.currentTimeMillis() + interval;
            timer(this::autoclearTick, 20L, 20L);
        }
        if (config().getBoolean("mob-limit.enabled")) {
            long every = Math.max(5, config().getLong("mob-limit.check-seconds", 30)) * 20L;
            timer(this::enforceMobLimit, every, every);
        }
        if (config().getBoolean("tps-alert.enabled")) {
            timer(this::tpsCheck, 600L, 200L);
        }
    }

    // ---- scheduled work ---------------------------------------------------------------------

    private void autoclearTick() {
        long left = (nextClearAt - System.currentTimeMillis()) / 1000L;
        if (left <= 0) {
            int removed = clearItems(config().getLong("autoclear.min-age-seconds", 20));
            Bukkit.broadcast(Text.color(Text.fill(Text.fillPrefix(config().getString("autoclear.messages.done")), "count", removed)));
            nextClearAt = System.currentTimeMillis() + Math.max(1, config().getLong("autoclear.interval-minutes", 10)) * 60_000L;
            lastLeft = Long.MAX_VALUE;
            return;
        }
        // Announce each warning threshold the moment the countdown crosses it.
        int announce = -1;
        for (int w : config().getIntegerList("autoclear.warnings")) {
            if (lastLeft > w && left <= w && (announce == -1 || w > announce)) announce = w;
        }
        lastLeft = left;
        if (announce > 0) {
            Bukkit.broadcast(Text.color(Text.fill(Text.fillPrefix(config().getString("autoclear.messages.warning")), "time", Text.duration(announce))));
        }
    }

    private int clearItems(long minAgeSeconds) {
        Set<Material> keep = new HashSet<>();
        for (String n : config().getStringList("autoclear.keep-materials")) {
            Material m = Material.matchMaterial(n);
            if (m != null) keep.add(m);
        }
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                if (item.getTicksLived() < minAgeSeconds * 20L) continue;
                if (keep.contains(stack.getType()) || item.customName() != null) continue;
                if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasLore())) continue;
                item.remove();
                removed += 1;
            }
        }
        return removed;
    }

    private void enforceMobLimit() {
        int max = Math.max(5, config().getInt("mob-limit.max-per-chunk", 60));
        Set<EntityType> keepTypes = new HashSet<>();
        for (String n : config().getStringList("mob-limit.keep-types")) {
            try {
                keepTypes.add(EntityType.valueOf(n.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            Map<Long, List<LivingEntity>> byChunk = new HashMap<>();
            for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class)) {
                if (!(le instanceof Mob mob) || keepTypes.contains(le.getType())) continue;
                if (le.customName() != null || mob.isLeashed()) continue;
                if (le instanceof Tameable t && t.isTamed()) continue;
                if (le.getType() == EntityType.ENDER_DRAGON || le.getType() == EntityType.WITHER) continue;
                Location l = le.getLocation();
                byChunk.computeIfAbsent(chunkKey(l.getBlockX() >> 4, l.getBlockZ() >> 4), k -> new ArrayList<>()).add(le);
            }
            for (List<LivingEntity> list : byChunk.values()) {
                if (list.size() <= max) continue;
                list.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
                for (int i = 0; i < list.size() - max; i++) {
                    list.get(i).remove();
                    removed++;
                }
            }
        }
        if (removed > 0) log.info("[perf] removed " + removed + " surplus mobs from crowded chunks.");
    }

    private void tpsCheck() {
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        double threshold = config().getDouble("tps-alert.threshold", 16.0);
        long cooldown = config().getLong("tps-alert.cooldown-minutes", 5) * 60_000L;
        if (tps < threshold && System.currentTimeMillis() - lastTpsAlert >= cooldown) {
            lastTpsAlert = System.currentTimeMillis();
            String msg = Text.fill(config().getString("tps-alert.message"), "tps", String.format("%.1f", tps),
                    "mspt", String.format("%.1f", Bukkit.getAverageTickTime()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("wafflecore.perf.alerts")) Text.send(p, msg);
            }
            log.warning("[perf] low TPS: " + String.format("%.1f", tps));
        }
    }

    // ---- /perf ------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> overview(sender);
            case "chunks" -> chunks(sender);
            case "clear" -> {
                int n = clearItems(0);
                Text.send(sender, "Cleared &6" + n + "&7 dropped items.");
            }
            default -> Text.send(sender, "&cUsage: /perf [chunks|clear]");
        }
        return true;
    }

    private void overview(CommandSender s) {
        double[] tps = Bukkit.getTPS();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long max = rt.maxMemory() >> 20;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        Text.raw(s, "&8&m      &r &6&lWaffle&e&lMC &6Performance &8&m      ");
        Text.raw(s, "&7TPS &8(1m/5m/15m): " + colour(tps[0]) + " &8/ " + colour(tps[1]) + " &8/ " + colour(tps[2]));
        Text.raw(s, "&7Tick time: &f" + String.format("%.1f", Bukkit.getAverageTickTime()) + "ms &8(budget 50ms)");
        Text.raw(s, "&7Memory: &f" + used + " &8/ &f" + max + " MB &8(" + (used * 100 / max) + "%)");
        Text.raw(s, "&7Uptime: &f" + Text.duration(uptime) + " &8| &7Players: &f" + Bukkit.getOnlinePlayers().size() + "&8/&f" + Bukkit.getMaxPlayers());
        for (World w : Bukkit.getWorlds()) {
            int items = w.getEntitiesByClass(Item.class).size();
            Text.raw(s, "&6" + w.getName() + " &8- &7chunks &f" + w.getLoadedChunks().length + " &7entities &f" + w.getEntityCount()
                    + " &7items &f" + items + " &7players &f" + w.getPlayerCount());
        }
        Text.raw(s, "&8Use &e/perf chunks &8for the busiest chunks.");
    }

    private void chunks(CommandSender s) {
        record Hot(World world, int x, int z, int count) { }
        List<Hot> hot = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Map<Long, Integer> counts = new HashMap<>();
            for (Entity e : w.getEntities()) {
                Location l = e.getLocation();
                counts.merge(chunkKey(l.getBlockX() >> 4, l.getBlockZ() >> 4), 1, Integer::sum);
            }
            counts.forEach((k, v) -> hot.add(new Hot(w, (int) (k >> 32), (int) k.longValue(), v)));
        }
        hot.sort(Comparator.comparingInt(Hot::count).reversed());
        Text.send(s, "&6Busiest chunks by entity count");
        for (Hot h : hot.stream().limit(8).toList()) {
            int bx = h.x * 16 + 8;
            int bz = h.z * 16 + 8;
            Component line = Text.color("&e" + h.count + " &7entities &8- &f" + h.world.getName() + " " + bx + ", " + bz);
            if (s instanceof Player) {
                line = line.clickEvent(ClickEvent.runCommand("/execute in " + h.world.getKey() + " run tp @s " + bx + " ~ " + bz))
                        .hoverEvent(HoverEvent.showText(Text.color("&7Click to teleport there")));
            }
            s.sendMessage(line);
        }
    }

    private static String colour(double tps) {
        double t = Math.min(20.0, tps);
        String c = t >= 18 ? "&a" : t >= 15 ? "&e" : "&c";
        return c + String.format("%.1f", t);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("chunks", "clear").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
