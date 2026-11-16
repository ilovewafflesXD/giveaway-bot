package com.wafflemc.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class Auction implements CommandExecutor, TabCompleter, Listener {
   private static final int PER_PAGE = 45;
   private final WaffleCore plugin;
   private final Yml listings;
   private final Yml claims;
   private final Map<UUID, String> pendingInput = new ConcurrentHashMap<>();
   private final Map<UUID, String> sortPref = new ConcurrentHashMap<>();
   private final Map<UUID, String> queryPref = new ConcurrentHashMap<>();
   private static final List<String> SORTS = List.of("new", "old", "price-low", "price-high", "ending");
   private long nextId;

   Auction(WaffleCore var1) {
      this.plugin = var1;
      this.listings = var1.store("auction.yml");
      this.claims = var1.store("auction-claims.yml");
      ConfigurationSection var2 = this.listings.cfg.getConfigurationSection("l");
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
      ConfigurationSection var1 = this.listings.cfg.getConfigurationSection("l");
      ArrayList<String> var2 = new ArrayList<>();
      if (var1 != null) {
         var2.addAll(var1.getKeys(false));
      }

      var2.sort((var0, var1x) -> Long.compare(Long.parseLong(var1x), Long.parseLong(var0)));
      return var2;
   }

   private ItemStack give(Player var1, ItemStack var2) {
      for (ItemStack var4 : var1.getInventory().addItem(new ItemStack[]{var2}).values()) {
         var1.getWorld().dropItemNaturally(var1.getLocation(), var4);
      }

      return var2;
   }

   void expireSweep() {
      long var1 = System.currentTimeMillis();
      boolean var3 = false;

      for (String var5 : this.activeIds()) {
         if (this.listings.cfg.getLong("l." + var5 + ".expires") <= var1) {
            this.returnToOwner(var5);
            var3 = true;
         }
      }

      if (var3) {
         this.listings.mark();
         this.claims.mark();
      }
   }

   private void returnToOwner(String var1) {
      String var2 = "l." + var1;
      String var3 = this.listings.cfg.getString(var2 + ".seller");
      ItemStack var4 = this.listings.cfg.getItemStack(var2 + ".item");
      this.listings.cfg.set(var2, null);
      if (var3 != null && var4 != null) {
         ArrayList var5 = new ArrayList();
         List var6 = this.claims.cfg.getList("c." + var3);
         if (var6 != null) {
            for (Object var8 : var6) {
               if (var8 instanceof ItemStack) {
                  var5.add((ItemStack)var8);
               }
            }
         }

         var5.add(var4);
         this.claims.cfg.set("c." + var3, var5);
         Player var9 = Bukkit.getPlayer(UUID.fromString(var3));
         if (var9 != null) {
            U.send(var9, "A listing expired. Use &e/ah claim&7 to collect your item.");
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      List var2 = this.claims.cfg.getList("c." + var1.getPlayer().getUniqueId());
      if (var2 != null && !var2.isEmpty()) {
         Bukkit.getScheduler().runTaskLater(this.plugin.host, () -> {
            if (var1.getPlayer().isOnline()) {
               U.send(var1.getPlayer(), "You have items waiting. Use &e/ah claim&7.");
            }
         }, 40L);
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!(var1 instanceof Player var5)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (var4.length == 0) {
         this.open(var5, 0);
         return true;
      } else {
         String var6 = var4[0].toLowerCase(Locale.ROOT);
         switch (var6) {
            case "sell":
               return this.sell(var5, var4);
            case "claim":
               return this.claim(var5);
            default:
               U.send(var5, "&cUsage: /ah, /ah sell <price>, /ah claim");
               return true;
         }
      }
   }

   private boolean sell(Player var1, String[] var2) {
      if (var2.length < 2) {
         U.send(var1, "&cUsage: /ah sell <price>");
         return true;
      } else {
         this.doSell(var1, var2[1]);
         return true;
      }
   }

   private void startSell(Player var1) {
      ItemStack var2 = var1.getInventory().getItemInMainHand();
      if (var2.getType() == Material.AIR) {
         U.send(var1, "&cHold the item you want to sell, then click the button again.");
      } else {
         var1.closeInventory();
         this.pendingInput.put(var1.getUniqueId(), "sell");
         U.send(
            var1,
            "Type the price for &e"
               + (var2.getAmount() > 1 ? var2.getAmount() + "x " : "")
               + this.pretty(var2.getType())
               + "&7 in chat. Type &ecancel&7 to abort."
         );
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent var1) {
      Player var2 = var1.getPlayer();
      if (this.pendingInput.containsKey(var2.getUniqueId())) {
         var1.setCancelled(true);
         String var3 = PlainTextComponentSerializer.plainText().serialize(var1.message()).trim();
         Bukkit.getScheduler().runTask(this.plugin.host, () -> {
            String var3x = this.pendingInput.remove(var2.getUniqueId());
            if (var2.isOnline() && var3x != null) {
               if (var3.equalsIgnoreCase("cancel")) {
                  U.send(var2, "Cancelled.");
               } else if (var3x.equals("search")) {
                  if (var3.equalsIgnoreCase("clear")) {
                     this.queryPref.remove(var2.getUniqueId());
                  } else {
                     this.queryPref.put(var2.getUniqueId(), var3.toLowerCase(Locale.ROOT));
                  }

                  this.open(var2, 0, false);
               } else {
                  if (this.doSell(var2, var3)) {
                     this.open(var2, 0, true);
                  }
               }
            }
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.pendingInput.remove(var1.getPlayer().getUniqueId());
      this.sortPref.remove(var1.getPlayer().getUniqueId());
      this.queryPref.remove(var1.getPlayer().getUniqueId());
   }

   private boolean doSell(Player var1, String var2) {
      Double var3 = U.parseAmount(var2);
      if (var3 != null && !(var3 <= 0.0) && !(var3 > 1.0E12)) {
         ItemStack var4 = var1.getInventory().getItemInMainHand();
         if (var4.getType() == Material.AIR) {
            U.send(var1, "&cHold the item you want to sell.");
            return false;
         } else {
            int var5 = this.plugin.getConfig().getInt("auction.max-listings", 5);
            int var6 = 0;

            for (String var8 : this.activeIds()) {
               if (var1.getUniqueId().toString().equals(this.listings.cfg.getString("l." + var8 + ".seller"))) {
                  var6++;
               }
            }

            if (var6 >= var5 && !var1.hasPermission("wafflecore.admin")) {
               U.send(var1, "&cYou can only have &e" + var5 + "&c active listings.");
               return false;
            } else {
               long var11 = this.plugin.getConfig().getLong("auction.duration-hours", 48L);
               String var9 = String.valueOf(this.nextId++);
               String var10 = "l." + var9;
               this.listings.cfg.set(var10 + ".seller", var1.getUniqueId().toString());
               this.listings.cfg.set(var10 + ".sellerName", var1.getName());
               this.listings.cfg.set(var10 + ".price", var3);
               this.listings.cfg.set(var10 + ".expires", System.currentTimeMillis() + var11 * 3600000L);
               this.listings.cfg.set(var10 + ".item", var4.clone());
               this.listings.mark();
               this.listings.save();
               var1.getInventory().setItemInMainHand(null);
               var1.playSound(var1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.2F);
               U.send(var1, "Listed for &6" + U.money(var3) + "&7. Expires in &e" + var11 + "h&7.");
               return true;
            }
         }
      } else {
         U.send(var1, "&cEnter a valid price.");
         return false;
      }
   }

   private boolean claim(Player var1) {
      String var2 = "c." + var1.getUniqueId();
      List var3 = this.claims.cfg.getList(var2);
      if (var3 != null && !var3.isEmpty()) {
         for (Object var5 : var3) {
            if (var5 instanceof ItemStack) {
               this.give(var1, (ItemStack)var5);
            }
         }

         this.claims.cfg.set(var2, null);
         this.claims.mark();
         U.send(var1, "Items claimed.");
         return true;
      } else {
         U.send(var1, "&cYou have nothing to claim.");
         return true;
      }
   }

   void open(Player var1, int var2) {
      this.open(var1, var2, false);
   }

   private void reopen(Player var1, Gui var2) {
      this.open(var1, var2.page, "mine".equals(var2.extra));
   }

   private void open(Player var1, int var2, boolean var3) {
      List<String> var4 = this.activeIds();
      if (var3) {
         var4.removeIf(var2x -> !var1.getUniqueId().toString().equals(this.listings.cfg.getString("l." + var2x + ".seller")));
      }

      String var5 = this.sortPref.getOrDefault(var1.getUniqueId(), "new");
      String var6 = this.queryPref.get(var1.getUniqueId());
      if (var6 != null) {
         var4.removeIf(var2x -> !this.matches(var2x, var6));
      }

      switch (var5) {
         case "old":
            var4.sort((var0, var1x) -> Long.compare(Long.parseLong(var0), Long.parseLong(var1x)));
            break;
         case "price-low":
            var4.sort(
               (var1x, var2x) -> Double.compare(this.listings.cfg.getDouble("l." + var1x + ".price"), this.listings.cfg.getDouble("l." + var2x + ".price"))
            );
            break;
         case "price-high":
            var4.sort(
               (var1x, var2x) -> Double.compare(this.listings.cfg.getDouble("l." + var2x + ".price"), this.listings.cfg.getDouble("l." + var1x + ".price"))
            );
            break;
         case "ending":
            var4.sort(
               (var1x, var2x) -> Long.compare(this.listings.cfg.getLong("l." + var1x + ".expires"), this.listings.cfg.getLong("l." + var2x + ".expires"))
            );
      }

      int var7 = Math.max(1, (var4.size() + 45 - 1) / 45);
      var2 = Math.max(0, Math.min(var2, var7 - 1));
      Gui var22 = new Gui("ah");
      var22.page = var2;
      var22.extra = var3 ? "mine" : null;
      Inventory var9 = var22.make(54, "&6&lWaffle&e&lMC &8» &7" + (var3 ? "My Listings" : "Auction House") + " &8(" + (var2 + 1) + "/" + var7 + ")");
      int var10 = var2 * 45;

      for (int var11 = 0; var11 < 45 && var10 + var11 < var4.size(); var11++) {
         String var12 = (String)var4.get(var10 + var11);
         String var13 = "l." + var12;
         ItemStack var14 = this.listings.cfg.getItemStack(var13 + ".item");
         if (var14 != null) {
            ItemStack var15 = var14.clone();
            ItemMeta var16 = var15.getItemMeta();
            ArrayList var17 = var16.hasLore() ? new ArrayList(var16.getLore()) : new ArrayList();
            boolean var18 = var1.getUniqueId().toString().equals(this.listings.cfg.getString(var13 + ".seller"));
            var17.add(U.color("&r&8&m                "));
            var17.add(U.color("&r&7Price: &6" + U.money(this.listings.cfg.getDouble(var13 + ".price"))));
            var17.add(U.color("&r&7Seller: &e" + this.listings.cfg.getString(var13 + ".sellerName", "?")));
            long var19 = (this.listings.cfg.getLong(var13 + ".expires") - System.currentTimeMillis()) / 1000L;
            var17.add(U.color("&r&7Expires in: &f" + U.duration(var19)));
            var17.add(U.color(var18 ? "&cClick to cancel your listing" : "&aClick to buy"));
            var16.setLore(var17);
            var15.setItemMeta(var16);
            var9.setItem(var11, var15);
            var22.keys.add(var12);
         }
      }

      List var23 = this.claims.cfg.getList("c." + var1.getUniqueId());
      int var24 = var23 == null ? 0 : var23.size();
      var9.setItem(45, this.nav(Material.ARROW, "&ePrevious page"));
      var9.setItem(46, this.nav(Material.NAME_TAG, var3 ? "&eShow all listings" : "&eShow my listings"));
      var9.setItem(47, this.nav(Material.EMERALD, "&aSell item in hand", "&7Hold an item, click here,", "&7then type the price in chat."));
      var9.setItem(
         48, this.nav(Material.HOPPER, "&6Sort: &f" + this.sortLabel(var5), "&7Click to change the order.", "&8new, old, cheapest, priciest, ending soon")
      );
      var9.setItem(
         50,
         this.nav(
            Material.SPYGLASS,
            var6 == null ? "&bSearch" : "&bSearch: &f" + var6,
            var6 == null ? "&7Click, then type an item name." : "&7Click to search again.",
            var6 == null ? "" : "&cRight-click to clear."
         )
      );
      var9.setItem(49, this.nav(Material.GOLD_INGOT, "&6Your balance: " + U.money(this.plugin.eco.get(var1.getUniqueId()))));
      var9.setItem(
         51, this.nav(var24 > 0 ? Material.ENDER_CHEST : Material.CHEST, "&bClaim items", var24 > 0 ? "&7" + var24 + " stacks waiting" : "&8Nothing waiting")
      );
      var9.setItem(52, this.nav(Material.WRITABLE_BOOK, "&dBuy orders", "&7Click to open /orders"));
      var9.setItem(53, this.nav(Material.ARROW, "&eNext page"));
      Gui.pad(var9, 45, Material.ORANGE_STAINED_GLASS_PANE);
      var1.openInventory(var9);
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
         if (var2.type.equals("ah")) {
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
                     this.startSell(var3);
                  } else if (var5 == 48) {
                     this.click(var3);
                     String var19 = this.sortPref.getOrDefault(var3.getUniqueId(), "new");
                     this.sortPref.put(var3.getUniqueId(), SORTS.get((SORTS.indexOf(var19) + 1) % SORTS.size()));
                     this.open(var3, 0, var4);
                  } else if (var5 == 50) {
                     this.click(var3);
                     if (var1.isRightClick()) {
                        this.queryPref.remove(var3.getUniqueId());
                        this.open(var3, 0, var4);
                     } else {
                        var3.closeInventory();
                        this.pendingInput.put(var3.getUniqueId(), "search");
                        U.send(var3, "Type what to search for in chat. Type &eclear&7 to reset or &ecancel&7 to abort.");
                     }
                  } else if (var5 == 51) {
                     this.click(var3);
                     var3.closeInventory();
                     this.claim(var3);
                  } else if (var5 == 52) {
                     this.click(var3);
                     this.plugin.orders.open(var3, 0, false);
                  } else if (var5 == 53) {
                     this.click(var3);
                     this.open(var3, var2.page + 1, var4);
                  } else if (var5 >= 0 && var5 < var2.keys.size()) {
                     String var6 = var2.keys.get(var5);
                     String var7 = "l." + var6;
                     if (!this.listings.cfg.contains(var7)) {
                        U.send(var3, "&cThat listing is gone.");
                        this.reopen(var3, var2);
                     } else {
                        String var8 = this.listings.cfg.getString(var7 + ".seller");
                        ItemStack var9 = this.listings.cfg.getItemStack(var7 + ".item");
                        if (var9 != null && var8 != null) {
                           if (var8.equals(var3.getUniqueId().toString())) {
                              this.listings.cfg.set(var7, null);
                              this.listings.mark();
                              this.give(var3, var9);
                              U.send(var3, "Listing cancelled and item returned.");
                              this.reopen(var3, var2);
                           } else {
                              double var10 = this.listings.cfg.getDouble(var7 + ".price");
                              if (!this.plugin.eco.take(var3.getUniqueId(), var10)) {
                                 U.send(var3, "&cYou can't afford that. You have &6" + U.money(this.plugin.eco.get(var3.getUniqueId())) + "&c.");
                              } else {
                                 double var12 = this.plugin.getConfig().getDouble("auction.tax", 5.0);
                                 double var14 = (double)Math.round(var10 * (1.0 - var12 / 100.0) * 100.0) / 100.0;
                                 UUID var16 = UUID.fromString(var8);
                                 this.plugin.eco.add(var16, var14);
                                 this.listings.cfg.set(var7, null);
                                 this.listings.mark();
                                 this.give(var3, var9);
                                 String var17 = (var9.getAmount() > 1 ? var9.getAmount() + "x " : "") + this.pretty(var9.getType());
                                 U.send(var3, "Bought &e" + var17 + "&7 for &6" + U.money(var10) + "&7.");
                                 Player var18 = Bukkit.getPlayer(var16);
                                 if (var18 != null) {
                                    U.send(var18, "&e" + var3.getName() + "&7 bought your &e" + var17 + "&7. You earned &6" + U.money(var14) + "&7.");
                                 }

                                 this.reopen(var3, var2);
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

   private String sortLabel(String var1) {
      switch (var1) {
         case "old":
            return "oldest first";
         case "price-low":
            return "cheapest first";
         case "price-high":
            return "priciest first";
         case "ending":
            return "ending soon";
         default:
            return "newest first";
      }
   }

   private boolean matches(String var1, String var2) {
      String var3 = "l." + var1;
      ItemStack var4 = this.listings.cfg.getItemStack(var3 + ".item");
      if (var4 == null) {
         return false;
      } else {
         StringBuilder var5 = new StringBuilder(this.pretty(var4.getType()).toLowerCase(Locale.ROOT));
         ItemMeta var6 = var4.getItemMeta();
         if (var6 != null && var6.hasDisplayName()) {
            var5.append(' ').append(var6.getDisplayName().toLowerCase(Locale.ROOT));
         }

         var5.append(' ').append(this.listings.cfg.getString(var3 + ".sellerName", "").toLowerCase(Locale.ROOT));
         return var5.toString().contains(var2);
      }
   }

   private void click(Player var1) {
      var1.playSound(var1.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
   }

   private String pretty(Material var1) {
      String[] var2 = var1.name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder var3 = new StringBuilder();

      for (String var7 : var2) {
         var3.append(Character.toUpperCase(var7.charAt(0))).append(var7.substring(1)).append(' ');
      }

      return var3.toString().trim();
   }

   @EventHandler
   public void onDrag(InventoryDragEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         var1.setCancelled(true);
      }
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      return var4.length == 1 ? U.filter(List.of("sell", "claim"), var4[0]) : List.of();
   }
}
