package com.wafflemc.modlog;

import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Text;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionDefault;

public final class ModLog extends ModuleBase implements Listener, TabExecutor {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private File logDir;
    private ExecutorService io;
    private HttpClient http;
    private Set<String> logged = Set.of();
    private Set<String> hooked = Set.of();
    private String webhookUrl = "";
    private long nextHookAt;

    @Override
    protected void enable() {
        logDir = new File(dataFolder, "logs");
        logDir.mkdirs();
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WaffleCore-ModLog");
            t.setDaemon(true);
            return t;
        });
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        logged = lower(config().getStringList("logged-commands"));
        hooked = lower(config().getStringList("webhook.commands"));
        webhookUrl = config().getString("webhook.url", "").trim();
        if (!webhookUrl.isEmpty() && !webhookUrl.startsWith("https://")) {
            log.warning("[modlog] webhook.url must start with https://; Discord alerts are off.");
            webhookUrl = "";
        }
        permission("wafflecore.modlog", PermissionDefault.OP, "Read the moderation log");
        listen(this);
        command("modlog", "Search the moderation log.", "wafflecore.modlog", List.of("mlog"), this);
        io.execute(this::prune);
    }

    @Override
    protected void disable() {
        io.shutdown();
        try {
            io.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- recording --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        record(p.getName(), p.getLocation(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        String who = e.getSender() instanceof Player ? e.getSender().getName() : "CONSOLE";
        record(who, null, "/" + e.getCommand());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (config().getBoolean("log-joins") && e.getPlayer().hasPermission("wafflecore.staff")) {
            append(e.getPlayer().getName() + " joined");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (config().getBoolean("log-joins") && e.getPlayer().hasPermission("wafflecore.staff")) {
            append(e.getPlayer().getName() + " left");
        }
    }

    private void record(String who, Location at, String fullCommand) {
        String line = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
        String label = line.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!logged.contains(label) && !hooked.contains(label)) return;

        String where = at == null || at.getWorld() == null ? ""
                : " @" + at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
        append(who + where + " /" + line);
        if (!webhookUrl.isEmpty() && hooked.contains(label)) {
            notifyDiscord(who, "/" + line);
        }
    }

    private void append(String text) {
        LocalDateTime now = LocalDateTime.now();
        String entry = "[" + TIME.format(now) + "] " + text + System.lineSeparator();
        File file = new File(logDir, DAY.format(now) + ".log");
        if (io.isShutdown()) return;
        io.execute(() -> {
            try {
                Files.writeString(file.toPath(), entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                log.warning("[modlog] could not write log: " + ex.getMessage());
            }
        });
    }

    private void prune() {
        int days = config().getInt("retention-days", 30);
        if (days <= 0) return;
        LocalDate cutoff = LocalDate.now().minusDays(days);
        File[] files = logDir.listFiles((d, n) -> n.endsWith(".log"));
        if (files == null) return;
        for (File f : files) {
            try {
                if (LocalDate.parse(f.getName().replace(".log", ""), DAY).isBefore(cutoff)) f.delete();
            } catch (Exception ignored) {
            }
        }
    }

    // ---- Discord ----------------------------------------------------------------------------

    private void notifyDiscord(String who, String command) {
        // Discord allows roughly 30 requests per minute per webhook; space ours out.
        long delay;
        synchronized (this) {
            long now = System.currentTimeMillis();
            nextHookAt = Math.max(nextHookAt, now) + 2000;
            delay = nextHookAt - now - 2000;
        }
        String body = "{\"username\":\"" + json(config().getString("webhook.username", "WaffleCore"))
                + "\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"title\":\"Staff command\",\"color\":16753920,"
                + "\"fields\":[{\"name\":\"Staff\",\"value\":\"" + json(who) + "\",\"inline\":true},"
                + "{\"name\":\"Command\",\"value\":\"`" + json(command.length() > 900 ? command.substring(0, 900) : command).replace("`", "'") + "`\"}]}]}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        Bukkit.getScheduler().runTaskLaterAsynchronously(host, () -> http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .whenComplete((res, err) -> {
                    if (err != null) log.warning("[modlog] Discord webhook failed: " + err.getMessage());
                    else if (res.statusCode() >= 300) log.warning("[modlog] Discord webhook returned HTTP " + res.statusCode());
                }), Math.max(0, delay / 50));
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ---- /modlog ----------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String filter = null;
        int count = 15;
        for (String a : args) {
            if (a.matches("\\d{1,3}")) count = Math.min(50, Math.max(1, Integer.parseInt(a)));
            else filter = a.toLowerCase(Locale.ROOT);
        }
        final String needle = filter;
        final int limit = count;
        Bukkit.getScheduler().runTaskAsynchronously(host, () -> {
            List<String> found = search(needle, limit);
            sync(() -> {
                Text.send(sender, "&6Moderation log &8(" + (needle == null ? "latest" : "matching &e" + needle + "&8") + ", " + found.size() + " lines)");
                if (found.isEmpty()) Text.raw(sender, "&7Nothing found.");
                found.forEach(l -> Text.raw(sender, "&8" + l.replaceFirst("^(\\S+ \\S+)", "&7$1&f")));
            });
        });
        return true;
    }

    /** Searches today's log and then older days (up to a week) for the newest matching lines. */
    private List<String> search(String needle, int limit) {
        List<String> out = new ArrayList<>();
        for (int d = 0; d < 7 && out.size() < limit; d++) {
            LocalDate day = LocalDate.now().minusDays(d);
            File f = new File(logDir, DAY.format(day) + ".log");
            if (!f.isFile()) continue;
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0 && out.size() < limit; i--) {
                    String l = lines.get(i);
                    if (needle == null || l.toLowerCase(Locale.ROOT).contains(needle)) {
                        out.add(DAY.format(day) + " " + l);
                    }
                }
            } catch (IOException ex) {
                log.warning("[modlog] could not read " + f.getName() + ": " + ex.getMessage());
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        Set<String> names = new HashSet<>(logged);
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName().toLowerCase(Locale.ROOT)));
        String t = args[0].toLowerCase(Locale.ROOT);
        return names.stream().filter(n -> n.startsWith(t)).sorted().limit(30).toList();
    }

    private static Set<String> lower(List<String> in) {
        Set<String> out = new HashSet<>();
        in.forEach(s -> out.add(s.toLowerCase(Locale.ROOT)));
        return out;
    }
}
