package com.wafflemc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

final class Mining implements Listener {
   private final WaffleCore plugin;
   private final Set<UUID> busy = new HashSet<>();

   Mining(WaffleCore var1) {
      this.plugin = var1;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent var1) {
      if (this.plugin.module("mining")) {
         Player var2 = var1.getPlayer();
         if (!this.busy.contains(var2.getUniqueId())) {
            if (var2.getGameMode() != GameMode.CREATIVE) {
               if (!this.plugin.getConfig().getBoolean("mining.require-sneak", true) || var2.isSneaking()) {
                  ItemStack var3 = var2.getInventory().getItemInMainHand();
                  String var4 = var3.getType().name();
                  Block var5 = var1.getBlock();
                  Material var6 = var5.getType();
                  int var7;
                  if (var4.endsWith("_AXE")
                     && Tag.LOGS.isTagged(var6)
                     && this.plugin.getConfig().getBoolean("mining.trees", true)
                     && var2.hasPermission("wafflecore.mining.trees")) {
                     var7 = this.plugin.getConfig().getInt("mining.tree-limit", 128);
                  } else {
                     if (!var4.endsWith("_PICKAXE")
                        || !this.isOre(var6)
                        || !this.plugin.getConfig().getBoolean("mining.veins", true)
                        || !var2.hasPermission("wafflecore.mining.veins")) {
                        return;
                     }

                     var7 = this.plugin.getConfig().getInt("mining.vein-limit", 64);
                  }

                  List<Block> var8 = this.collect(var5, var6, var7);
                  var8.remove(var5);
                  this.busy.add(var2.getUniqueId());

                  try {
                     for (Block var10 : var8) {
                        if (!this.canKeepMining(var2.getInventory().getItemInMainHand(), var3.getType())) {
                           break;
                        }

                        if (var10.getType() == var6) {
                           var2.breakBlock(var10);
                        }
                     }
                  } finally {
                     this.busy.remove(var2.getUniqueId());
                  }
               }
            }
         }
      }
   }

   private boolean isOre(Material var1) {
      String var2 = var1.name();
      return var2.endsWith("_ORE") || var1 == Material.ANCIENT_DEBRIS;
   }

   private boolean canKeepMining(ItemStack var1, Material var2) {
      if (var1 != null && var1.getType() == var2) {
         ItemMeta var3 = var1.getItemMeta();
         if (var3 instanceof Damageable && var1.getType().getMaxDurability() > 0) {
            int var4 = var1.getType().getMaxDurability() - ((Damageable)var3).getDamage();
            if (var4 <= 2) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private List<Block> collect(Block var1, Material var2, int var3) {
      ArrayList<Block> var4 = new ArrayList<>();
      HashSet<Block> var5 = new HashSet<>();
      ArrayDeque<Block> var6 = new ArrayDeque<>();
      var6.add(var1);
      var5.add(var1);

      while (!var6.isEmpty() && var4.size() < var3) {
         Block var7 = (Block)var6.poll();
         var4.add(var7);

         for (int var8 = -1; var8 <= 1; var8++) {
            for (int var9 = -1; var9 <= 1; var9++) {
               for (int var10 = -1; var10 <= 1; var10++) {
                  if (var8 != 0 || var9 != 0 || var10 != 0) {
                     Block var11 = var7.getRelative(var8, var9, var10);
                     if (var11.getType() == var2 && var5.add(var11)) {
                        var6.add(var11);
                     }
                  }
               }
            }
         }
      }

      return var4;
   }
}
