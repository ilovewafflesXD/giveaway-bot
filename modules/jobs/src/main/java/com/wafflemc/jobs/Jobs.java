package com.wafflemc.jobs;

import com.wafflemc.host.api.AtomicYaml;
import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Services;
import com.wafflemc.host.api.Text;
import com.wafflemc.host.api.WaffleEconomy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;

public final class Jobs extends ModuleBase implements Listener, TabExecutor {
    private static final int PLACED_LIMIT = 30_000;

    private AtomicYaml store;
    private final LinkedHashSet<Long> placed = new LinkedHashSet<>();

    @Override
    protected void enable() {
        store = data("jobs.yml");
        permission("wafflecore.jobs", PermissionDefault.TRUE, "Use /jobs");
        listen(this);
        command("jobs", "Join jobs and earn money.", "wafflecore.jobs", List.of("job"), this);
        autosave(1200L);
        if (Services.economy().isEmpty()) {
            log.warning("[jobs] the core economy is not available, so jobs cannot pay out until core.jar is running.");
        }
    }

    // ---- earning ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!config().getBoolean("track-placed-blocks", true)) return;
        // Replanted crops must still pay when harvested, so only non-crop blocks are tracked.
        if (e.getBlockPlaced().getBlockData() instanceof Ageable || e.getBlockPlaced().getType() == org.bukkit.Material.SUGAR_CANE) return;
        placed.add(key(e.getBlockPlaced()));
        if (placed.size() > PLACED_LIMIT) {
            var it = placed.iterator();
            it.next();
            it.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        Block b = e.getBlock();
        boolean wasPlaced = placed.remove(key(b));
        if (wasPlaced) return;
        if (b.getBlockData() instanceof Ageable crop && crop.getAge() < crop.getMaximumAge()) return;
        for (String job : jobsOf(p.getUniqueId())) {
            ConfigurationSection s = config().getConfigurationSection("jobs." + job + ".blocks");
            Double pay = lookup(s, b.getType().name());
            if (pay != null) {
                earn(p, job, pay);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || p.getGameMode() != GameMode.SURVIVAL) return;
        LivingEntity dead = e.getEntity();
        if (config().getBoolean("ignore-spawner-mobs", true)) {
            SpawnReason r = dead.getEntitySpawnReason();
            if (r == SpawnReason.SPAWNER || r == SpawnReason.SPAWNER_EGG) return;
        }
        for (String job : jobsOf(p.getUniqueId())) {
            Double pay = lookup(config().getConfigurationSection("jobs." + job + ".mobs"), dead.getType().name());
            if (pay != null) {
                earn(p, job, pay);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item caught)) return;
        Player p = e.getPlayer();
        ItemStack stack = caught.getItemStack();
        for (String job : jobsOf(p.getUniqueId())) {
            ConfigurationSection s = config().getConfigurationSection("jobs." + job + ".catches");
            if (s == null) continue;
            Double pay = lookup(s, stack.getType().name());
            if (pay == null) pay = s.contains("default") ? s.getDouble("default") : null;
            if (pay != null) {
                earn(p, job, pay);
                return;
            }
        }
    }

    private void earn(Player p, String job, double basePay) {
        UUID id = p.getUniqueId();
        String root = "players." + id + ".";
        int level = Math.max(1, store.cfg().getInt(root + job + ".level", 1));
        double pay = basePay * (1 + config().getDouble("pay-bonus-per-level", 0.02) * (level - 1));
        pay = Math.round(pay * 100.0) / 100.0;

        // Daily income cap
        double cap = config().getDouble("daily-income-cap", 0);
        String today = LocalDate.now().toString();
        if (!today.equals(store.cfg().getString(root + "day"))) {
            store.cfg().set(root + "day", today);
            store.cfg().set(root + "earned", 0.0);
        }
        double earned = store.cfg().getDouble(root + "earned", 0.0);
        if (cap > 0 && earned >= cap) {
            if (earned < cap + 0.01 || Math.random() < 0.02) {
                Text.send(p, config().getString("messages.cap-reached"));
            }
            store.cfg().set(root + "earned", cap + 0.01);
            return;
        }
        if (cap > 0) pay = Math.min(pay, cap - earned);

        WaffleEconomy eco = Services.economy().orElse(null);
        if (eco != null) eco.deposit(id, pay);
        store.cfg().set(root + "earned", earned + pay);

        double xp = store.cfg().getDouble(root + job + ".xp", 0) + Math.max(1.0, basePay * config().getDouble("xp-per-money", 8));
        int max = config().getInt("max-level", 100);
        boolean leveled = false;
        while (level < max && xp >= xpFor(level)) {
            xp -= xpFor(level);
            level++;
            leveled = true;
        }
        if (level >= max) xp = 0;
        store.cfg().set(root + job + ".level", level);
        store.cfg().set(root + job + ".xp", xp);
        store.mark();

        String name = Text.strip(config().getString("jobs." + job + ".name", job));
        String bar = "&6+" + (eco != null ? eco.format(pay) : String.format("%.2f", pay)) + " &8| &7" + name + " &8" + level;
        p.sendActionBar(Text.color(bar));
        if (leveled) {
            Text.send(p, Text.fill(config().getString("messages.level-up"), "job", name, "level", level));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    private static double xpFor(int level) {
        return 100.0 * Math.pow(level, 1.4);
    }

    private static Double lookup(ConfigurationSection s, String name) {
        if (s == null) return null;
        if (s.isSet(name)) return s.getDouble(name);
        for (String key : s.getKeys(false)) {
            if (key.startsWith("*") && name.endsWith(key.substring(1))) return s.getDouble(key);
        }
        return null;
    }

    private static long key(Block b) {
        long pos = ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | ((long) (b.getY() + 64) & 0xFFF);
        return pos ^ (b.getWorld().getUID().getLeastSignificantBits() * 0x9E3779B97F4A7C15L);
    }

    private List<String> jobsOf(UUID id) {
        return store.cfg().getStringList("players." + id + ".joined");
    }

    // ---- /jobs ------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "me" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "join" -> {
                if (!(sender instanceof Player p)) return player(sender);
                if (args.length < 2) return false;
                join(p, args[1].toLowerCase(Locale.ROOT));
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) return player(sender);
                if (args.length < 2) return false;
                leave(p, args[1].toLowerCase(Locale.ROOT));
            }
            case "top" -> top(sender, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null);
            case "me", "info" -> {
                if (!(sender instanceof Player p)) return player(sender);
                me(p);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean player(CommandSender s) {
        Text.send(s, "&cPlayers only.");
        return true;
    }

    private void list(CommandSender s) {
        Text.raw(s, "&8&m      &r &6&lJobs &8&m      ");
        ConfigurationSection jobs = config().getConfigurationSection("jobs");
        if (jobs == null) return;
        for (String id : jobs.getKeys(false)) {
            Text.raw(s, jobs.getString(id + ".name", id) + " &8- &7" + jobs.getString(id + ".description", "") + " &8(/jobs join " + id + ")");
        }
    }

    private void join(Player p, String job) {
        if (!config().isConfigurationSection("jobs." + job)) {
            Text.send(p, "&cThere's no job called &e" + job + "&c. Try /jobs list.");
            return;
        }
        List<String> mine = new ArrayList<>(jobsOf(p.getUniqueId()));
        if (mine.contains(job)) {
            Text.send(p, "&cYou already have that job.");
            return;
        }
        if (mine.size() >= config().getInt("max-jobs", 2)) {
            Text.send(p, "&cYou can only have &e" + config().getInt("max-jobs", 2) + "&c jobs. Leave one first with /jobs leave <job>.");
            return;
        }
        mine.add(job);
        store.cfg().set("players." + p.getUniqueId() + ".joined", mine);
        store.cfg().set("players." + p.getUniqueId() + ".name", p.getName());
        store.mark();
        Text.send(p, Text.fill(config().getString("messages.joined"), "job", Text.strip(config().getString("jobs." + job + ".name", job))));
    }

    private void leave(Player p, String job) {
        List<String> mine = new ArrayList<>(jobsOf(p.getUniqueId()));
        if (!mine.remove(job)) {
            Text.send(p, "&cYou don't have that job.");
            return;
        }
        store.cfg().set("players." + p.getUniqueId() + ".joined", mine);
        store.mark();
        Text.send(p, Text.fill(config().getString("messages.left"), "job", Text.strip(config().getString("jobs." + job + ".name", job))));
    }

    private void me(Player p) {
        List<String> mine = jobsOf(p.getUniqueId());
        Text.raw(p, "&8&m      &r &6&lYour Jobs &8&m      ");
        if (mine.isEmpty()) {
            Text.raw(p, "&7You have no jobs. Use &e/jobs list &7then &e/jobs join <job>&7.");
            return;
        }
        String root = "players." + p.getUniqueId() + ".";
        for (String job : mine) {
            int level = Math.max(1, store.cfg().getInt(root + job + ".level", 1));
            double xp = store.cfg().getDouble(root + job + ".xp", 0);
            double need = xpFor(level);
            int filled = (int) Math.min(20, Math.round(20 * xp / need));
            String bar = "&a" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled);
            Text.raw(p, config().getString("jobs." + job + ".name", job) + " &7level &e" + level + " " + bar + " &7" + (int) xp + "/" + (int) need);
        }
        Text.raw(p, "&7Earned today: &6" + Services.economy().map(e -> e.format(store.cfg().getDouble(root + "earned", 0))).orElse("-"));
    }

    private void top(CommandSender s, String job) {
        if (job == null || !config().isConfigurationSection("jobs." + job)) {
            Text.send(s, "&cUsage: /jobs top <job>");
            return;
        }
        record Row(String name, int level, double xp) { }
        List<Row> rows = new ArrayList<>();
        ConfigurationSection players = store.cfg().getConfigurationSection("players");
        if (players != null) {
            for (String id : players.getKeys(false)) {
                if (!players.isSet(id + "." + job + ".level")) continue;
                rows.add(new Row(players.getString(id + ".name", id.substring(0, 8)), players.getInt(id + "." + job + ".level"),
                        players.getDouble(id + "." + job + ".xp")));
            }
        }
        rows.sort((a, b) -> a.level != b.level ? Integer.compare(b.level, a.level) : Double.compare(b.xp, a.xp));
        Text.raw(s, "&8&m      &r &6" + Text.strip(config().getString("jobs." + job + ".name", job)) + " leaderboard &8&m      ");
        int i = 1;
        for (Row r : rows.stream().limit(10).toList()) {
            Text.raw(s, "&e" + i++ + ". &f" + r.name + " &8- &7level &e" + r.level);
        }
        if (rows.isEmpty()) Text.raw(s, "&7Nobody has levelled this job yet.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String t = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return List.of("list", "join", "leave", "top", "me").stream().filter(x -> x.startsWith(t)).toList();
        if (args.length == 2 && List.of("join", "leave", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
            ConfigurationSection jobs = config().getConfigurationSection("jobs");
            if (jobs == null) return List.of();
            return jobs.getKeys(false).stream().filter(x -> x.startsWith(t)).toList();
        }
        return List.of();
    }

}
