package com.wafflemc.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class Teleports implements CommandExecutor, TabCompleter, Listener {
   private final WaffleCore plugin;
   private final Yml spawn;
   private final Yml homes;
   private final Yml warps;
   private final Map<UUID, BukkitTask> pending = new HashMap<>();
   private final Map<UUID, Location> back = new HashMap<>();
   private final Map<UUID, Teleports.Req> requests = new HashMap<>();
   private final Map<UUID, Long> rtpCooldown = new HashMap<>();

   Teleports(WaffleCore var1) {
      this.plugin = var1;
      this.spawn = var1.store("spawn.yml");
      this.homes = var1.store("homes.yml");
      this.warps = var1.store("warps.yml");
      if (!this.spawn.cfg.contains("spawn")) {
         this.importSpawn();
      }

      if (!this.homes.file.exists()) {
         this.importHomes();
      }
   }

   private void importSpawn() {
      File var1 = new File(this.plugin.getDataFolder().getParentFile(), "Spawn/location.yml");
      if (var1.exists()) {
         YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
         ConfigurationSection var3 = var2.getConfigurationSection("spawn");
         if (var3 != null) {
            ConfigurationSection var4 = this.spawn.cfg.createSection("spawn");
            var4.set("world", var3.getString("world", "world"));
            var4.set("x", var3.getDouble("x"));
            var4.set("y", var3.getDouble("y"));
            var4.set("z", var3.getDouble("z"));
            var4.set("yaw", var3.getDouble("yaw"));
            var4.set("pitch", var3.getDouble("pitch"));
            this.spawn.mark();
            this.spawn.save();
         }
      }
   }

   private void importHomes() {
      File var1 = new File(this.plugin.getDataFolder().getParentFile(), "Essentials/userdata");
      File[] var2 = var1.listFiles((var0, var1x) -> var1x.endsWith(".yml"));
      if (var2 != null) {
         for (File var6 : var2) {
            String var7 = var6.getName().substring(0, var6.getName().length() - 4);
            YamlConfiguration var8 = YamlConfiguration.loadConfiguration(var6);
            ConfigurationSection var9 = var8.getConfigurationSection("homes");
            if (var9 != null) {
               for (String var11 : var9.getKeys(false)) {
                  ConfigurationSection var12 = var9.getConfigurationSection(var11);
                  if (var12 != null) {
                     String var13 = var12.getString("world-name", var12.getString("world", "world"));
                     ConfigurationSection var14 = this.homes.cfg.createSection(var7 + "." + var11.toLowerCase(Locale.ROOT));
                     var14.set("world", var13);
                     var14.set("x", var12.getDouble("x"));
                     var14.set("y", var12.getDouble("y"));
                     var14.set("z", var12.getDouble("z"));
                     var14.set("yaw", var12.getDouble("yaw"));
                     var14.set("pitch", var12.getDouble("pitch"));
                  }
               }
            }
         }

         this.homes.mark();
         this.homes.save();
      }
   }

   Yml homesStore() {
      return this.homes;
   }

   Location spawnLocation() {
      Location var1 = Yml.readLoc(this.spawn.cfg.getConfigurationSection("spawn"));
      if (var1 != null) {
         return var1;
      } else {
         World var2 = (World)Bukkit.getWorlds().get(0);
         return var2.getSpawnLocation();
      }
   }

   void go(Player var1, final Location var2, final String var3) {
      if (var2 == null || var2.getWorld() == null) {
         U.send(var1, "&cThat destination isn't available right now.");
      } else if (this.plugin.combat.tagged(var1)) {
         U.send(var1, "&cYou can't teleport while in combat.");
      } else {
         final int var4 = var1.hasPermission("wafflecore.bypass.warmup") ? 0 : this.plugin.getConfig().getInt("teleport-warmup", 3);
         this.cancelPending(var1.getUniqueId());
         if (var4 <= 0) {
            this.jump(var1, var2, var3);
         } else {
            final UUID var5 = var1.getUniqueId();
            final Location var6 = var1.getLocation();
            U.send(var1, "Teleporting to &e" + var3 + "&7 in &e" + var4 + "s&7. Don't move!");
            BukkitTask var7 = (new BukkitRunnable() {
               int left = var4;

               public void run() {
                  Player var1 = Bukkit.getPlayer(var5);
                  if (var1 != null && var1.isOnline()) {
                     Location var2x = var1.getLocation();
                     if (var2x.getWorld() != var6.getWorld() || var2x.distanceSquared(var6) > 0.6) {
                        Teleports.this.cancelPending(var5);
                        U.send(var1, "&cTeleport cancelled because you moved.");
                     } else if (Teleports.this.plugin.combat.tagged(var1)) {
                        Teleports.this.cancelPending(var5);
                        U.send(var1, "&cTeleport cancelled because you entered combat.");
                     } else {
                        this.left--;
                        if (this.left <= 0) {
                           Teleports.this.cancelPending(var5);
                           Teleports.this.jump(var1, var2, var3);
                        }
                     }
                  } else {
                     Teleports.this.cancelPending(var5);
                  }
               }
            }).runTaskTimer(this.plugin.host, 20L, 20L);
            this.pending.put(var5, var7);
         }
      }
   }

   private void cancelPending(UUID var1) {
      BukkitTask var2 = this.pending.remove(var1);
      if (var2 != null) {
         var2.cancel();
      }
   }

   private void jump(Player var1, Location var2, String var3) {
      this.back.put(var1.getUniqueId(), var1.getLocation());
      var1.teleportAsync(var2).thenAccept(var2x -> {
         if (var2x) {
            U.send(var1, "Teleported to &e" + var3 + "&7.");
         }
      });
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      if (!var2.hasPlayedBefore() && this.plugin.getConfig().getBoolean("spawn.first-join", true) && this.spawn.cfg.contains("spawn")) {
         Bukkit.getScheduler().runTaskLater(this.plugin.host, () -> {
            if (var2.isOnline()) {
               var2.teleportAsync(this.spawnLocation());
            }
         }, 2L);
      }

      String var3 = this.plugin.getConfig().getString("join-message", "");
      if (!var3.isEmpty()) {
         var1.setJoinMessage(U.color(var3.replace("{player}", var2.getName())));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.cancelPending(var2);
      this.requests.remove(var2);
      String var3 = this.plugin.getConfig().getString("quit-message", "");
      if (!var3.isEmpty()) {
         var1.setQuitMessage(U.color(var3.replace("{player}", var1.getPlayer().getName())));
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent var1) {
      Player var2 = var1.getEntity();
      if (var2.hasPermission("wafflecore.back.death")) {
         this.back.put(var2.getUniqueId(), var2.getLocation());
      }
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent var1) {
      if (this.plugin.getConfig().getBoolean("spawn.respawn", true)) {
         if (!var1.isBedSpawn() && !var1.isAnchorSpawn()) {
            if (this.spawn.cfg.contains("spawn")) {
               Location var2 = this.spawnLocation();
               if (var2.getWorld() != null) {
                  var1.setRespawnLocation(var2);
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!(var1 instanceof Player var5)) {
         U.send(var1, "&cThis command is for players only.");
         return true;
      } else {
         String var6 = var2.getName().toLowerCase(Locale.ROOT);
         switch (var6) {
            case "spawn":
               this.go(var5, this.spawnLocation(), "spawn");
               return true;
            case "setspawn":
               if (!var5.hasPermission("wafflecore.admin")) {
                  return this.deny(var5);
               }

               Yml.writeLoc(this.spawn.cfg.createSection("spawn"), var5.getLocation());
               this.spawn.mark();
               U.send(var5, "Spawn set to your location.");
               return true;
            case "sethome":
               return this.setHome(var5, var4);
            case "home":
               return this.home(var5, var4);
            case "homes":
               return this.listHomes(var5);
            case "delhome":
               return this.delHome(var5, var4);
            case "warp":
               return this.warp(var5, var4);
            case "warps":
               return this.listWarps(var5);
            case "setwarp":
               return this.setWarp(var5, var4);
            case "delwarp":
               return this.delWarp(var5, var4);
            case "tpa":
               return this.request(var5, var4, false);
            case "tpahere":
               return this.request(var5, var4, true);
            case "tpaccept":
               return this.accept(var5, var4);
            case "tpdeny":
               return this.deny(var5, var4);
            case "tpcancel":
               return this.cancelReq(var5);
            case "rtp":
               return this.rtp(var5);
            case "back":
               return this.back(var5);
            default:
               return false;
         }
      }
   }

   private boolean deny(Player var1) {
      U.send(var1, "&cYou don't have permission.");
      return true;
   }

   private ConfigurationSection homeSection(Player var1) {
      return this.homes.cfg.getConfigurationSection(var1.getUniqueId().toString());
   }

   private List<String> homeNames(Player var1) {
      ConfigurationSection var2 = this.homeSection(var1);
      return var2 == null ? new ArrayList<>() : new ArrayList<>(var2.getKeys(false));
   }

   private boolean setHome(Player var1, String[] var2) {
      String var3 = (var2.length > 0 ? var2[0] : "home").toLowerCase(Locale.ROOT);
      if (!var3.matches("[a-z0-9_-]{1,24}")) {
         U.send(var1, "&cHome names can use letters, numbers, _ and - (max 24).");
         return true;
      } else {
         List var4 = this.homeNames(var1);
         int var5 = this.plugin.getConfig().getInt("home-limit", 5);
         if (!var4.contains(var3) && var4.size() >= var5 && !var1.hasPermission("wafflecore.homes.unlimited")) {
            U.send(var1, "&cYou've reached your limit of &e" + var5 + "&c homes. Use &e/delhome&c first.");
            return true;
         } else {
            Yml.writeLoc(this.homes.cfg.createSection(var1.getUniqueId() + "." + var3), var1.getLocation());
            this.homes.mark();
            U.send(var1, "Home &e" + var3 + "&7 set.");
            return true;
         }
      }
   }

   private boolean home(Player var1, String[] var2) {
      List var3 = this.homeNames(var1);
      if (var3.isEmpty()) {
         U.send(var1, "&cYou have no homes. Use &e/sethome&c.");
         return true;
      } else {
         String var4;
         if (var2.length > 0) {
            var4 = var2[0].toLowerCase(Locale.ROOT);
         } else if (var3.contains("home")) {
            var4 = "home";
         } else {
            if (var3.size() != 1) {
               this.openHomes(var1);
               return true;
            }

            var4 = (String)var3.get(0);
         }

         Location var5 = Yml.readLoc(this.homeSection(var1).getConfigurationSection(var4));
         if (!var3.contains(var4)) {
            U.send(var1, "&cNo home named &e" + var4 + "&c. Your homes: &e" + String.join("&7, &e", var3));
            return true;
         } else {
            this.go(var1, var5, "home &e" + var4);
            return true;
         }
      }
   }

   private boolean listHomes(Player var1) {
      if (this.homeNames(var1).isEmpty()) {
         U.send(var1, "You have no homes. Use &e/sethome&7 to make one.");
         return true;
      } else {
         this.openHomes(var1);
         return true;
      }
   }

   private void openHomes(Player var1) {
      List<String> var2 = this.homeNames(var1);
      Collections.sort(var2);
      int var3 = Gui.rowsFor(var2.size());
      String var4 = var1.hasPermission("wafflecore.homes.unlimited") ? "unlimited" : String.valueOf(this.plugin.getConfig().getInt("home-limit", 5));
      Gui var5 = new Gui("homes");
      Gui.menu(var5.make(var3 * 9, "&6&lWaffle&e&lMC &8» &7Homes &8(" + var2.size() + "/" + var4 + ")"), var3);
      Inventory var6 = var5.getInventory();
      List var7 = Gui.inner(var3);
      int var8 = 0;

      for (String var10 : var2) {
         if (var8 >= var7.size()) {
            break;
         }

         Location var11 = Yml.readLoc(this.homeSection(var1).getConfigurationSection(var10));
         Material var12 = Material.RED_BED;
         ArrayList<String> var13 = new ArrayList<>();
         if (var11 == null) {
            var12 = Material.BARRIER;
            var13.add("&cWorld not loaded");
         } else {
            Environment var14 = var11.getWorld().getEnvironment();
            if (var14 == Environment.NETHER) {
               var12 = Material.CRIMSON_NYLIUM;
            } else if (var14 == Environment.THE_END) {
               var12 = Material.END_STONE;
            }

            var13.add("&7World: &f" + var11.getWorld().getName());
            var13.add("&7Location: &f" + var11.getBlockX() + ", " + var11.getBlockY() + ", " + var11.getBlockZ());
            var13.add("");
            var13.add("&eLeft-click &7to teleport");
         }

         var13.add("&cShift + right-click &7to delete");
         int var15 = (Integer)var7.get(var8++);
         var6.setItem(var15, Gui.item(var12, "&6&l" + var10, var13.toArray(new String[0])));
         var5.at.put(var15, var10);
      }

      var6.setItem(4, Gui.item(Material.OAK_SIGN, "&6&lYour homes", "&7Using &f" + var2.size() + "&7 of &f" + var4, "", "&7Set one with &e/sethome <name>"));
      var6.setItem(var3 * 9 - 5, Gui.item(Material.BARRIER, "&cClose"));
      var1.openInventory(var6);
   }

   @EventHandler
   public void onHomesClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("homes")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  int var4 = var1.getSlot();
                  if (var4 == var1.getInventory().getSize() - 5) {
                     var3.closeInventory();
                  } else {
                     String var5 = var2.at.get(var4);
                     if (var5 != null && this.homeNames(var3).contains(var5)) {
                        if (var1.isShiftClick() && var1.isRightClick()) {
                           this.homes.cfg.set(var3.getUniqueId() + "." + var5, null);
                           this.homes.mark();
                           U.send(var3, "Home &e" + var5 + "&7 deleted.");
                           if (this.homeNames(var3).isEmpty()) {
                              var3.closeInventory();
                           } else {
                              this.openHomes(var3);
                           }
                        } else {
                           if (var1.isLeftClick() && !var1.isShiftClick()) {
                              Location var6 = Yml.readLoc(this.homeSection(var3).getConfigurationSection(var5));
                              var3.closeInventory();
                              this.go(var3, var6, "home &e" + var5);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean delHome(Player var1, String[] var2) {
      if (var2.length == 0) {
         U.send(var1, "&cUsage: /delhome <name>");
         return true;
      } else {
         String var3 = var2[0].toLowerCase(Locale.ROOT);
         if (!this.homeNames(var1).contains(var3)) {
            U.send(var1, "&cNo home named &e" + var3 + "&c.");
            return true;
         } else {
            this.homes.cfg.set(var1.getUniqueId() + "." + var3, null);
            this.homes.mark();
            U.send(var1, "Home &e" + var3 + "&7 deleted.");
            return true;
         }
      }
   }

   private List<String> warpNames() {
      ConfigurationSection var1 = this.warps.cfg.getConfigurationSection("warps");
      return var1 == null ? new ArrayList<>() : new ArrayList<>(var1.getKeys(false));
   }

   private boolean warp(Player var1, String[] var2) {
      if (var2.length == 0) {
         return this.listWarps(var1);
      } else {
         String var3 = var2[0].toLowerCase(Locale.ROOT);
         if (!this.warpNames().contains(var3)) {
            U.send(var1, "&cNo warp named &e" + var3 + "&c.");
            return true;
         } else {
            this.go(var1, Yml.readLoc(this.warps.cfg.getConfigurationSection("warps." + var3)), "warp &e" + var3);
            return true;
         }
      }
   }

   private boolean listWarps(Player var1) {
      List var2 = this.warpNames();
      U.send(var1, var2.isEmpty() ? "There are no warps yet." : "Warps: &e" + String.join("&7, &e", var2));
      return true;
   }

   private boolean setWarp(Player var1, String[] var2) {
      if (!var1.hasPermission("wafflecore.admin")) {
         return this.deny(var1);
      } else if (var2.length != 0 && var2[0].matches("[A-Za-z0-9_-]{1,24}")) {
         Yml.writeLoc(this.warps.cfg.createSection("warps." + var2[0].toLowerCase(Locale.ROOT)), var1.getLocation());
         this.warps.mark();
         U.send(var1, "Warp &e" + var2[0].toLowerCase(Locale.ROOT) + "&7 set.");
         return true;
      } else {
         U.send(var1, "&cUsage: /setwarp <name>");
         return true;
      }
   }

   private boolean delWarp(Player var1, String[] var2) {
      if (!var1.hasPermission("wafflecore.admin")) {
         return this.deny(var1);
      } else if (var2.length != 0 && this.warpNames().contains(var2[0].toLowerCase(Locale.ROOT))) {
         this.warps.cfg.set("warps." + var2[0].toLowerCase(Locale.ROOT), null);
         this.warps.mark();
         U.send(var1, "Warp deleted.");
         return true;
      } else {
         U.send(var1, "&cUsage: /delwarp <name>");
         return true;
      }
   }

   private boolean request(Player var1, String[] var2, boolean var3) {
      if (var2.length == 0) {
         U.send(var1, "&cUsage: /" + (var3 ? "tpahere" : "tpa") + " <player>");
         return true;
      } else {
         Player var4 = Bukkit.getPlayerExact(var2[0]);
         if (var4 == null) {
            U.send(var1, "&cThat player isn't online.");
            return true;
         } else if (var4.equals(var1)) {
            U.send(var1, "&cYou can't send a request to yourself.");
            return true;
         } else {
            long var5 = this.plugin.getConfig().getLong("tpa-timeout", 60L);
            this.requests.put(var4.getUniqueId(), new Teleports.Req(var1.getUniqueId(), var3, System.currentTimeMillis() + var5 * 1000L));
            U.send(var1, "Request sent to &e" + var4.getName() + "&7. It expires in &e" + var5 + "s&7.");
            U.send(
               var4, "&e" + var1.getName() + "&7 wants " + (var3 ? "you to teleport to them" : "to teleport to you") + ". Type &a/tpaccept&7 or &c/tpdeny&7."
            );
            return true;
         }
      }
   }

   private Teleports.Req take(Player var1) {
      Teleports.Req var2 = this.requests.remove(var1.getUniqueId());
      return var2 != null && var2.expires >= System.currentTimeMillis() ? var2 : null;
   }

   private boolean accept(Player var1, String[] var2) {
      Teleports.Req var3 = this.take(var1);
      if (var3 == null) {
         U.send(var1, "&cYou have no pending teleport requests.");
         return true;
      } else {
         Player var4 = Bukkit.getPlayer(var3.from);
         if (var4 == null) {
            U.send(var1, "&cThat player went offline.");
            return true;
         } else {
            U.send(var1, "Request accepted.");
            U.send(var4, "&e" + var1.getName() + "&7 accepted your request.");
            if (var3.here) {
               this.go(var1, var4.getLocation(), var4.getName());
            } else {
               this.go(var4, var1.getLocation(), var1.getName());
            }

            return true;
         }
      }
   }

   private boolean deny(Player var1, String[] var2) {
      Teleports.Req var3 = this.take(var1);
      if (var3 == null) {
         U.send(var1, "&cYou have no pending teleport requests.");
         return true;
      } else {
         Player var4 = Bukkit.getPlayer(var3.from);
         U.send(var1, "Request denied.");
         if (var4 != null) {
            U.send(var4, "&e" + var1.getName() + "&7 denied your request.");
         }

         return true;
      }
   }

   private boolean cancelReq(Player var1) {
      boolean var2 = false;

      for (Entry var4 : new ArrayList<>(this.requests.entrySet())) {
         if (((Teleports.Req)var4.getValue()).from.equals(var1.getUniqueId())) {
            this.requests.remove(var4.getKey());
            var2 = true;
         }
      }

      U.send(var1, var2 ? "Your teleport request was cancelled." : "&cYou have no outgoing requests.");
      return true;
   }

   private boolean back(Player var1) {
      if (!var1.hasPermission("wafflecore.back")) {
         return this.deny(var1);
      } else {
         Location var2 = this.back.get(var1.getUniqueId());
         if (var2 == null) {
            U.send(var1, "&cThere's nowhere to go back to.");
            return true;
         } else {
            this.go(var1, var2, "your previous location");
            return true;
         }
      }
   }

   private List<Teleports.RtpDest> rtpDests() {
      ArrayList var1 = new ArrayList();
      ConfigurationSection var2 = this.plugin.getConfig().getConfigurationSection("rtp.menu");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            ConfigurationSection var5 = var2.getConfigurationSection(var4);
            if (var5 != null) {
               Material var6 = Material.matchMaterial(var5.getString("icon", "GRASS_BLOCK"));
               var1.add(
                  new Teleports.RtpDest(
                     var5.getString("world", var4),
                     var5.getString("name", var4),
                     var6 == null ? Material.GRASS_BLOCK : var6,
                     var5.getInt("slot", 11 + var1.size() * 2),
                     var5.getInt("min-radius", 0),
                     var5.getInt("max-radius", 4000)
                  )
               );
            }
         }
      }

      if (var1.isEmpty()) {
         var1.add(new Teleports.RtpDest("world", "&aOverworld", Material.GRASS_BLOCK, 11, 20000, 100000));
         var1.add(new Teleports.RtpDest("world_nether", "&cNether", Material.NETHERRACK, 13, 25000, 50000));
         var1.add(new Teleports.RtpDest("world_the_end", "&5The End", Material.END_STONE, 15, 2500, 50000));
      }

      return var1;
   }

   private boolean rtp(Player var1) {
      Gui var2 = new Gui("rtp");
      Inventory var3 = var2.make(27, "&6&lWaffle&e&lMC &8» &7Random Teleport");
      Gui.menu(var3, 3);
      long var4 = this.plugin.getConfig().getLong("rtp.cooldown", 30L);

      for (Teleports.RtpDest var7 : this.rtpDests()) {
         if (var7.slot >= 0 && var7.slot < 27) {
            World var8 = Bukkit.getWorld(var7.world);
            ItemStack var9 = new ItemStack(var7.icon);
            ItemMeta var10 = var9.getItemMeta();
            var10.setDisplayName(U.color(var7.name));
            ArrayList<String> var11 = new ArrayList<>();
            if (var8 == null) {
               var11.add(U.color("&r&cUnavailable"));
            } else {
               var11.add(U.color("&r&fClick to randomly teleport"));
               var11.add("");
               var11.add(U.color("&r&7Players: &b" + var8.getPlayers().size()));
               var11.add(U.color("&r&7Range: &b" + var7.min + "-" + var7.max));
               var11.add(U.color("&r&7Cooldown: &b" + var4 + "s"));
            }

            var10.setLore(var11);
            var9.setItemMeta(var10);
            var3.setItem(var7.slot, var9);
         }
      }

      var1.openInventory(var3);
      return true;
   }

   @EventHandler
   public void onRtpClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("rtp")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  for (Teleports.RtpDest var5 : this.rtpDests()) {
                     if (var5.slot == var1.getSlot()) {
                        var3.closeInventory();
                        this.startRtp(var3, var5);
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private void startRtp(Player var1, Teleports.RtpDest var2) {
      World var3 = Bukkit.getWorld(var2.world);
      if (var3 == null) {
         U.send(var1, "&cThat world isn't available.");
      } else if (this.plugin.combat.tagged(var1)) {
         U.send(var1, "&cYou can't teleport while in combat.");
      } else {
         long var4 = this.plugin.getConfig().getLong("rtp.cooldown", 30L);
         Long var6 = this.rtpCooldown.get(var1.getUniqueId());
         long var7 = System.currentTimeMillis();
         if (var6 != null && !var1.hasPermission("wafflecore.bypass.cooldown") && var7 - var6 < var4 * 1000L) {
            U.send(var1, "&cWait &e" + U.duration((var4 * 1000L - (var7 - var6)) / 1000L + 1L) + "&c before using &e/rtp&c again.");
         } else {
            this.rtpCooldown.put(var1.getUniqueId(), var7);
            U.send(var1, "Searching for a safe spot...");
            this.attempt(var1, var3, var2, 0);
         }
      }
   }

   private void attempt(Player var1, World var2, Teleports.RtpDest var3, int var4) {
      if (var1.isOnline()) {
         if (var4 >= 25) {
            this.rtpCooldown.remove(var1.getUniqueId());
            U.send(var1, "&cCouldn't find a safe spot. Try again.");
         } else {
            ThreadLocalRandom var5 = ThreadLocalRandom.current();
            double var6 = var5.nextDouble() * Math.PI * 2.0;
            double var8 = (double)var3.min + var5.nextDouble() * (double)Math.max(1, var3.max - var3.min);
            int var10 = (int)Math.round(Math.cos(var6) * var8);
            int var11 = (int)Math.round(Math.sin(var6) * var8);
            var2.getChunkAtAsync(var10 >> 4, var11 >> 4).thenAccept(var7 -> Bukkit.getScheduler().runTask(this.plugin.host, () -> {
                  if (var1.isOnline()) {
                     Location var7x = var2.getEnvironment() == Environment.NETHER ? this.netherSpot(var2, var10, var11) : this.surfaceSpot(var2, var10, var11);
                     if (var7x != null && var2.getWorldBorder().isInside(var7x)) {
                        var7x.setYaw(var1.getLocation().getYaw());
                        this.go(var1, var7x, "a random location");
                     } else {
                        this.attempt(var1, var2, var3, var4 + 1);
                     }
                  }
               }));
         }
      }
   }

   private Location surfaceSpot(World var1, int var2, int var3) {
      int var4 = var1.getHighestBlockYAt(var2, var3);
      if (var4 <= var1.getMinHeight()) {
         return null;
      } else {
         return !this.safe(var1.getBlockAt(var2, var4, var3).getType()) ? null : new Location(var1, (double)var2 + 0.5, (double)(var4 + 1), (double)var3 + 0.5);
      }
   }

   private Location netherSpot(World var1, int var2, int var3) {
      for (int var4 = 120; var4 > 32; var4--) {
         if (this.safe(var1.getBlockAt(var2, var4, var3).getType())
            && var1.getBlockAt(var2, var4 + 1, var3).getType().isAir()
            && var1.getBlockAt(var2, var4 + 2, var3).getType().isAir()) {
            return new Location(var1, (double)var2 + 0.5, (double)(var4 + 1), (double)var3 + 0.5);
         }
      }

      return null;
   }

   private boolean safe(Material var1) {
      if (!var1.isSolid()) {
         return false;
      } else {
         String var2 = var1.name();
         return !var2.contains("LEAVES")
            && !var2.contains("MAGMA")
            && !var2.contains("CACTUS")
            && !var2.contains("FIRE")
            && !var2.contains("POWDER_SNOW")
            && !var2.contains("LAVA")
            && !var2.contains("WATER")
            && !var2.contains("ICE");
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length == 1 && var1 instanceof Player var5) {
         String var6 = var2.getName().toLowerCase(Locale.ROOT);
         switch (var6) {
            case "home":
            case "delhome":
               return U.filter(this.homeNames(var5), var4[0]);
            case "warp":
            case "delwarp":
               return U.filter(this.warpNames(), var4[0]);
            case "tpa":
            case "tpahere":
               return Eco.onlineNames(var4[0]);
            default:
               return List.of();
         }
      } else {
         return List.of();
      }
   }

   private static final class Req {
      final UUID from;
      final boolean here;
      final long expires;

      Req(UUID var1, boolean var2, long var3) {
         this.from = var1;
         this.here = var2;
         this.expires = var3;
      }
   }

   private static final class RtpDest {
      final String world;
      final String name;
      final Material icon;
      final int slot;
      final int min;
      final int max;

      RtpDest(String var1, String var2, Material var3, int var4, int var5, int var6) {
         this.world = var1;
         this.name = var2;
         this.icon = var3;
         this.slot = var4;
         this.min = var5;
         this.max = var6;
      }
   }
}
