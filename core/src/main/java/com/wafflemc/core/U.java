package com.wafflemc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

final class U {
   private static final Pattern DUR = Pattern.compile("(\\d+)([dhms])");

   private U() {
   }

   static String color(String var0) {
      return ChatColor.translateAlternateColorCodes('&', var0 == null ? "" : var0);
   }

   static void send(CommandSender var0, String var1) {
      var0.sendMessage(color(WaffleCore.get().getConfig().getString("prefix", "&6&lWaffle&e&lMC &8» &7") + var1));
   }

   static void raw(CommandSender var0, String var1) {
      var0.sendMessage(color(var1));
   }

   static boolean can(CommandSender var0, String var1, String var2) {
      return var0.hasPermission("wafflecore.admin")
         || var0.hasPermission("wafflecore." + var1)
         || var0.hasPermission("wafflecore." + var1 + "." + var2)
         || (U.legacy() && var0.hasPermission("staffcore.admin"))
         || (U.legacy() && var0.hasPermission("staffcore.command." + var2));
   }

   /** True when the old StaffCore permission nodes should still grant access (config: legacy-staffcore-permissions). */
   static boolean legacy() {
      return WaffleCore.get().getConfig().getBoolean("legacy-staffcore-permissions", false);
   }

   static String money(double var0) {
      String var2 = WaffleCore.get().getConfig().getString("currency", "$");
      return var2 + String.format(Locale.US, "%,.2f", var0);
   }

   static long parseDuration(String var0) {
      if (var0 == null) {
         return Long.MIN_VALUE;
      } else {
         String var1 = var0.toLowerCase(Locale.ROOT).trim();
         if (var1.equals("-1")) {
            return -1L;
         } else if (var1.matches("\\d+")) {
            return Long.parseLong(var1);
         } else {
            Matcher var2 = DUR.matcher(var1);
            long var3 = 0L;

            int var5;
            for (var5 = 0; var2.find(); var5 = var2.end()) {
               if (var2.start() != var5) {
                  return Long.MIN_VALUE;
               }

               long var6 = Long.parseLong(var2.group(1));
               String var8 = var2.group(2);
               switch (var8) {
                  case "d":
                     var3 += var6 * 86400L;
                     break;
                  case "h":
                     var3 += var6 * 3600L;
                     break;
                  case "m":
                     var3 += var6 * 60L;
                     break;
                  default:
                     var3 += var6;
               }
            }

            return var5 == var1.length() && var5 != 0 ? var3 : Long.MIN_VALUE;
         }
      }
   }

   static String duration(long var0) {
      if (var0 <= 0L) {
         return "0s";
      } else {
         long var2 = var0 / 86400L;
         long var4 = var0 % 86400L / 3600L;
         long var6 = var0 % 3600L / 60L;
         long var8 = var0 % 60L;
         StringBuilder var10 = new StringBuilder();
         if (var2 > 0L) {
            var10.append(var2).append("d ");
         }

         if (var4 > 0L) {
            var10.append(var4).append("h ");
         }

         if (var6 > 0L) {
            var10.append(var6).append("m ");
         }

         if (var8 > 0L && var2 == 0L) {
            var10.append(var8).append("s");
         }

         return var10.toString().trim();
      }
   }

   static Double parseAmount(String var0) {
      try {
         double var1 = Double.parseDouble(var0.replace(",", ""));
         return !Double.isNaN(var1) && !Double.isInfinite(var1) ? (double)Math.round(var1 * 100.0) / 100.0 : null;
      } catch (NumberFormatException var3) {
         return null;
      }
   }

   static List<String> filter(List<String> var0, String var1) {
      ArrayList var2 = new ArrayList();
      String var3 = var1.toLowerCase(Locale.ROOT);

      for (String var5 : var0) {
         if (var5.toLowerCase(Locale.ROOT).startsWith(var3)) {
            var2.add(var5);
         }
      }

      return var2;
   }
}
