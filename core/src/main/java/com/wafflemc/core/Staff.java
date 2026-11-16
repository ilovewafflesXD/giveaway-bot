package com.wafflemc.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

final class Staff implements CommandExecutor, TabCompleter, Listener {
   private final WaffleCore plugin;
   private final Set<UUID> vanished = new HashSet<>();
   private final Map<UUID, UUID> lastMsg = new HashMap<>();

   Staff(WaffleCore var1) {
      this.plugin = var1;
   }

   private boolean perm(CommandSender var1, String var2) {
      if (!var1.hasPermission("wafflecore.admin") && !var1.hasPermission(var2)) {
         U.send(var1, "&cYou don't have permission.");
         return false;
      } else {
         return true;
      }
   }

   private Player target(CommandSender var1, String[] var2, int var3) {
      if (var2.length > var3) {
         Player var4 = Bukkit.getPlayerExact(var2[var3]);
         if (var4 == null) {
            U.send(var1, "&cThat player isn't online.");
         }

         return var4;
      } else if (var1 instanceof Player) {
         return (Player)var1;
      } else {
         U.send(var1, "&cSpecify a player.");
         return null;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();

      for (UUID var4 : this.vanished) {
         Player var5 = Bukkit.getPlayer(var4);
         if (var5 != null && !var2.hasPermission("wafflecore.vanish.see")) {
            var2.hidePlayer(this.plugin.host, var5);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.vanished.remove(var1.getPlayer().getUniqueId());
      this.lastMsg.remove(var1.getPlayer().getUniqueId());
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      switch (var5) {
         case "gm":
            return this.gm(var1, var4);
         case "fly":
            return this.fly(var1, var4);
         case "heal":
            return this.heal(var1, var4);
         case "feed":
            return this.feed(var1, var4);
         case "vanish":
            return this.vanish(var1);
         case "invsee":
         case "endersee":
            return this.see(var1, var4, var5.equals("endersee"));
         case "tphere":
            return this.tphere(var1, var4);
         case "msg":
            return this.msg(var1, var4);
         case "reply":
            return this.reply(var1, var4);
         case "broadcast":
            return this.broadcast(var1, var4);
         case "day":
         case "night":
            return this.time(var1, var5.equals("day"));
         case "top":
            return this.top(var1);
         case "speed":
            return this.speed(var1, var4);
         case "clearchat":
            return this.clearchat(var1);
         default:
            return false;
      }
   }

   private boolean gm(CommandSender var1, String[] var2) {
      if (!this.perm(var1, "wafflecore.gamemode")) {
         return true;
      } else if (var2.length == 0) {
         U.send(var1, "&cUsage: /gm <s|c|a|sp> [player]");
         return true;
      } else {
         String var4 = var2[0].toLowerCase(Locale.ROOT);
         GameMode var3;
         switch (var4) {
            case "0":
            case "s":
            case "survival":
               var3 = GameMode.SURVIVAL;
               break;
            case "1":
            case "c":
            case "creative":
               var3 = GameMode.CREATIVE;
               break;
            case "2":
            case "a":
            case "adventure":
               var3 = GameMode.ADVENTURE;
               break;
            case "3":
            case "sp":
            case "spectator":
               var3 = GameMode.SPECTATOR;
               break;
            default:
               U.send(var1, "&cUnknown gamemode.");
               return true;
         }

         Player var6 = this.target(var1, var2, 1);
         if (var6 == null) {
            return true;
         } else {
            var6.setGameMode(var3);
            U.send(var1, "&e" + var6.getName() + "&7 is now in &e" + var3.name().toLowerCase(Locale.ROOT) + "&7.");
            return true;
         }
      }
   }

   private boolean fly(CommandSender var1, String[] var2) {
      if (!this.perm(var1, "wafflecore.fly")) {
         return true;
      } else {
         Player var3 = this.target(var1, var2, 0);
         if (var3 == null) {
            return true;
         } else {
            boolean var4 = !var3.getAllowFlight();
            var3.setAllowFlight(var4);
            if (!var4) {
               var3.setFlying(false);
            }

            U.send(var1, "Flight " + (var4 ? "&aenabled" : "&cdisabled") + "&7 for &e" + var3.getName() + "&7.");
            return true;
         }
      }
   }

   private boolean heal(CommandSender var1, String[] var2) {
      if (!this.perm(var1, "wafflecore.heal")) {
         return true;
      } else {
         Player var3 = this.target(var1, var2, 0);
         if (var3 == null) {
            return true;
         } else {
            var3.setHealth(var3.getAttribute(Attribute.MAX_HEALTH).getValue());
            var3.setFoodLevel(20);
            var3.setSaturation(20.0F);
            var3.setFireTicks(0);
            U.send(var1, "Healed &e" + var3.getName() + "&7.");
            return true;
         }
      }
   }

   private boolean feed(CommandSender var1, String[] var2) {
      if (!this.perm(var1, "wafflecore.feed")) {
         return true;
      } else {
         Player var3 = this.target(var1, var2, 0);
         if (var3 == null) {
            return true;
         } else {
            var3.setFoodLevel(20);
            var3.setSaturation(20.0F);
            U.send(var1, "Fed &e" + var3.getName() + "&7.");
            return true;
         }
      }
   }

   private boolean vanish(CommandSender var1) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.perm(var1, "wafflecore.vanish")) {
         return true;
      } else {
         Player var2 = (Player)var1;
         boolean var3 = this.vanished.add(var2.getUniqueId());
         if (!var3) {
            this.vanished.remove(var2.getUniqueId());
         }

         for (Player var5 : Bukkit.getOnlinePlayers()) {
            if (!var5.equals(var2)) {
               if (var3 && !var5.hasPermission("wafflecore.vanish.see")) {
                  var5.hidePlayer(this.plugin.host, var2);
               } else {
                  var5.showPlayer(this.plugin.host, var2);
               }
            }
         }

         U.send(var2, "Vanish " + (var3 ? "&aenabled" : "&cdisabled") + "&7.");
         return true;
      }
   }

   private boolean see(CommandSender var1, String[] var2, boolean var3) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.perm(var1, "wafflecore.invsee")) {
         return true;
      } else if (var2.length == 0) {
         U.send(var1, "&cUsage: /" + (var3 ? "endersee" : "invsee") + " <player>");
         return true;
      } else {
         Player var4 = Bukkit.getPlayerExact(var2[0]);
         if (var4 == null) {
            U.send(var1, "&cThat player isn't online.");
            return true;
         } else {
            ((Player)var1).openInventory((Inventory)(var3 ? var4.getEnderChest() : var4.getInventory()));
            return true;
         }
      }
   }

   private boolean tphere(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.perm(var1, "wafflecore.tp")) {
         return true;
      } else if (var2.length == 0) {
         U.send(var1, "&cUsage: /tphere <player>");
         return true;
      } else {
         Player var3 = Bukkit.getPlayerExact(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cThat player isn't online.");
            return true;
         } else {
            var3.teleportAsync(((Player)var1).getLocation());
            U.send(var1, "Teleported &e" + var3.getName() + "&7 to you.");
            return true;
         }
      }
   }

   private boolean msg(CommandSender var1, String[] var2) {
      if (var2.length < 2) {
         U.send(var1, "&cUsage: /msg <player> <message>");
         return true;
      } else {
         Player var3 = Bukkit.getPlayerExact(var2[0]);
         if (var3 != null && (!this.vanished.contains(var3.getUniqueId()) || var1.hasPermission("wafflecore.vanish.see"))) {
            this.deliver(var1, var3, String.join(" ", Arrays.copyOfRange(var2, 1, var2.length)));
            return true;
         } else {
            U.send(var1, "&cThat player isn't online.");
            return true;
         }
      }
   }

   private boolean reply(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else {
         UUID var3 = this.lastMsg.get(((Player)var1).getUniqueId());
         Player var4 = var3 == null ? null : Bukkit.getPlayer(var3);
         if (var4 == null) {
            U.send(var1, "&cYou have nobody to reply to.");
            return true;
         } else if (var2.length == 0) {
            U.send(var1, "&cUsage: /r <message>");
            return true;
         } else {
            this.deliver(var1, var4, String.join(" ", var2));
            return true;
         }
      }
   }

   private void deliver(CommandSender var1, Player var2, String var3) {
      String var4 = var1 instanceof Player ? var1.getName() : "Console";
      U.raw(var1, "&8[&6You &7→ &e" + var2.getName() + "&8] &f" + var3);
      U.raw(var2, "&8[&e" + var4 + " &7→ &6You&8] &f" + var3);
      if (var1 instanceof Player) {
         this.lastMsg.put(var2.getUniqueId(), ((Player)var1).getUniqueId());
         this.lastMsg.put(((Player)var1).getUniqueId(), var2.getUniqueId());
      }
   }

   private boolean broadcast(CommandSender var1, String[] var2) {
      if (!this.perm(var1, "wafflecore.broadcast")) {
         return true;
      } else if (var2.length == 0) {
         U.send(var1, "&cUsage: /broadcast <message>");
         return true;
      } else {
         Bukkit.broadcastMessage(U.color("&6&lWaffle&e&lMC &8» &f" + String.join(" ", var2)));
         return true;
      }
   }

   private boolean time(CommandSender var1, boolean var2) {
      if (!this.perm(var1, "wafflecore.time")) {
         return true;
      } else {
         World var3 = var1 instanceof Player ? ((Player)var1).getWorld() : (World)Bukkit.getWorlds().get(0);
         var3.setTime(var2 ? 1000L : 13000L);
         U.send(var1, "Time set to &e" + (var2 ? "day" : "night") + "&7.");
         return true;
      }
   }

   private boolean top(CommandSender var1) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.perm(var1, "wafflecore.top")) {
         return true;
      } else {
         Player var2 = (Player)var1;
         Location var3 = var2.getLocation();
         int var4 = var3.getWorld().getHighestBlockYAt(var3);
         var2.teleportAsync(new Location(var3.getWorld(), var3.getX(), (double)(var4 + 1), var3.getZ(), var3.getYaw(), var3.getPitch()));
         U.send(var2, "Teleported to the top.");
         return true;
      }
   }

   private boolean speed(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.perm(var1, "wafflecore.speed")) {
         return true;
      } else {
         Player var3 = (Player)var1;
         if (var2.length == 0) {
            U.send(var3, "&cUsage: /speed <1-10>");
            return true;
         } else {
            try {
               float var4 = Float.parseFloat(var2[0]);
               if (var4 < 1.0F || var4 > 10.0F) {
                  throw new NumberFormatException();
               }

               float var5 = var4 / 10.0F;
               if (var3.isFlying()) {
                  var3.setFlySpeed(var5);
               } else {
                  var3.setWalkSpeed(Math.min(1.0F, var5 * 0.5F + 0.1F));
               }

               U.send(var3, "Speed set to &e" + var2[0] + "&7.");
            } catch (NumberFormatException var6) {
               U.send(var3, "&cEnter a number from 1 to 10.");
            }

            return true;
         }
      }
   }

   private boolean clearchat(CommandSender var1) {
      if (!this.perm(var1, "wafflecore.clearchat")) {
         return true;
      } else {
         for (Player var3 : Bukkit.getOnlinePlayers()) {
            if (!var3.hasPermission("wafflecore.admin")) {
               for (int var4 = 0; var4 < 100; var4++) {
                  var3.sendMessage("");
               }
            }
         }

         Bukkit.broadcastMessage(U.color("&6&lWaffle&e&lMC &8» &7Chat was cleared."));
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (var5.equals("gm")) {
         if (var4.length == 1) {
            return U.filter(List.of("survival", "creative", "adventure", "spectator"), var4[0]);
         } else {
            return var4.length == 2 ? Eco.onlineNames(var4[1]) : List.of();
         }
      } else {
         boolean var6 = List.of("fly", "heal", "feed", "invsee", "endersee", "tphere", "msg").contains(var5);
         return var6 && var4.length == 1 ? Eco.onlineNames(var4[0]) : List.of();
      }
   }
}
