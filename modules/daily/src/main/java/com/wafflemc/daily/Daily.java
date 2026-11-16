package com.wafflemc.daily;

import com.wafflemc.host.api.AtomicYaml;
import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Services;
import com.wafflemc.host.api.Text;
import com.wafflemc.host.api.WaffleEconomy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionDefault;

public final class Daily extends ModuleBase implements Listener, TabExecutor {
    private AtomicYaml store;

    @Override
    protected void enable() {
        store = data("daily.yml");
        permission("wafflecore.daily", PermissionDefault.TRUE, "Claim the daily reward");
        listen(this);
        command("daily", "Claim your daily reward.", "wafflecore.daily", List.of("dailyreward"), this);
        autosave(1200L);
        if (Services.economy().isEmpty()) {
            log.warning("[daily] the core economy is not available, so rewards will only run milestone commands.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!config().getBoolean("join-reminder", true)) return;
        Player p = e.getPlayer();
        later(() -> {
            if (p.isOnline() && p.hasPermission("wafflecore.daily") && remaining(p.getUniqueId()) <= 0) {
                Text.send(p, config().getString("messages.reminder"));
            }
        }, 80L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "&cPlayers only.");
            return true;
        }
        UUID id = p.getUniqueId();
        long wait = remaining(id);
        if (wait > 0) {
            Text.send(p, Text.fill(config().getString("messages.not-ready"), "time", Text.duration(wait / 1000)));
            int streak = store.cfg().getInt("players." + id + ".streak", 0);
            if (streak > 0) Text.raw(p, "&7Current streak: &e" + streak + " days");
            return true;
        }

        long now = System.currentTimeMillis();
        String base = "players." + id;
        long last = store.cfg().getLong(base + ".last", 0);
        long window = config().getLong("streak-window-hours", 48) * 3_600_000L;
        int streak = store.cfg().getInt(base + ".streak", 0);
        if (last != 0 && now - last > window && streak > 0) {
            Text.send(p, config().getString("messages.streak-lost"));
            streak = 0;
        }
        streak++;

        double amount = config().getDouble("reward.base", 250)
                + config().getDouble("reward.per-streak-day", 50) * Math.min(streak - 1, config().getInt("reward.streak-cap", 30));
        ConfigurationSection milestone = config().getConfigurationSection("milestones." + streak);
        if (milestone != null) amount += milestone.getDouble("money", 0);

        store.cfg().set(base + ".last", now);
        store.cfg().set(base + ".streak", streak);
        store.cfg().set(base + ".best", Math.max(streak, store.cfg().getInt(base + ".best", 0)));
        store.cfg().set(base + ".total", store.cfg().getInt(base + ".total", 0) + 1);
        store.mark();

        WaffleEconomy eco = Services.economy().orElse(null);
        String shown = "nothing";
        if (eco != null && amount > 0) {
            eco.deposit(id, amount);
            shown = eco.format(amount);
        }
        Text.send(p, Text.fill(config().getString("messages.claimed"), "amount", shown, "streak", streak));
        if (milestone != null) {
            String m = milestone.getString("message");
            if (m != null) Text.send(p, m);
            for (String c : milestone.getStringList("commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", p.getName()));
            }
        }
        return true;
    }

    /** Milliseconds until the player can claim again; zero or less means they can claim now. */
    private long remaining(UUID id) {
        long last = store.cfg().getLong("players." + id + ".last", 0);
        long cooldown = config().getLong("cooldown-hours", 20) * 3_600_000L;
        return last + cooldown - System.currentTimeMillis();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return List.of();
    }
}
