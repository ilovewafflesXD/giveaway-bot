package com.wafflemc.host.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Message helpers using the same {@code &} colour codes as the rest of WaffleCore. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    /** Sends a prefixed message. */
    public static void send(CommandSender to, String message) {
        to.sendMessage(color(Services.prefix() + message));
    }

    /** Sends a message without the prefix. */
    public static void raw(CommandSender to, String message) {
        to.sendMessage(color(message));
    }

    /** Prepends the WaffleCore prefix, for messages that are broadcast rather than sent to one sender. */
    public static String fillPrefix(String message) {
        return Services.prefix() + message;
    }

    /** Replaces {key} placeholders; pairs are key, value, key, value... */
    public static String fill(String template, Object... pairs) {
        String out = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return out;
    }

    public static String duration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long d = seconds / 86400;
        long h = seconds % 86400 / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
