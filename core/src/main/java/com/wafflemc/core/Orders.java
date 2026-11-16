package com.wafflemc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class Orders implements CommandExecutor, TabCompleter, Listener {
   private static final int PER_PAGE = 45;
   private final WaffleCore plugin;
   private final Yml orders;
   private final Yml deliveries;
   private long nextId;

   Orders(WaffleCore var1) {
      this.plugin = var1;
      this.orders = var1.store("orders.yml");
      this.deliveries = var1.store("order-deliveries.yml");
      ConfigurationSection var2 = this.orders.cfg.getConfigurationSection("o");
      if (var2 != null) {
         for (String var4 : var2.getKeys(false)) {
            try {
               this.nextId = Math.max(this.nextId, Long.parseLong(var4) + 1L);
            } catch (NumberFormatException var6) {
            }
         }
      }
   }

   private List<String> activeIds() {
      ConfigurationSection var1 = this.orders.cfg.getConfigurationSection("o");
      ArrayList<String> var2 = new ArrayList<>();
      if (var1 != null) {
         var2.addAll(var1.getKeys(false));
      }

      var2.sort((var0, var1x) -> Long.compare(Long.parseLong(var1x), Long.parseLong(var0)));
      return var2;
   }

   private boolean isMine(Player var1, String var2) {
      return var1.getUniqueId().toString().equals(this.orders.cfg.getString("o." + var2 + ".buyer"));
   }

   private int pendingCount(UUID var1) {
      ConfigurationSection var2 = this.deliveries.cfg.getConfigurationSection("d." + var1);
      if (var2 == null) {
         return 0;
      } else {
         int var3 = 0;

         for (String var5 : var2.getKeys(false)) {
            var3 += var2.getInt(var5);
         }

         return var3;
      }
   }

   private String pretty(Material var1) {
      String[] var2 = var1.name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder var3 = new StringBuilder();

      for (String var7 : var2) {
         var3.append(Character.toUpperCase(var7.charAt(0))).append(var7.substring(1)).append(' ');
      }

      return var3.toString().trim();
   }

   private void click(Player var1) {
      var1.playSound(var1.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
   }

   private boolean allowed(Material var1) {
      if (var1 != null && var1.isItem() && !var1.isAir()) {
         String var2 = var1.name();
         return !var2.contains("COMMAND_BLOCK")
            && !var2.contains("SPAWNER")
            && !var2.contains("BEDROCK")
            && !var2.contains("BARRIER")
            && !var2.contains("STRUCTURE")
            && !var2.contains("JIGSAW")
            && !var2.contains("DEBUG_STICK")
            && !var2.contains("LIGHT")
            && !var2.contains("SPAWN_EGG")
            && !var2.contains("KNOWLEDGE_BOOK");
      } else {
         return false;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      int var2 = this.pendingCount(var1.getPlayer().getUniqueId());
      if (var2 > 0) {
         Bukkit.getScheduler().runTaskLater(this.plugin.host, () -> {
            if (var1.getPlayer().isOnline()) {
               U.send(var1.getPlayer(), "Your orders were filled. Use &e/orders claim&7 to collect &e" + var2 + "&7 items.");
            }
         }, 60L);
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!(var1 instanceof Player var5)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (var4.length == 0) {
         this.open(var5, 0, false);
         return true;
      } else {
         String var6 = var4[0].toLowerCase(Locale.ROOT);
         switch (var6) {
            case "create":
               return this.create(var5, var4);
            case "claim":
               return this.claim(var5);
            case "mine":
               this.open(var5, 0, true);
               return true;
            default:
               U.send(var5, "&cUsage: /orders, /orders create <amount> <price-each> [item], /orders mine, /orders claim");
               return true;
         }
      }
   }

   private boolean create(Player var1, String[] var2) {
      if (var2.length < 3) {
         U.send(var1, "&cUsage: /orders create <amount> <price-each> [item]");
         U.send(var1, "&7Hold the item you want, or name it, e.g. &e/orders create 64 5 cobblestone");
         return true;
      } else {
         int var3;
         try {
            var3 = Integer.parseInt(var2[1]);
         } catch (NumberFormatException var13) {
            U.send(var1, "&cEnter a valid amount.");
            return true;
         }

         int var4 = this.plugin.getConfig().getInt("orders.max-amount", 2304);
         if (var3 >= 1 && var3 <= var4) {
            Double var5 = U.parseAmount(var2[2]);
            if (var5 != null && !(var5 <= 0.0) && !(var5 > 1.0E9)) {
               Material var6;
               if (var2.length >= 4) {
                  var6 = Material.matchMaterial(var2[3]);
               } else {
                  var6 = var1.getInventory().getItemInMainHand().getType();
               }

               if (!this.allowed(var6)) {
                  U.send(var1, "&cHold a valid item or name one: &e/orders create <amount> <price-each> <item>");
                  return true;
               } else {
                  int var7 = this.plugin.getConfig().getInt("orders.max-orders", 5);
                  int var8 = 0;

                  for (String var10 : this.activeIds()) {
                     if (this.isMine(var1, var10)) {
                        var8++;
                     }
                  }

                  if (var8 >= var7 && !var1.hasPermission("wafflecore.admin")) {
                     U.send(var1, "&cYou can only have &e" + var7 + "&c active orders.");
                     return true;
                  } else {
                     double var14 = (double)Math.round(var5 * (double)var3 * 100.0) / 100.0;
                     if (!this.plugin.eco.take(var1.getUniqueId(), var14)) {
                        U.send(
                           var1,
                           "&cYou need &6" + U.money(var14) + "&c to place that order. You have &6" + U.money(this.plugin.eco.get(var1.getUniqueId())) + "&c."
                        );
                        return true;
                     } else {
                        String var11 = String.valueOf(this.nextId++);
                        String var12 = "o." + var11;
                        this.orders.cfg.set(var12 + ".buyer", var1.getUniqueId().toString());
                        this.orders.cfg.set(var12 + ".buyerName", var1.getName());
                        this.orders.cfg.set(var12 + ".item", var6.name());
                        this.orders.cfg.set(var12 + ".price", var5);
                        this.orders.cfg.set(var12 + ".amount", var3);
                        this.orders.cfg.set(var12 + ".created", System.currentTimeMillis());
                        this.orders.mark();
                        this.orders.save();
                        U.send(
                           var1,
                           "Order placed: &e"
                              + var3
                              + "x "
                              + this.pretty(var6)
                              + "&7 at &6"
                              + U.money(var5)
                              + "&7 each. &6"
                              + U.money(var14)
                              + "&7 held until filled or cancelled."
                        );
                        return true;
                     }
                  }
               }
            } else {
               U.send(var1, "&cEnter a valid price per item.");
               return true;
            }
         } else {
            U.send(var1, "&cAmount must be between &e1&c and &e" + var4 + "&c.");
            return true;
         }
      }
   }

   private boolean claim(Player var1) {
      String var2 = "d." + var1.getUniqueId();
      ConfigurationSection var3 = this.deliveries.cfg.getConfigurationSection(var2);
      if (var3 != null && !var3.getKeys(false).isEmpty()) {
         int var4 = 0;

         for (String var6 : var3.getKeys(false)) {
            Material var7 = Material.matchMaterial(var6);
            int var8 = var3.getInt(var6);
            if (var7 != null && var8 > 0) {
               var4 += var8;

               while (var8 > 0) {
                  int var9 = Math.min(var8, var7.getMaxStackSize());
                  this.give(var1, new ItemStack(var7, var9));
                  var8 -= var9;
               }
            }
         }

         this.deliveries.cfg.set(var2, null);
         this.deliveries.mark();
         U.send(var1, "Claimed &e" + var4 + "&7 items.");
         return true;
      } else {
         U.send(var1, "&cYou have nothing to claim.");
         return true;
      }
   }

   private void give(Player var1, ItemStack var2) {
      for (ItemStack var4 : var1.getInventory().addItem(new ItemStack[]{var2}).values()) {
         var1.getWorld().dropItemNaturally(var1.getLocation(), var4);
      }
   }

   void open(Player var1, int var2, boolean var3) {
      List<String> var4 = this.activeIds();
      if (var3) {
         var4.removeIf(var2x -> !this.isMine(var1, var2x));
      }

      int var5 = Math.max(1, (var4.size() + 45 - 1) / 45);
      var2 = Math.max(0, Math.min(var2, var5 - 1));
      Gui var6 = new Gui("orders");
      var6.page = var2;
      var6.extra = var3 ? "mine" : null;
      Inventory var7 = var6.make(54, "&6&lWaffle&e&lMC &8» &7" + (var3 ? "My Orders" : "Buy Orders") + " &8(" + (var2 + 1) + "/" + var5 + ")");
      int var8 = var2 * 45;

      for (int var9 = 0; var9 < 45 && var8 + var9 < var4.size(); var9++) {
         String var10 = (String)var4.get(var8 + var9);
         String var11 = "o." + var10;
         Material var12 = Material.matchMaterial(this.orders.cfg.getString(var11 + ".item", ""));
         if (var12 != null) {
            int var13 = this.orders.cfg.getInt(var11 + ".amount");
            double var14 = this.orders.cfg.getDouble(var11 + ".price");
            boolean var16 = this.isMine(var1, var10);
            ArrayList<String> var17 = new ArrayList<>();
            var17.add(U.color("&r&7Wanted: &f" + var13));
            var17.add(U.color("&r&7Pays: &6" + U.money(var14) + " &7each"));
            var17.add(U.color("&r&7Total left: &6" + U.money(var14 * (double)var13)));
            var17.add(U.color("&r&7Buyer: &e" + this.orders.cfg.getString(var11 + ".buyerName", "?")));
            var17.add("");
            if (var16) {
               var17.add(U.color("&r&cClick to cancel and refund"));
            } else {
               int var18 = this.countMatching(var1, var12);
               var17.add(U.color("&r&7You have: &f" + var18));
               var17.add(U.color(var18 > 0 ? "&aClick to sell " + Math.min(var18, var13) + " to this order" : "&8You have none to sell"));
            }

            ItemStack var22 = new ItemStack(var12);
            ItemMeta var19 = var22.getItemMeta();
            var19.setDisplayName(U.color("&r&6&l" + this.pretty(var12)));
            var19.setLore(var17);
            var22.setItemMeta(var19);
            var7.setItem(var9, var22);
            var6.keys.add(var10);
         }
      }

      int var21 = this.pendingCount(var1.getUniqueId());
      var7.setItem(45, this.nav(Material.ARROW, "&ePrevious page"));
      var7.setItem(46, this.nav(Material.NAME_TAG, var3 ? "&eShow all orders" : "&eShow my orders"));
      var7.setItem(47, this.nav(Material.WRITABLE_BOOK, "&aCreate an order", "&7Use &e/orders create <amount> <price> [item]", "&7Hold the item or name it."));
      var7.setItem(49, this.nav(Material.GOLD_INGOT, "&6Your balance: " + U.money(this.plugin.eco.get(var1.getUniqueId()))));
      var7.setItem(
         51,
         this.nav(
            var21 > 0 ? Material.ENDER_CHEST : Material.CHEST, "&bClaim delivered items", var21 > 0 ? "&7" + var21 + " items waiting" : "&8Nothing waiting"
         )
      );
      var7.setItem(52, this.nav(Material.EMERALD, "&aAuction house", "&7Click to open /ah"));
      var7.setItem(53, this.nav(Material.ARROW, "&eNext page"));
      Gui.pad(var7, 45, Material.ORANGE_STAINED_GLASS_PANE);
      var1.openInventory(var7);
   }

   private int countMatching(Player var1, Material var2) {
      int var3 = 0;

      for (ItemStack var7 : var1.getInventory().getStorageContents()) {
         if (var7 != null && var7.getType() == var2 && !var7.hasItemMeta()) {
            var3 += var7.getAmount();
         }
      }

      return var3;
   }

   private int removeMatching(Player var1, Material var2, int var3) {
      int var4 = var3;
      ItemStack[] var5 = var1.getInventory().getStorageContents();

      for (int var6 = 0; var6 < var5.length && var4 > 0; var6++) {
         ItemStack var7 = var5[var6];
         if (var7 != null && var7.getType() == var2 && !var7.hasItemMeta()) {
            int var8 = Math.min(var4, var7.getAmount());
            var4 -= var8;
            if (var8 >= var7.getAmount()) {
               var5[var6] = null;
            } else {
               var7.setAmount(var7.getAmount() - var8);
            }
         }
      }

      var1.getInventory().setStorageContents(var5);
      return var3 - var4;
   }

   private ItemStack nav(Material var1, String var2, String... var3) {
      ItemStack var4 = new ItemStack(var1);
      ItemMeta var5 = var4.getItemMeta();
      var5.setDisplayName(U.color("&r" + var2));
      if (var3.length > 0) {
         ArrayList var6 = new ArrayList();

         for (String var10 : var3) {
            var6.add(U.color("&r" + var10));
         }

         var5.setLore(var6);
      }

      var4.setItemMeta(var5);
      return var4;
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("orders")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  boolean var4 = "mine".equals(var2.extra);
                  int var5 = var1.getSlot();
                  if (var5 == 45) {
                     this.click(var3);
                     this.open(var3, var2.page - 1, var4);
                  } else if (var5 == 46) {
                     this.click(var3);
                     this.open(var3, 0, !var4);
                  } else if (var5 == 47) {
                     this.click(var3);
                     var3.closeInventory();
                     U.send(var3, "Use &e/orders create <amount> <price-each> [item]&7. Hold the item or name it.");
                  } else if (var5 == 51) {
                     this.click(var3);
                     var3.closeInventory();
                     this.claim(var3);
                  } else if (var5 == 52) {
                     this.click(var3);
                     this.plugin.auction.open(var3, 0);
                  } else if (var5 == 53) {
                     this.click(var3);
                     this.open(var3, var2.page + 1, var4);
                  } else if (var5 >= 0 && var5 < var2.keys.size()) {
                     String var6 = var2.keys.get(var5);
                     String var7 = "o." + var6;
                     if (!this.orders.cfg.contains(var7)) {
                        U.send(var3, "&cThat order is gone.");
                        this.open(var3, var2.page, var4);
                     } else {
                        Material var8 = Material.matchMaterial(this.orders.cfg.getString(var7 + ".item", ""));
                        int var9 = this.orders.cfg.getInt(var7 + ".amount");
                        double var10 = this.orders.cfg.getDouble(var7 + ".price");
                        String var12 = this.orders.cfg.getString(var7 + ".buyer");
                        if (var8 != null && var12 != null) {
                           if (this.isMine(var3, var6)) {
                              double var21 = (double)Math.round(var10 * (double)var9 * 100.0) / 100.0;
                              this.plugin.eco.add(var3.getUniqueId(), var21);
                              this.orders.cfg.set(var7, null);
                              this.orders.mark();
                              this.click(var3);
                              U.send(var3, "Order cancelled. Refunded &6" + U.money(var21) + "&7.");
                              this.open(var3, var2.page, var4);
                           } else {
                              int var13 = this.countMatching(var3, var8);
                              if (var13 <= 0) {
                                 U.send(var3, "&cYou don't have any &e" + this.pretty(var8) + "&c to sell. Items with custom names or damage don't count.");
                              } else {
                                 int var14 = this.removeMatching(var3, var8, Math.min(var13, var9));
                                 if (var14 > 0) {
                                    double var15 = (double)Math.round(var10 * (double)var14 * 100.0) / 100.0;
                                    this.plugin.eco.add(var3.getUniqueId(), var15);
                                    UUID var17 = UUID.fromString(var12);
                                    String var18 = "d." + var12 + "." + var8.name();
                                    this.deliveries.cfg.set(var18, this.deliveries.cfg.getInt(var18) + var14);
                                    this.deliveries.mark();
                                    if (var14 >= var9) {
                                       this.orders.cfg.set(var7, null);
                                    } else {
                                       this.orders.cfg.set(var7 + ".amount", var9 - var14);
                                    }

                                    this.orders.mark();
                                    var3.playSound(var3.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
                                    U.send(var3, "Sold &e" + var14 + "x " + this.pretty(var8) + "&7 for &6" + U.money(var15) + "&7.");
                                    Player var19 = Bukkit.getPlayer(var17);
                                    if (var19 != null) {
                                       String var20 = var14 >= var9 ? "completely filled" : "partly filled";
                                       U.send(
                                          var19,
                                          "Your order for &e"
                                             + this.pretty(var8)
                                             + "&7 was "
                                             + var20
                                             + " by &e"
                                             + var3.getName()
                                             + "&7 (&e"
                                             + var14
                                             + "&7 items). Use &e/orders claim&7."
                                       );
                                    }

                                    this.open(var3, var2.page, var4);
                                 }
                              }
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
      if (var4.length == 1) {
         return U.filter(List.of("create", "mine", "claim"), var4[0]);
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("create")) {
         return U.filter(List.of("1", "16", "64", "128", "576"), var4[1]);
      } else if (var4.length == 4 && var4[0].equalsIgnoreCase("create")) {
         ArrayList var5 = new ArrayList();
         String var6 = var4[3].toLowerCase(Locale.ROOT);

         for (Material var10 : Material.values()) {
            if (var5.size() >= 40) {
               break;
            }

            String var11 = var10.name().toLowerCase(Locale.ROOT);
            if (var10.isItem() && var11.startsWith(var6)) {
               var5.add(var11);
            }
         }

         return var5;
      } else {
         return List.of();
      }
   }
}
