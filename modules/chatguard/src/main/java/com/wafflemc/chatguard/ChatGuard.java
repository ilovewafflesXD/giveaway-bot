package com.wafflemc.chatguard;

import com.wafflemc.host.api.ModuleBase;
import com.wafflemc.host.api.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionDefault;

public final class ChatGuard extends ModuleBase implements Listener {
    private static final Pattern URL = Pattern.compile(
            "(?i)(https?://|www\\.)\\S+|\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?\\b|\\b[a-z0-9-]{2,}\\s?(\\.|\\(dot\\)|\\[dot])\\s?(com|net|org|gg|io|xyz|me|co|uk|eu|tk|ml|gq|cf|ga|club|fun|online|site|lol|pw)\\b(/\\S*)?");

    private static final class State {
        final Deque<Long> times = new ArrayDeque<>();
        String last = "";
        long lastAt;
        double points;
        long pointsAt;
        int stepReached = -1;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private record Word(Pattern whole, String squashed) { }

    private List<Word> words = List.of();

    @Override
    protected void enable() {
        permission("wafflecore.chatguard.bypass", PermissionDefault.OP, "Skip all ChatGuard checks");
        permission("wafflecore.chatguard.alerts", PermissionDefault.OP, "See ChatGuard alerts");
        words = config().getStringList("words.list").stream()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .filter(w -> !w.replaceAll("[^a-z]", "").isEmpty())
                .map(w -> new Word(Pattern.compile("\\b" + Pattern.quote(w) + "\\b"), w.replaceAll("[^a-z]", "")))
                .toList();
        listen(this);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("wafflecore.chatguard.bypass")) return;
        String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        long now = System.currentTimeMillis();

        synchronized (s) {
            decay(s, now);

            // Content checks come first because they are the most serious.
            if (config().getBoolean("words.enabled") && containsBlockedWord(raw)) {
                block(e, p, s, "words", raw);
                return;
            }
            if (config().getBoolean("links.enabled") && hasDisallowedLink(raw)) {
                block(e, p, s, "links", raw);
                return;
            }

            if (config().getBoolean("rate.enabled")) {
                long min = config().getLong("rate.min-interval-ms", 700);
                int burst = config().getInt("rate.burst-messages", 5);
                long window = config().getLong("rate.burst-seconds", 6) * 1000L;
                while (!s.times.isEmpty() && now - s.times.peekFirst() > window) s.times.pollFirst();
                boolean tooFast = s.lastAt != 0 && now - s.lastAt < min;
                boolean burstHit = s.times.size() >= burst;
                if (tooFast || burstHit) {
                    s.lastAt = now;
                    block(e, p, s, "rate", raw);
                    return;
                }
            }

            String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (config().getBoolean("repeat.enabled") && !norm.isEmpty() && norm.equals(s.last)
                    && now - s.lastAt < config().getLong("repeat.seconds", 30) * 1000L) {
                s.lastAt = now;
                block(e, p, s, "repeat", raw);
                return;
            }

            s.times.addLast(now);
            s.last = norm;
            s.lastAt = now;
        }

        // Cosmetic fixes: change the message instead of blocking it.
        String fixed = raw;
        if (config().getBoolean("flood-chars.enabled")) {
            int run = Math.max(2, config().getInt("flood-chars.max-run", 3));
            fixed = collapseRuns(raw, run);
        }
        if (config().getBoolean("caps.enabled") && tooManyCaps(fixed)) {
            fixed = fixed.toLowerCase(Locale.ROOT);
        }
        if (!fixed.equals(raw)) {
            e.message(Component.text(fixed));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    // ---- checks -----------------------------------------------------------------------------

    private boolean containsBlockedWord(String msg) {
        String leet = unleet(msg.toLowerCase(Locale.ROOT));
        String squashed = leet.replaceAll("[^a-z]", "");
        for (Word w : words) {
            if (w.whole().matcher(leet).find()) return true;
            // Catches "n i g g a" and "n.i.g.g.a" but only for longer words, to avoid false hits.
            if (w.squashed().length() >= 5 && squashed.contains(w.squashed())) return true;
        }
        return false;
    }

    private static String unleet(String s) {
        return s.replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a')
                .replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's').replace('!', 'i');
    }

    private boolean hasDisallowedLink(String msg) {
        var m = URL.matcher(msg);
        List<String> allowed = config().getStringList("links.allowed");
        while (m.find()) {
            String hit = m.group().toLowerCase(Locale.ROOT);
            boolean ok = false;
            for (String a : allowed) {
                String al = a.toLowerCase(Locale.ROOT);
                if (hit.contains(al) || al.contains(hit)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return true;
        }
        return false;
    }

    private boolean tooManyCaps(String msg) {
        if (msg.length() < config().getInt("caps.min-length", 8)) return false;
        int letters = 0;
        int upper = 0;
        for (char c : msg.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters >= 6 && (double) upper / letters > config().getDouble("caps.max-ratio", 0.7);
    }

    private static String collapseRuns(String s, int max) {
        StringBuilder sb = new StringBuilder(s.length());
        int run = 0;
        char prev = 0;
        for (char c : s.toCharArray()) {
            run = (c == prev) ? run + 1 : 1;
            prev = c;
            boolean floodable = Character.isLetter(c) || c == (char) 33 || c == (char) 63;
            if (run <= max || !floodable) sb.append(c);
        }
        return sb.toString();
    }

    // ---- reaction ---------------------------------------------------------------------------

    private void block(AsyncChatEvent e, Player p, State s, String reason, String message) {
        e.setCancelled(true);
        double add = config().getDouble("points." + reason, 1);
        s.points += add;
        s.pointsAt = System.currentTimeMillis();
        String pmsg = config().getString("messages." + reason, "&cMessage blocked.");
        String alert = Text.fill(config().getString("messages.alert"), "player", p.getName(), "reason", reason, "message", message);
        double total = s.points;
        int reached = stepFor(total);
        final int previous = s.stepReached;
        if (reached > previous) s.stepReached = reached;
        List<String> commands = new ArrayList<>();
        if (reached > previous) {
            var steps = config().getMapList("steps");
            for (int i = previous + 1; i <= reached; i++) {
                Object cmds = steps.get(i).get("commands");
                if (cmds instanceof List<?> l) l.forEach(o -> commands.add(String.valueOf(o).replace("{player}", p.getName())));
            }
        }
        sync(() -> {
            Text.raw(p, pmsg);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("wafflecore.chatguard.alerts")) Text.raw(staff, alert);
            }
            commands.forEach(c -> {
                log.info("[chatguard] running: " + c);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            });
        });
    }

    private int stepFor(double points) {
        int best = -1;
        var steps = config().getMapList("steps");
        for (int i = 0; i < steps.size(); i++) {
            Object need = steps.get(i).get("points");
            if (need instanceof Number n && points >= n.doubleValue()) best = i;
        }
        return best;
    }

    private void decay(State s, long now) {
        long window = config().getLong("decay-minutes", 10) * 60_000L;
        if (s.points > 0 && now - s.pointsAt > window) {
            s.points = 0;
            s.stepReached = -1;
        }
    }
}
