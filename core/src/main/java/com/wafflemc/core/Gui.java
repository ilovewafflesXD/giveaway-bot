package com.wafflemc.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class Gui implements InventoryHolder {
   final String type;
   final List<String> keys = new ArrayList<>();
   final Map<Integer, String> at = new HashMap<>();
   int page;
   String extra;
   private Inventory inv;

   Gui(String var1) {
      this.type = var1;
   }

   Inventory make(int var1, String var2) {
      this.inv = Bukkit.createInventory(this, var1, U.color(var2));
      return this.inv;
   }

   public Inventory getInventory() {
      return this.inv;
   }

   static ItemStack item(Material var0, String var1, String... var2) {
      ItemStack var3 = new ItemStack(var0);
      ItemMeta var4 = var3.getItemMeta();
      var4.setDisplayName(U.color("&r" + var1));
      if (var2.length > 0) {
         ArrayList var5 = new ArrayList();

         for (String var9 : var2) {
            var5.add(U.color("&r" + var9));
         }

         var4.setLore(var5);
      }

      var3.setItemMeta(var4);
      return var3;
   }

   static ItemStack pane(Material var0) {
      ItemStack var1 = new ItemStack(var0);
      ItemMeta var2 = var1.getItemMeta();
      var2.setDisplayName(" ");
      var1.setItemMeta(var2);
      return var1;
   }

   static void pad(Inventory var0, int var1, Material var2) {
      ItemStack var3 = pane(var2);

      for (int var4 = var1; var4 < var0.getSize(); var4++) {
         if (var0.getItem(var4) == null) {
            var0.setItem(var4, var3);
         }
      }
   }

   static void border(Inventory var0, Material var1) {
      ItemStack var2 = pane(var1);
      int var3 = var0.getSize() / 9;

      for (int var4 = 0; var4 < var0.getSize(); var4++) {
         int var5 = var4 / 9;
         int var6 = var4 % 9;
         if (var5 == 0 || var5 == var3 - 1 || var6 == 0 || var6 == 8) {
            var0.setItem(var4, var2);
         }
      }
   }

   static void menu(Inventory var0, int var1) {
      border(var0, Material.ORANGE_STAINED_GLASS_PANE);
      pad(var0, 0, Material.GRAY_STAINED_GLASS_PANE);
   }

   static List<Integer> inner(int var0) {
      ArrayList<Integer> var1 = new ArrayList<>();

      for (int var2 = 1; var2 < var0 - 1; var2++) {
         for (int var3 = 1; var3 < 8; var3++) {
            var1.add(var2 * 9 + var3);
         }
      }

      return var1;
   }

   static int rowsFor(int var0) {
      return Math.max(3, Math.min(6, (var0 + 6) / 7 + 2));
   }
}
