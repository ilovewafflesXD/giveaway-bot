package com.wafflemc.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

final class Tools implements CommandExecutor, TabCompleter, Listener {
   private final WaffleCore plugin;
   private final Set<UUID> invincible = ConcurrentHashMap.newKeySet();

   Tools(WaffleCore var1) {
      this.plugin = var1;
   }

   private boolean can(CommandSender var1, String var2) {
      if (U.can(var1, "tools", var2)) {
         return true;
      } else {
         U.send(var1, "&cYou don't have permission.");
         return false;
      }
   }

   private List<Player> targets(CommandSender var1, String[] var2, int var3) {
      ArrayList var4 = new ArrayList();
      if (var2.length > var3) {
         if (var2[var3].equals("*")) {
            var4.addAll(Bukkit.getOnlinePlayers());
            return var4;
         } else {
            Player var5 = Bukkit.getPlayerExact(var2[var3]);
            if (var5 == null) {
               U.send(var1, "&cThat player isn't online.");
            } else {
               var4.add(var5);
            }

            return var4;
         }
      } else {
         if (var1 instanceof Player) {
            var4.add((Player)var1);
         } else {
            U.send(var1, "&cSpecify a player.");
         }

         return var4;
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.invincible.remove(var1.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onDamage(EntityDamageEvent var1) {
      if (var1.getEntity() instanceof Player && this.invincible.contains(var1.getEntity().getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onFood(FoodLevelChangeEvent var1) {
      if (this.invincible.contains(var1.getEntity().getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      switch (var5) {
         case "gmc":
            return this.mode(var1, var4, GameMode.CREATIVE, "gamemode");
         case "gms":
            return this.mode(var1, var4, GameMode.SURVIVAL, "gamemode");
         case "gmsp":
            return this.mode(var1, var4, GameMode.SPECTATOR, "gamemode");
         case "gma":
            return this.mode(var1, var4, GameMode.ADVENTURE, "gamemode");
         case "invincible":
            return this.god(var1, var4);
         case "tpall":
            return this.tpall(var1, var4);
         case "enchant":
            return this.enchant(var1, var4);
         case "repair":
            return this.repair(var1);
         case "struck":
            return this.struck(var1, var4);
         case "sudo":
            return this.sudo(var1, var4);
         case "clear":
            return this.clear(var1, var4);
         case "cleargrounditems":
            return this.clearItems(var1);
         case "phase":
            return this.phase(var1);
         default:
            return false;
      }
   }

   private boolean mode(CommandSender var1, String[] var2, GameMode var3, String var4) {
      if (!this.can(var1, var4)) {
         return true;
      } else {
         for (Player var6 : this.targets(var1, var2, 0)) {
            var6.setGameMode(var3);
            U.send(var6, "Game mode set to &e" + var3.name().toLowerCase(Locale.ROOT) + "&7.");
            if (var6 != var1) {
               U.send(var1, "Set &e" + var6.getName() + "&7 to &e" + var3.name().toLowerCase(Locale.ROOT) + "&7.");
            }
         }

         return true;
      }
   }

   private boolean god(CommandSender var1, String[] var2) {
      if (!this.can(var1, "invincible")) {
         return true;
      } else {
         for (Player var4 : this.targets(var1, var2, 0)) {
            boolean var5 = this.invincible.add(var4.getUniqueId());
            if (!var5) {
               this.invincible.remove(var4.getUniqueId());
            } else {
               var4.setHealth(var4.getAttribute(Attribute.MAX_HEALTH).getValue());
               var4.setFoodLevel(20);
            }

            U.send(var4, "Invincibility " + (var5 ? "&aenabled" : "&cdisabled") + "&7.");
            if (var4 != var1) {
               U.send(var1, "Invincibility " + (var5 ? "&aenabled" : "&cdisabled") + "&7 for &e" + var4.getName() + "&7.");
            }
         }

         return true;
      }
   }

   private boolean tpall(CommandSender var1, String[] var2) {
      if (!this.can(var1, "teleport")) {
         return true;
      } else {
         Location var3;
         if (var2.length >= 3 && var1 instanceof Player) {
            try {
               Location var8 = ((Player)var1).getLocation();
               var3 = new Location(var8.getWorld(), this.coord(var2[0], var8.getX()), this.coord(var2[1], var8.getY()), this.coord(var2[2], var8.getZ()));
            } catch (NumberFormatException var7) {
               U.send(var1, "&cInvalid coordinates.");
               return true;
            }
         } else if (var2.length == 1) {
            Player var4 = Bukkit.getPlayerExact(var2[0]);
            if (var4 == null) {
               U.send(var1, "&cThat player isn't online.");
               return true;
            }

            var3 = var4.getLocation();
         } else {
            if (!(var1 instanceof Player)) {
               U.send(var1, "&cUsage: /tpall <player | x y z>");
               return true;
            }

            var3 = ((Player)var1).getLocation();
         }

         int var9 = 0;

         for (Player var6 : Bukkit.getOnlinePlayers()) {
            if (var6 != var1) {
               var6.teleportAsync(var3);
               var9++;
            }
         }

         U.send(var1, "Teleported &e" + var9 + "&7 players.");
         return true;
      }
   }

   private double coord(String var1, double var2) {
      return var1.startsWith("~") ? var2 + (var1.length() == 1 ? 0.0 : Double.parseDouble(var1.substring(1))) : Double.parseDouble(var1);
   }

   private boolean enchant(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "enchant")) {
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /enchant <enchantment> [level]");
         return true;
      } else {
         Player var3 = (Player)var1;
         ItemStack var4 = var3.getInventory().getItemInMainHand();
         if (var4.getType().isAir()) {
            U.send(var1, "&cHold an item first.");
            return true;
         } else {
            String var5 = var2[0].toLowerCase(Locale.ROOT);
            if (var5.startsWith("minecraft:")) {
               var5 = var5.substring(10);
            }

            Enchantment var6 = NamespacedKey.fromString(var5) == null ? null : (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft(var5));
            if (var6 == null) {
               U.send(var1, "&cUnknown enchantment.");
               return true;
            } else {
               int var7 = 1;
               if (var2.length > 1) {
                  try {
                     var7 = Integer.parseInt(var2[1]);
                  } catch (NumberFormatException var9) {
                     U.send(var1, "&cInvalid level.");
                     return true;
                  }
               }

               if (var7 >= 0 && var7 <= 255) {
                  if (var7 == 0) {
                     var4.removeEnchantment(var6);
                  } else {
                     var4.addUnsafeEnchantment(var6, var7);
                  }

                  U.send(var1, "Applied &e" + var5 + " " + var7 + "&7.");
                  return true;
               } else {
                  U.send(var1, "&cLevel must be 0-255.");
                  return true;
               }
            }
         }
      }
   }

   private boolean repair(CommandSender var1) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "repair")) {
         return true;
      } else {
         Player var2 = (Player)var1;
         ItemStack var3 = var2.getInventory().getItemInMainHand();
         ItemMeta var4 = var3.getItemMeta();
         if (var4 instanceof Damageable) {
            ((Damageable)var4).setDamage(0);
            var3.setItemMeta(var4);
            U.send(var1, "Item repaired.");
         } else {
            U.send(var1, "&cThat item can't be repaired.");
         }

         return true;
      }
   }

   private boolean struck(CommandSender var1, String[] var2) {
      if (!this.can(var1, "struck")) {
         return true;
      } else if (var2.length == 0) {
         if (!(var1 instanceof Player var6)) {
            U.send(var1, "&cSpecify a player.");
            return true;
         } else {
            Block var7 = var6.getTargetBlockExact(100);
            Location var5 = var7 == null ? var6.getLocation() : var7.getLocation().add(0.5, 1.0, 0.5);
            var6.getWorld().strikeLightning(var5);
            return true;
         }
      } else {
         for (Player var4 : this.targets(var1, var2, 0)) {
            var4.getWorld().strikeLightning(var4.getLocation());
         }

         return true;
      }
   }

   private boolean sudo(CommandSender var1, String[] var2) {
      if (!this.can(var1, "sudo")) {
         return true;
      } else if (var2.length < 2) {
         U.send(var1, "&cUsage: /sudo <player|*> <command | m:message>");
         return true;
      } else {
         String var3 = String.join(" ", Arrays.copyOfRange(var2, 1, var2.length));

         for (Player var5 : this.targets(var1, var2, 0)) {
            if (!var5.hasPermission("wafflecore.admin") || !(var1 instanceof Player) || var1.hasPermission("wafflecore.admin")) {
               if (var3.startsWith("m:")) {
                  var5.chat(var3.substring(2));
               } else {
                  var5.performCommand(var3.startsWith("/") ? var3.substring(1) : var3);
               }
            }
         }

         U.send(var1, "Done.");
         return true;
      }
   }

   private boolean clear(CommandSender var1, String[] var2) {
      if (!this.can(var1, "clear")) {
         return true;
      } else {
         for (Player var4 : this.targets(var1, var2, 0)) {
            var4.getInventory().clear();
            U.send(var4, "Your inventory was cleared.");
            if (var4 != var1) {
               U.send(var1, "Cleared &e" + var4.getName() + "&7's inventory.");
            }
         }

         return true;
      }
   }

   private boolean clearItems(CommandSender var1) {
      if (!this.can(var1, "cleargrounditems")) {
         return true;
      } else {
         int var2 = 0;

         for (World var4 : Bukkit.getWorlds()) {
            for (Entity var6 : var4.getEntities()) {
               if (var6 instanceof Item) {
                  var6.remove();
                  var2++;
               }
            }
         }

         U.send(var1, "Removed &e" + var2 + "&7 dropped items.");
         return true;
      }
   }

   private boolean phase(CommandSender var1) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "phase")) {
         return true;
      } else {
         Player var2 = (Player)var1;
         Vector var3 = var2.getLocation().getDirection().setY(0);
         if (var3.lengthSquared() < 1.0E-4) {
            U.send(var1, "&cLook horizontally to phase.");
            return true;
         } else {
            var3.normalize();
            Location var4 = var2.getLocation();
            boolean var5 = false;

            for (int var6 = 1; var6 <= 12; var6++) {
               Location var7 = var4.clone().add(var3.clone().multiply(var6));
               Block var8 = var7.getBlock();
               Block var9 = var7.clone().add(0.0, 1.0, 0.0).getBlock();
               boolean var10 = var8.isPassable() && var9.isPassable() && !var8.isLiquid();
               if (!var10) {
                  var5 = true;
               } else if (var5) {
                  Block var11 = var7.clone().add(0.0, -1.0, 0.0).getBlock();
                  if (!var11.isPassable() || var11.isLiquid() || !var11.getType().isAir()) {
                     Location var12 = new Location(
                        var4.getWorld(),
                        (double)var7.getBlockX() + 0.5,
                        (double)var7.getBlockY(),
                        (double)var7.getBlockZ() + 0.5,
                        var4.getYaw(),
                        var4.getPitch()
                     );
                     var2.teleportAsync(var12);
                     U.send(var1, "Phased through.");
                     return true;
                  }
               }
            }

            U.send(var1, "&cNothing to phase through within 12 blocks.");
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (!var5.equals("enchant")) {
         if (var4.length == 1 && !var5.equals("repair") && !var5.equals("phase") && !var5.equals("cleargrounditems")) {
            ArrayList var9 = new ArrayList<>(Eco.onlineNames(var4[0]));
            if (!var5.equals("tpall") && "*".startsWith(var4[0])) {
               var9.add("*");
            }

            return var9;
         } else {
            return List.of();
         }
      } else if (var4.length != 1) {
         return List.of();
      } else {
         ArrayList var6 = new ArrayList();

         for (Enchantment var8 : Registry.ENCHANTMENT) {
            var6.add(var8.getKey().getKey());
         }

         return U.filter(var6, var4[0]);
      }
   }
}
