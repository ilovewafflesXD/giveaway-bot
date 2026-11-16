package com.wafflemc.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

final class Eco implements CommandExecutor, TabCompleter, Listener {
   private final WaffleCore plugin;
   private final Yml y;

   Eco(WaffleCore var1) {
      this.plugin = var1;
      this.y = var1.store("economy.yml");
      if (!this.y.file.exists()) {
         this.importEssentials();
      }
   }

   int importEssentials() {
      File var1 = new File(this.plugin.getDataFolder().getParentFile(), "Essentials/userdata");
      File[] var2 = var1.listFiles((var0, var1x) -> var1x.endsWith(".yml"));
      if (var2 == null) {
         return 0;
      } else {
         int var3 = 0;

         for (File var7 : var2) {
            String var8 = var7.getName().substring(0, var7.getName().length() - 4);

            try {
               UUID.fromString(var8);
            } catch (IllegalArgumentException var14) {
               continue;
            }

            YamlConfiguration var9 = YamlConfiguration.loadConfiguration(var7);

            double var10;
            try {
               var10 = Double.parseDouble(var9.getString("money", "0"));
            } catch (NumberFormatException var13) {
               var10 = 0.0;
            }

            this.y.cfg.set("bal." + var8, var10);
            String var12 = var9.getString("last-account-name");
            if (var12 != null) {
               this.y.cfg.set("name." + var8, var12);
               this.nameIdx = null;
            }

            var3++;
         }

         this.y.mark();
         this.y.save();
         return var3;
      }
   }

   double get(UUID var1) {
      return this.y.cfg.getDouble("bal." + var1, this.plugin.getConfig().getDouble("starting-balance", 0.0));
   }

   void set(UUID var1, double var2) {
      double var4 = this.get(var1);
      double var6 = Math.max(0.0, (double)Math.round(var2 * 100.0) / 100.0);
      this.y.cfg.set("bal." + var1, var6);
      this.y.mark();
      this.topAt = 0L;
      if (Math.abs(var6 - var4) > 1.0E-9) {
         this.txLog(var1, var4, var6);
      }
   }

   /** Appends one line per balance change to economy.log, off the main thread. */
   private void txLog(UUID var1, double var2, double var4) {
      String var6 = LOG_TIME.format(Instant.now()) + " " + this.nameOf(var1) + " (" + var1 + ") " + String.format(Locale.US, "%.2f -> %.2f (%+.2f)", var2, var4, var4 - var2) + (this.ctx == null ? "" : " [" + this.ctx + "]") + "\n";
      File var7 = new File(this.plugin.getDataFolder(), "economy.log");
      this.plugin.io(() -> {
         try {
            Files.writeString(var7.toPath(), var6, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException var4x) {
            this.plugin.getLogger().warning("Could not write economy.log: " + var4x.getMessage());
         }
      });
   }

   private volatile Map<String, UUID> nameIdx;
   private List<Map.Entry<String, Double>> topCache = List.of();
   private long topAt;
   private String ctx;
   private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

   void add(UUID var1, double var2) {
      this.set(var1, this.get(var1) + var2);
   }

   private boolean takeCtx(UUID var1, double var2, String var4) {
      this.ctx = var4;
      boolean var5 = this.take(var1, var2);
      this.ctx = var5 ? var4 : null;
      return var5;
   }

   boolean take(UUID var1, double var2) {
      if (this.get(var1) + 1.0E-9 < var2) {
         return false;
      } else {
         this.set(var1, this.get(var1) - var2);
         return true;
      }
   }

   UUID find(String var1) {
      Player var2 = Bukkit.getPlayerExact(var1);
      if (var2 != null) {
         return var2.getUniqueId();
      } else {
         Map var3 = this.nameIdx;
         if (var3 == null) {
            var3 = this.buildNameIndex();
         }

         return (UUID)var3.get(var1.toLowerCase(Locale.ROOT));
      }
   }

   private Map<String, UUID> buildNameIndex() {
      HashMap<String, UUID> var1 = new HashMap<>();
      ConfigurationSection var2 = this.y.cfg.getConfigurationSection("name");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            String var5 = var2.getString(var4);
            if (var5 != null) {
               try {
                  var1.putIfAbsent(var5.toLowerCase(Locale.ROOT), UUID.fromString(var4));
               } catch (IllegalArgumentException var7) {
               }
            }
         }
      }

      this.nameIdx = var1;
      return var1;
   }

   String nameOf(UUID var1) {
      Player var2 = Bukkit.getPlayer(var1);
      return var2 != null ? var2.getName() : this.y.cfg.getString("name." + var1, var1.toString().substring(0, 8));
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      if (!var2.getName().equals(this.y.cfg.getString("name." + var2.getUniqueId()))) {
         this.y.cfg.set("name." + var2.getUniqueId(), var2.getName());
         this.nameIdx = null;
      }

      if (!this.y.cfg.contains("bal." + var2.getUniqueId())) {
         this.y.cfg.set("bal." + var2.getUniqueId(), this.plugin.getConfig().getDouble("starting-balance", 0.0));
      }

      this.y.mark();
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      switch (var5) {
         case "balance":
            return this.balance(var1, var4);
         case "pay":
            return this.pay(var1, var4);
         case "baltop":
            return this.baltop(var1);
         case "eco":
            return this.admin(var1, var4);
         default:
            return false;
      }
   }

   private boolean balance(CommandSender var1, String[] var2) {
      UUID var3;
      if (var2.length == 0) {
         if (!(var1 instanceof Player)) {
            U.send(var1, "&cConsole must specify a player.");
            return true;
         }

         var3 = ((Player)var1).getUniqueId();
      } else {
         if (!var1.hasPermission("wafflecore.balance.others")) {
            U.send(var1, "&cYou can't check other players' balances.");
            return true;
         }

         var3 = this.find(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cNo player named &e" + var2[0] + "&c found.");
            return true;
         }
      }

      boolean var4 = var1 instanceof Player && ((Player)var1).getUniqueId().equals(var3);
      U.send(var1, (var4 ? "Your balance: " : "&e" + this.nameOf(var3) + "&7's balance: ") + "&6" + U.money(this.get(var3)));
      return true;
   }

   private boolean pay(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (var2.length < 2) {
         U.send(var1, "&cUsage: /pay <player> <amount>");
         return true;
      } else {
         UUID var4 = this.find(var2[0]);
         Double var5 = U.parseAmount(var2[1]);
         if (var4 == null) {
            U.send(var1, "&cNo player named &e" + var2[0] + "&c found.");
            return true;
         } else if (var5 == null || var5 <= 0.0) {
            U.send(var1, "&cEnter a valid amount above zero.");
            return true;
         } else if (var4.equals(var3.getUniqueId())) {
            U.send(var1, "&cYou can't pay yourself.");
            return true;
         } else if (!this.takeCtx(var3.getUniqueId(), var5, "pay to " + this.nameOf(var4))) {
            U.send(var1, "&cYou only have &6" + U.money(this.get(var3.getUniqueId())) + "&c.");
            return true;
         } else {
            this.ctx = "pay from " + var3.getName();
            this.add(var4, var5);
            this.ctx = null;
            U.send(var1, "Sent &6" + U.money(var5) + "&7 to &e" + this.nameOf(var4) + "&7.");
            Player var6 = Bukkit.getPlayer(var4);
            if (var6 != null) {
               U.send(var6, "&e" + var3.getName() + "&7 sent you &6" + U.money(var5) + "&7.");
            }

            return true;
         }
      }
   }

   private boolean baltop(CommandSender var1) {
      long var2 = System.currentTimeMillis();
      if (var2 - this.topAt > 15000L || this.topCache.isEmpty()) {
         ConfigurationSection var4 = this.y.cfg.getConfigurationSection("bal");
         if (var4 == null) {
            U.send(var1, "No balances yet.");
            return true;
         }

         ArrayList<Map.Entry<String, Double>> var5 = new ArrayList<>();

         for (String var7 : var4.getKeys(false)) {
            var5.add(Map.entry(var7, var4.getDouble(var7)));
         }

         var5.sort((var0, var1x) -> Double.compare(var1x.getValue(), var0.getValue()));
         this.topCache = List.copyOf(var5.subList(0, Math.min(10, var5.size())));
         this.topAt = var2;
      }

      U.raw(var1, "&8&m      &r &6&lWaffle&e&lMC &6Rich List &8&m      ");
      int var8 = 1;

      for (Map.Entry<String, Double> var6 : this.topCache) {
         U.raw(var1, "&e" + var8++ + ". &f" + this.nameOf(UUID.fromString(var6.getKey())) + " &8- &6" + U.money(var6.getValue()));
      }

      return true;
   }

   private boolean admin(CommandSender var1, String[] var2) {
      if (!var1.hasPermission("wafflecore.admin")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length == 1 && var2[0].equalsIgnoreCase("import")) {
         int var7 = this.importEssentials();
         U.send(var1, "Imported &e" + var7 + "&7 balances from EssentialsX.");
         return true;
      } else if (var2.length < 3) {
         U.send(var1, "&cUsage: /eco <give|take|set> <player> <amount> | /eco import");
         return true;
      } else {
         UUID var3 = this.find(var2[1]);
         Double var4 = U.parseAmount(var2[2]);
         if (var3 != null && var4 != null && !(var4 < 0.0)) {
            String var5 = var2[0].toLowerCase(Locale.ROOT);
            this.ctx = "/eco " + var5 + " by " + var1.getName();
            switch (var5) {
               case "give":
                  this.add(var3, var4);
                  break;
               case "take":
                  this.set(var3, this.get(var3) - var4);
                  break;
               case "set":
                  this.set(var3, var4);
                  break;
               default:
                  this.ctx = null;
                  U.send(var1, "&cUsage: /eco <give|take|set> <player> <amount>");
                  return true;
            }

            this.ctx = null;
            U.send(var1, "&e" + this.nameOf(var3) + "&7 now has &6" + U.money(this.get(var3)) + "&7.");
            return true;
         } else {
            U.send(var1, "&cInvalid player or amount.");
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (var5.equals("eco")) {
         if (var4.length == 1) {
            return U.filter(List.of("give", "take", "set", "import"), var4[0]);
         } else {
            return var4.length == 2 ? onlineNames(var4[1]) : List.of();
         }
      } else {
         return var4.length == 1 ? onlineNames(var4[0]) : List.of();
      }
   }

   static List<String> onlineNames(String var0) {
      ArrayList var1 = new ArrayList();

      for (Player var3 : Bukkit.getOnlinePlayers()) {
         var1.add(var3.getName());
      }

      return U.filter(var1, var0);
   }
}
