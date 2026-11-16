package com.wafflemc.core;

import com.wafflemc.host.Module;
import com.wafflemc.host.api.Services;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class WaffleCore implements Module {
   private static WaffleCore instance;
   JavaPlugin host;
   private FileConfiguration config;
   private YamlConfiguration descriptor;
   private final List<Yml> stores = new ArrayList<>();
   private final List<Listener> listeners = new ArrayList<>();
   private final Map<String, Command> owned = new LinkedHashMap<>();
   private final Map<String, Command> displaced = new HashMap<>();
   private final List<String> permissions = new ArrayList<>();
   Eco eco;
   Combat combat;
   Teleports tp;
   Kits kits;
   Auction auction;
   Orders orders;
   Mining mining;
   Staff staff;
   Moderation mod;
   Tools tools;
   private ExecutorService ioPool;

   /** Queues work on the single background IO thread (file writes, logs). */
   void io(Runnable var1) {
      if (this.ioPool == null || this.ioPool.isShutdown()) {
         var1.run();
      } else {
         this.ioPool.execute(var1);
      }
   }

   /** Runs work on the IO thread and waits for it, so it lands after anything already queued. */
   void ioWait(Runnable var1) {
      if (this.ioPool == null || this.ioPool.isShutdown()) {
         var1.run();
      } else {
         try {
            this.ioPool.submit(var1).get(30L, TimeUnit.SECONDS);
         } catch (Exception var3) {
            this.getLogger().warning("IO task failed: " + var3);
         }
      }
   }

   static WaffleCore get() {
      return instance;
   }

   FileConfiguration getConfig() {
      return this.config;
   }

   void reloadConfig() {
      this.loadConfig();
   }

   Logger getLogger() {
      return this.host.getLogger();
   }

   File getDataFolder() {
      return this.host.getDataFolder();
   }

   Yml store(String var1) {
      Yml var2 = new Yml(new File(this.getDataFolder(), var1));
      this.stores.add(var2);
      return var2;
   }

   boolean module(String var1) {
      return this.getConfig().getBoolean("modules." + var1, true);
   }

   private InputStream resource(String var1) {
      return this.getClass().getClassLoader().getResourceAsStream(var1);
   }

   private void loadConfig() {
      File var1 = new File(this.getDataFolder(), "config.yml");

      try {
         if (!var1.exists()) {
            this.getDataFolder().mkdirs();

            try (InputStream var2 = this.resource("config.yml")) {
               if (var2 != null) {
                  Files.copy(var2, var1.toPath());
               }
            }
         }
      } catch (Exception var10) {
         this.getLogger().warning("Could not write default config: " + var10.getMessage());
      }

      this.config = YamlConfiguration.loadConfiguration(var1);

      try (InputStream var11 = this.resource("config.yml")) {
         if (var11 != null) {
            this.config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(var11, StandardCharsets.UTF_8)));
         }
      } catch (Exception var8) {
      }
   }

   public void start(JavaPlugin var1) throws Exception {
      instance = this;
      this.host = var1;
      this.loadConfig();
      this.ioPool = Executors.newSingleThreadExecutor(var1x -> {
         Thread var2x = new Thread(var1x, "WaffleCore-IO");
         var2x.setDaemon(true);
         return var2x;
      });

      try (InputStream var2 = this.resource("module.yml")) {
         if (var2 == null) {
            throw new IllegalStateException("module.yml missing from core.jar");
         }

         this.descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(var2, StandardCharsets.UTF_8));
      }

      this.registerPermissions();
      this.eco = new Eco(this);
      Services.economy(new EcoApi(this.eco));
      this.combat = new Combat(this);
      this.tp = new Teleports(this);
      this.kits = new Kits(this);
      this.auction = new Auction(this);
      this.orders = new Orders(this);
      this.mining = new Mining(this);
      this.staff = new Staff(this);
      this.mod = new Moderation(this);
      this.tools = new Tools(this);
      this.bind(this.eco, "balance", "pay", "baltop", "eco");
      this.bind(
         this.tp,
         "spawn",
         "setspawn",
         "home",
         "homes",
         "sethome",
         "delhome",
         "warp",
         "warps",
         "setwarp",
         "delwarp",
         "tpa",
         "tpahere",
         "tpaccept",
         "tpdeny",
         "tpcancel",
         "rtp",
         "back"
      );
      this.bind(this.kits, "kit", "kits", "createkit", "delkit");
      this.bind(this.auction, "ah");
      this.bind(this.orders, "orders");
      this.bind(
         this.staff,
         "gm",
         "fly",
         "heal",
         "feed",
         "vanish",
         "invsee",
         "endersee",
         "tphere",
         "msg",
         "reply",
         "broadcast",
         "day",
         "night",
         "top",
         "speed",
         "clearchat"
      );
      this.bind(
         this.mod,
         "ban",
         "tempban",
         "ipban",
         "tempipban",
         "unban",
         "unbanip",
         "mute",
         "tempmute",
         "unmute",
         "warn",
         "kick",
         "setjail",
         "deljail",
         "jail",
         "tempjail",
         "unjail",
         "freeze",
         "check",
         "chat",
         "staffchat",
         "punish",
         "alts",
         "notes",
         "report",
         "reports",
         "staff"
      );
      this.bind(this.tools, "gmc", "gms", "gmsp", "gma", "invincible", "tpall", "enchant", "repair", "struck", "sudo", "clear", "cleargrounditems", "phase");
      this.listen(this.eco, this.tp, this.kits, this.auction, this.orders, this.staff, this.combat, this.mining, this.mod, this.tools);
      Bukkit.getScheduler().runTaskTimer(var1, () -> this.stores.forEach(Yml::saveIfDirty), 600L, 600L);
      Bukkit.getScheduler().runTaskTimer(var1, this.auction::expireSweep, 1200L, 1200L);
      this.syncCommands();
   }

   public void stop() {
      for (Player var2 : Bukkit.getOnlinePlayers()) {
         if (var2.getOpenInventory().getTopInventory().getHolder() instanceof Gui) {
            var2.closeInventory();
         }
      }

      if (this.host != null) {
         Bukkit.getScheduler().cancelTasks(this.host);
      }

      for (Listener var5 : this.listeners) {
         HandlerList.unregisterAll(var5);
      }

      this.listeners.clear();
      this.unregisterCommands();

      for (String var6 : this.permissions) {
         Bukkit.getPluginManager().removePermission(var6);
      }

      this.permissions.clear();
      this.stores.forEach(var0 -> {
         if (var0.dirty) {
            var0.save();
         }
      });
      this.stores.clear();
      Services.economy(null);
      if (this.ioPool != null) {
         this.ioPool.shutdown();

         try {
            this.ioPool.awaitTermination(30L, TimeUnit.SECONDS);
         } catch (InterruptedException var7) {
            Thread.currentThread().interrupt();
         }
      }

      this.syncCommands();
      if (instance == this) {
         instance = null;
      }
   }

   private void registerPermissions() {
      ConfigurationSection var1 = this.descriptor.getConfigurationSection("permissions");
      if (var1 != null) {
         for (String var3 : var1.getKeys(false)) {
            if (Bukkit.getPluginManager().getPermission(var3) == null) {
               String var4 = var1.getString(var3 + ".default", "op");
               PermissionDefault var5 = PermissionDefault.getByName(var4);
               Permission var6 = new Permission(var3, var1.getString(var3 + ".description", ""), var5 == null ? PermissionDefault.OP : var5);
               Bukkit.getPluginManager().addPermission(var6);
               this.permissions.add(var3);
            }
         }
      }
   }

   private void listen(Listener... var1) {
      for (Listener var5 : var1) {
         Bukkit.getPluginManager().registerEvents(var5, this.host);
         this.listeners.add(var5);
      }
   }

   private void bind(Object var1, String... var2) {
      CommandMap var3 = Bukkit.getCommandMap();
      Map var4 = var3.getKnownCommands();

      for (String var8 : var2) {
         ConfigurationSection var9 = this.descriptor.getConfigurationSection("commands." + var8);
         if (var9 == null) {
            this.getLogger().warning("Command missing from module.yml: " + var8);
         } else {
            WaffleCore.Cmd var10 = new WaffleCore.Cmd(var8, (CommandExecutor)var1, var1 instanceof TabCompleter ? (TabCompleter)var1 : null);
            var10.setDescription(var9.getString("description", ""));
            List var11 = var9.getStringList("aliases");
            var10.setAliases(var11);
            ArrayList<String> var12 = new ArrayList<>();
            var12.add(var8);
            var12.addAll(var11);
            List var13 = this.getConfig().getStringList("takeover-skip");

            for (String var15 : var12) {
               String var16 = var15.toLowerCase();
               Command var17 = (Command)var4.get(var16);
               if (var17 != null && !this.owned.containsValue(var17) && var13.contains(var16)) {
                  this.getLogger().info("Leaving /" + var16 + " with " + var17.getClass().getSimpleName());
               } else {
                  if (var17 != null && !this.owned.containsValue(var17)) {
                     this.displaced.put(var16, var17);
                     this.getLogger().info("Took over /" + var16 + " from " + var17.getClass().getSimpleName());
                  }

                  var4.put(var16, var10);
                  this.owned.put(var16, var10);
               }
            }

            var4.put("wafflecore:" + var8, var10);
            this.owned.put("wafflecore:" + var8, var10);
            var10.setLabel(var8);
            var10.register(var3);
         }
      }
   }

   private void unregisterCommands() {
      Map var1 = Bukkit.getCommandMap().getKnownCommands();

      for (Entry var3 : this.owned.entrySet()) {
         if (var1.get(var3.getKey()) == var3.getValue()) {
            var1.remove(var3.getKey());
         }
      }

      this.owned.clear();

      for (Entry var5 : this.displaced.entrySet()) {
         if (!var1.containsKey(var5.getKey())) {
            var1.put((String)var5.getKey(), (Command)var5.getValue());
         }
      }

      this.displaced.clear();
   }

   private void syncCommands() {
      try {
         Server var1 = Bukkit.getServer();
         var1.getClass().getMethod("syncCommands").invoke(var1);
      } catch (Throwable var5) {
      }

      for (Player var2 : Bukkit.getOnlinePlayers()) {
         try {
            var2.updateCommands();
         } catch (Throwable var4) {
         }
      }
   }

   private static final class Cmd extends Command {
      private final CommandExecutor executor;
      private final TabCompleter completer;

      Cmd(String var1, CommandExecutor var2, TabCompleter var3) {
         super(var1);
         this.executor = var2;
         this.completer = var3;
      }

      public boolean execute(CommandSender var1, String var2, String[] var3) {
         boolean var4 = this.executor.onCommand(var1, this, var2, var3);
         if (!var4) {
            String var5 = this.getDescription();
            U.send(var1, "&cCheck your arguments: &e/" + this.getName() + (var5 == null || var5.isEmpty() ? "" : " &8- &7" + var5));
         }

         return true;
      }

      public List<String> tabComplete(CommandSender var1, String var2, String[] var3) {
         if (this.completer == null) {
            return super.tabComplete(var1, var2, var3);
         } else {
            List var4 = this.completer.onTabComplete(var1, this, var2, var3);
            return var4 == null ? super.tabComplete(var1, var2, var3) : var4;
         }
      }
   }
}
