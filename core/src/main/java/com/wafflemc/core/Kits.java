package com.wafflemc.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class Kits implements CommandExecutor, TabCompleter, Listener {
   private static final String[] SLOTS = new String[]{"helmet", "chestplate", "leggings", "boots", "offhand"};
   private final WaffleCore plugin;
   private final Yml kits;
   private final Yml cds;

   Kits(WaffleCore var1) {
      this.plugin = var1;
      File var2 = new File(var1.getDataFolder(), "kits.yml");
      File var3 = new File(var1.getDataFolder(), "kit-cooldowns.yml");
      File var4 = new File(var1.getDataFolder().getParentFile(), "Kits");
      if (!var2.exists() && new File(var4, "kits.yml").exists()) {
         try {
            var1.getDataFolder().mkdirs();
            Files.copy(new File(var4, "kits.yml").toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException var6) {
            var1.getLogger().warning("Kit import failed: " + var6.getMessage());
         }
      }

      this.kits = var1.store("kits.yml");
      this.cds = var1.store("kit-cooldowns.yml");
      if (!var3.exists()) {
         this.importCooldowns();
      }
   }

   int importCooldowns() {
      File var1 = new File(this.plugin.getDataFolder().getParentFile(), "Kits/data.yml");
      if (!var1.exists()) {
         return 0;
      } else {
         YamlConfiguration var2 = YamlConfiguration.loadConfiguration(var1);
         ConfigurationSection var3 = var2.getConfigurationSection("cooldowns");
         if (var3 == null) {
            return 0;
         } else {
            int var4 = 0;

            for (String var6 : var3.getKeys(false)) {
               ConfigurationSection var7 = var3.getConfigurationSection(var6);
               if (var7 != null) {
                  for (String var9 : var7.getKeys(false)) {
                     this.cds.cfg.set("cd." + var6 + "." + var9, var7.getLong(var9));
                     var4++;
                  }
               }
            }

            this.cds.mark();
            this.cds.save();
            return var4;
         }
      }
   }

   private List<String> names() {
      return new ArrayList<>(this.kits.cfg.getKeys(false));
   }

   private boolean canUse(Player var1, String var2) {
      return var1.hasPermission("wafflecore.admin")
         || var1.hasPermission("wafflecore.kit." + var2)
         || var1.hasPermission("wafflecore.kit.*")
         || var1.hasPermission("kits.kit." + var2)
         || var1.hasPermission("kits.kit.*");
   }

   private long cooldownSecs(String var1) {
      return this.kits.cfg.getLong(var1 + ".cooldown", 0L);
   }

   private long remainingMillis(Player var1, String var2) {
      long var3 = this.cooldownSecs(var2);
      long var5 = this.cds.cfg.getLong("cd." + var1.getUniqueId() + "." + var2, 0L);
      if (var3 == 0L || var5 == 0L) {
         return 0L;
      } else {
         return var3 < 0L ? -1L : Math.max(0L, var5 + var3 * 1000L - System.currentTimeMillis());
      }
   }

   private List<ItemStack> itemsOf(String var1) {
      ArrayList var2 = new ArrayList();
      List var3 = this.kits.cfg.getList(var1 + ".items");
      if (var3 != null) {
         for (Object var5 : var3) {
            if (var5 instanceof ItemStack) {
               var2.add(((ItemStack)var5).clone());
            }
         }
      }

      return var2;
   }

   private Map<String, ItemStack> armorOf(String var1) {
      HashMap var2 = new HashMap();

      for (String var6 : SLOTS) {
         ItemStack var7 = this.kits.cfg.getItemStack(var1 + ".armor." + var6);
         if (var7 != null && var7.getType() != Material.AIR) {
            var2.put(var6, var7.clone());
         }
      }

      return var2;
   }

   private void give(Player var1, String var2) {
      PlayerInventory var3 = var1.getInventory();
      boolean var4 = false;

      for (Entry var6 : this.armorOf(var2).entrySet()) {
         ItemStack var7 = (ItemStack)var6.getValue();
         boolean var8 = false;
         String var9 = (String)var6.getKey();
         byte var10 = -1;
         switch (var9.hashCode()) {
            case -1220934547:
               if (var9.equals("helmet")) {
                  var10 = 0;
               }
               break;
            case 93922241:
               if (var9.equals("boots")) {
                  var10 = 3;
               }
               break;
            case 1069952181:
               if (var9.equals("chestplate")) {
                  var10 = 1;
               }
               break;
            case 1735676010:
               if (var9.equals("leggings")) {
                  var10 = 2;
               }
         }

         var8 = switch (var10) {
            case 0 -> {
               if (this.isEmpty(var3.getHelmet())) {
                  var3.setHelmet(var7);
                  yield true;
               }
               yield false;
            }
            case 1 -> {
               if (this.isEmpty(var3.getChestplate())) {
                  var3.setChestplate(var7);
                  yield true;
               }
               yield false;
            }
            case 2 -> {
               if (this.isEmpty(var3.getLeggings())) {
                  var3.setLeggings(var7);
                  yield true;
               }
               yield false;
            }
            case 3 -> {
               if (this.isEmpty(var3.getBoots())) {
                  var3.setBoots(var7);
                  yield true;
               }
               yield false;
            }
            default -> {
               if (this.isEmpty(var3.getItemInOffHand())) {
                  var3.setItemInOffHand(var7);
                  yield true;
               }
               yield false;
            }
         };

         if (!var8) {
            var4 |= this.stash(var1, var7);
         }
      }

      for (ItemStack var12 : this.itemsOf(var2)) {
         var4 |= this.stash(var1, var12);
      }

      if (var4) {
         U.send(var1, "&eSome items didn't fit and were dropped at your feet.");
      }
   }

   private boolean isEmpty(ItemStack var1) {
      return var1 == null || var1.getType() == Material.AIR;
   }

   private boolean stash(Player var1, ItemStack var2) {
      HashMap<Integer, ItemStack> var3 = var1.getInventory().addItem(new ItemStack[]{var2});

      for (ItemStack var5 : var3.values()) {
         var1.getWorld().dropItemNaturally(var1.getLocation(), var5);
      }

      return !var3.isEmpty();
   }

   private void claim(Player var1, String var2) {
      if (!this.kits.cfg.contains(var2)) {
         U.send(var1, "&cKit &e" + var2 + "&c doesn't exist.");
      } else if (!this.canUse(var1, var2)) {
         U.send(var1, "&cYou don't have access to kit &e" + var2 + "&c.");
      } else {
         long var3 = this.remainingMillis(var1, var2);
         if (var3 == -1L && !var1.hasPermission("wafflecore.bypass.cooldown")) {
            U.send(var1, "&cKit &e" + var2 + "&c can only be claimed once.");
         } else if (var3 > 0L && !var1.hasPermission("wafflecore.bypass.cooldown")) {
            U.send(var1, "&cKit &e" + var2 + "&c is available in &e" + U.duration(var3 / 1000L + 1L) + "&c.");
         } else {
            this.give(var1, var2);
            this.cds.cfg.set("cd." + var1.getUniqueId() + "." + var2, System.currentTimeMillis());
            this.cds.mark();
            U.send(var1, "You received kit &e" + var2 + "&7!");
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      if (this.plugin.module("firstjoin")) {
         Player var2 = var1.getPlayer();
         String var3 = this.plugin.getConfig().getString("first-join-kit", "");
         if (!var2.hasPlayedBefore() && !var3.isEmpty() && this.kits.cfg.contains(var3)) {
            Bukkit.getScheduler().runTaskLater(this.plugin.host, () -> {
               if (var2.isOnline()) {
                  this.give(var2, var3);
                  this.cds.cfg.set("cd." + var2.getUniqueId() + "." + var3, System.currentTimeMillis());
                  this.cds.mark();
                  U.send(var2, "Welcome to &6&lWaffle&e&lMC&7! Here's your starter kit.");
               }
            }, 10L);
         }
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (!var5.equals("createkit") && !var5.equals("delkit")) {
         if (!(var1 instanceof Player var6)) {
            U.send(var1, "&cPlayers only.");
            return true;
         } else if (!var5.equals("kits") && var4.length != 0) {
            if (var4.length >= 2 && var6.hasPermission("wafflecore.admin")) {
               Player var7 = Bukkit.getPlayerExact(var4[1]);
               if (var7 == null) {
                  U.send(var6, "&cThat player isn't online.");
                  return true;
               } else {
                  String var8 = var4[0].toLowerCase(Locale.ROOT);
                  if (!this.kits.cfg.contains(var8)) {
                     U.send(var6, "&cKit &e" + var8 + "&c doesn't exist.");
                     return true;
                  } else {
                     this.give(var7, var8);
                     U.send(var6, "Gave kit &e" + var8 + "&7 to &e" + var7.getName() + "&7.");
                     U.send(var7, "You received kit &e" + var8 + "&7!");
                     return true;
                  }
               }
            } else {
               this.claim(var6, var4[0].toLowerCase(Locale.ROOT));
               return true;
            }
         } else {
            this.openMenu(var6);
            return true;
         }
      } else if (!var1.hasPermission("wafflecore.admin")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else {
         return var5.equals("createkit") ? this.create(var1, var4) : this.delete(var1, var4);
      }
   }

   private boolean create(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (var2.length != 0 && var2[0].matches("[A-Za-z0-9_-]{1,24}")) {
         long var4 = var2.length > 1 ? U.parseDuration(var2[1]) : 0L;
         if (var4 == Long.MIN_VALUE) {
            U.send(var3, "&cInvalid cooldown. Examples: 30m, 1h, 1d12h, 0, -1.");
            return true;
         } else {
            String var6 = var2[0].toLowerCase(Locale.ROOT);
            PlayerInventory var7 = var3.getInventory();
            ArrayList var8 = new ArrayList();

            for (ItemStack var12 : var7.getStorageContents()) {
               if (!this.isEmpty(var12)) {
                  var8.add(var12.clone());
               }
            }

            HashMap<String, ItemStack> var13 = new HashMap<>();
            if (!this.isEmpty(var7.getHelmet())) {
               var13.put("helmet", var7.getHelmet().clone());
            }

            if (!this.isEmpty(var7.getChestplate())) {
               var13.put("chestplate", var7.getChestplate().clone());
            }

            if (!this.isEmpty(var7.getLeggings())) {
               var13.put("leggings", var7.getLeggings().clone());
            }

            if (!this.isEmpty(var7.getBoots())) {
               var13.put("boots", var7.getBoots().clone());
            }

            if (!this.isEmpty(var7.getItemInOffHand())) {
               var13.put("offhand", var7.getItemInOffHand().clone());
            }

            if (var8.isEmpty() && var13.isEmpty()) {
               U.send(var3, "&cYour inventory is empty.");
               return true;
            } else {
               Material var14 = !this.isEmpty(var7.getItemInMainHand())
                  ? var7.getItemInMainHand().getType()
                  : (var8.isEmpty() ? Material.CHEST : ((ItemStack)var8.get(0)).getType());
               this.kits.cfg.set(var6, null);
               this.kits.cfg.set(var6 + ".cooldown", var4);
               this.kits.cfg.set(var6 + ".icon", var14.name());
               this.kits.cfg.set(var6 + ".items", var8);

               for (Entry var16 : var13.entrySet()) {
                  this.kits.cfg.set(var6 + ".armor." + (String)var16.getKey(), var16.getValue());
               }

               this.kits.mark();
               this.kits.save();
               U.send(var3, "Created kit &e" + var6 + "&7 (cooldown: &e" + (var4 < 0L ? "one-time" : (var4 == 0L ? "none" : U.duration(var4))) + "&7).");
               return true;
            }
         }
      } else {
         U.send(var3, "&cUsage: /createkit <name> [cooldown]  (e.g. 30m, 1d12h, 0, -1)");
         return true;
      }
   }

   private boolean delete(CommandSender var1, String[] var2) {
      if (var2.length != 0 && this.kits.cfg.contains(var2[0].toLowerCase(Locale.ROOT))) {
         this.kits.cfg.set(var2[0].toLowerCase(Locale.ROOT), null);
         this.kits.mark();
         this.kits.save();
         U.send(var1, "Deleted kit &e" + var2[0].toLowerCase(Locale.ROOT) + "&7.");
         return true;
      } else {
         U.send(var1, "&cUsage: /delkit <name>");
         return true;
      }
   }

   private void openMenu(Player var1) {
      List<String> var2 = this.names();
      if (var2.isEmpty()) {
         U.send(var1, "&cThere are no kits yet.");
      } else {
         int var3 = Gui.rowsFor(var2.size());
         Gui var4 = new Gui("kits");
         Inventory var5 = var4.make(var3 * 9, "&6&lWaffle&e&lMC &8» &7Kits");
         Gui.border(var5, Material.ORANGE_STAINED_GLASS_PANE);
         List var6 = Gui.inner(var3);
         int var7 = 0;

         for (String var9 : var2) {
            if (var7 >= var6.size()) {
               break;
            }

            Material var10 = Material.matchMaterial(this.kits.cfg.getString(var9 + ".icon", "CHEST"));
            ArrayList<String> var11 = new ArrayList<>();
            long var12 = this.cooldownSecs(var9);
            var11.add("&7Cooldown: &f" + (var12 < 0L ? "one-time" : (var12 == 0L ? "none" : U.duration(var12))));
            String var14;
            if (!this.canUse(var1, var9)) {
               var14 = "&c✖ No access";
            } else {
               long var15 = this.remainingMillis(var1, var9);
               if (var15 == -1L) {
                  var14 = "&c✖ Already claimed";
               } else if (var15 > 0L) {
                  var14 = "&c⌚ Ready in &e" + U.duration(var15 / 1000L + 1L);
               } else {
                  var14 = "&a✔ Ready to claim";
               }
            }

            var11.add(var14);
            var11.add("");
            var11.add("&eLeft-click &7to claim");
            var11.add("&eRight-click &7to preview");
            int var17 = (Integer)var6.get(var7++);
            var5.setItem(var17, Gui.item(var10 == null ? Material.CHEST : var10, "&6&l" + var9, var11.toArray(new String[0])));
            var4.at.put(var17, var9);
         }

         var5.setItem(4, Gui.item(Material.NETHER_STAR, "&6&lKits", "&7Pick a kit to claim."));
         var5.setItem(var3 * 9 - 5, Gui.item(Material.BARRIER, "&cClose"));
         Gui.pad(var5, 0, Material.GRAY_STAINED_GLASS_PANE);
         var1.openInventory(var5);
      }
   }

   private void openPreview(Player var1, String var2) {
      Gui var3 = new Gui("kitpreview");
      Inventory var4 = var3.make(54, "&6&lWaffle&e&lMC &8» &7Preview: " + var2);
      int var5 = 0;

      for (ItemStack var7 : this.itemsOf(var2)) {
         if (var5 < 45) {
            var4.setItem(var5++, var7);
         }
      }

      for (ItemStack var10 : this.armorOf(var2).values()) {
         if (var5 < 45) {
            var4.setItem(var5++, var10);
         }
      }

      for (int var9 = 45; var9 < 54; var9++) {
         var4.setItem(var9, Gui.pane(Material.ORANGE_STAINED_GLASS_PANE));
      }

      var4.setItem(49, Gui.item(Material.ARROW, "&cBack", "&7Return to the kit list"));
      var1.openInventory(var4);
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("kits") || var2.type.equals("kitpreview")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  if (var2.type.equals("kitpreview")) {
                     if (var1.getSlot() == 49) {
                        this.openMenu(var3);
                     }
                  } else {
                     int var4 = var1.getSlot();
                     if (var4 == var1.getInventory().getSize() - 5) {
                        var3.closeInventory();
                     } else {
                        String var5 = var2.at.get(var4);
                        if (var5 != null) {
                           if (var1.isRightClick()) {
                              this.openPreview(var3, var5);
                           } else {
                              this.claim(var3, var5);
                              var3.closeInventory();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         var1.setCancelled(true);
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (var4.length != 1 || !var5.equals("kit") && !var5.equals("delkit")) {
         return var4.length == 2 && var5.equals("kit") ? Eco.onlineNames(var4[1]) : List.of();
      } else {
         return U.filter(this.names(), var4[0]);
      }
   }
}
