package com.wafflemc.scoreboard;

import com.wafflemc.host.api.AtomicYaml;
import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Services;
import com.wafflemc.host.api.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class SidebarModule extends ModuleBase implements Listener, TabExecutor {
    private static final class View {
        final Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        final Objective objective;
        final List<Team> teams = new ArrayList<>();
        final List<String> shown = new ArrayList<>();
        String title = "";

        View() {
            objective = board.registerNewObjective("waffle", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
        }
    }

    private final Map<UUID, View> views = new HashMap<>();
    private final Set<UUID> toggled = new HashSet<>();
    private AtomicYaml prefs;

    @Override
    protected void enable() {
        prefs = data("preferences.yml");
        for (String id : prefs.cfg().getStringList("toggled")) {
            try {
                toggled.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        permission("wafflecore.scoreboard", PermissionDefault.TRUE, "Use /sidebar");
        listen(this);
        command("sidebar", "Toggle the sidebar.", "wafflecore.scoreboard", List.of("sb"), this);
        long period = Math.max(10, config().getLong("update-ticks", 40));
        timer(this::refreshAll, 20L, period);
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    protected void disable() {
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
        views.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        later(() -> refresh(e.getPlayer()), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
    }

    private boolean wants(Player p) {
        boolean defaultOn = config().getBoolean("enabled-by-default", true);
        boolean flipped = toggled.contains(p.getUniqueId());
        return defaultOn != flipped && !config().getStringList("hidden-worlds").contains(p.getWorld().getName());
    }

    private void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    private void refresh(Player p) {
        if (!p.isOnline()) return;
        if (!wants(p) || !p.hasPermission("wafflecore.scoreboard")) {
            hide(p);
            return;
        }
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        if (p.getScoreboard() != v.board) p.setScoreboard(v.board);

        String title = config().getString("title", "");
        if (!title.equals(v.title)) {
            v.objective.displayName(Text.color(title));
            v.title = title;
        }

        List<String> lines = config().getStringList("lines");
        if (lines.size() > 15) lines = lines.subList(0, 15);
        while (v.teams.size() < lines.size()) {
            int i = v.teams.size();
            Team t = v.board.registerNewTeam("l" + i);
            // A unique invisible entry per line keeps lines with identical text apart.
            String entry = "§" + Integer.toHexString(i) + "§r";
            t.addEntry(entry);
            v.objective.getScore(entry).setScore(0);
            v.teams.add(t);
            v.shown.add(null);
        }
        for (int i = 0; i < v.teams.size(); i++) {
            Team t = v.teams.get(i);
            String entry = "§" + Integer.toHexString(i) + "§r";
            if (i >= lines.size()) {
                v.board.resetScores(entry);
                v.shown.set(i, null);
                continue;
            }
            String text = fill(lines.get(i), p);
            v.objective.getScore(entry).setScore(lines.size() - i);
            if (!text.equals(v.shown.get(i))) {
                t.prefix(Text.color(text));
                v.shown.set(i, text);
            }
        }
    }

    private void hide(Player p) {
        View v = views.remove(p.getUniqueId());
        if (v != null && p.getScoreboard() == v.board) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private String fill(String line, Player p) {
        String balance = Services.economy().map(e -> e.format(e.balance(p.getUniqueId()))).orElse("-");
        long ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Text.fill(line,
                "player", p.getName(),
                "balance", balance,
                "online", Bukkit.getOnlinePlayers().size(),
                "max", Bukkit.getMaxPlayers(),
                "ping", p.getPing(),
                "tps", String.format("%.1f", Math.min(20.0, Bukkit.getTPS()[0])),
                "playtime", Text.duration(ticks / 20),
                "world", p.getWorld().getName(),
                "x", p.getLocation().getBlockX(),
                "y", p.getLocation().getBlockY(),
                "z", p.getLocation().getBlockZ());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Text.send(sender, "&cPlayers only.");
            return true;
        }
        if (!toggled.remove(p.getUniqueId())) toggled.add(p.getUniqueId());
        prefs.cfg().set("toggled", toggled.stream().map(UUID::toString).toList());
        prefs.mark();
        refresh(p);
        Text.send(p, "Sidebar " + (wants(p) ? "&aon" : "&coff") + "&7.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return List.of();
    }
}
