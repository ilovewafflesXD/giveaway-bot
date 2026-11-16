package com.wafflemc.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

final class Combat implements Listener {
   private final WaffleCore plugin;
   private final Map<UUID, Long> tags = new HashMap<>();

   Combat(WaffleCore var1) {
      this.plugin = var1;
   }

   boolean tagged(Player var1) {
      if (!this.plugin.module("combat")) {
         return false;
      } else {
         Long var2 = this.tags.get(var1.getUniqueId());
         if (var2 == null) {
            return false;
         } else if (var2 < System.currentTimeMillis()) {
            this.tags.remove(var1.getUniqueId());
            return false;
         } else {
            return true;
         }
      }
   }

   private void tag(Player var1, boolean var2) {
      if (!var1.hasPermission("wafflecore.bypass.combat")) {
         long var3 = this.plugin.getConfig().getLong("combat.seconds", 15L);
         boolean var5 = this.tagged(var1);
         this.tags.put(var1.getUniqueId(), System.currentTimeMillis() + var3 * 1000L);
         if (!var5 && var2) {
            U.send(var1, "&cYou're in combat! Don't log out for &e" + var3 + "s&c.");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent var1) {
      if (this.plugin.module("combat")) {
         if (var1.getEntity() instanceof Player) {
            Player var2 = (Player)var1.getEntity();
            Player var3 = null;
            if (var1.getDamager() instanceof Player) {
               var3 = (Player)var1.getDamager();
            } else if (var1.getDamager() instanceof Projectile) {
               ProjectileSource var4 = ((Projectile)var1.getDamager()).getShooter();
               if (var4 instanceof Player) {
                  var3 = (Player)var4;
               }
            }

            if (var3 != null && !var3.equals(var2)) {
               this.tag(var2, true);
               this.tag(var3, true);
            }
         }
      }
   }

   @EventHandler
   public void onCommand(PlayerCommandPreprocessEvent var1) {
      if (this.plugin.module("combat")) {
         Player var2 = var1.getPlayer();
         if (this.tagged(var2)) {
            String var3 = var1.getMessage().substring(1).toLowerCase(Locale.ROOT).trim();
            String var4 = var3.split("\\s+")[0];
            int var5 = var4.indexOf(58);
            if (var5 >= 0) {
               var4 = var4.substring(var5 + 1);
            }

            for (String var8 : this.plugin.getConfig().getStringList("combat.blocked-commands")) {
               if (var8.equalsIgnoreCase(var4)) {
                  var1.setCancelled(true);
                  U.send(var2, "&cYou can't use &e/" + var4 + "&c while in combat.");
                  return;
               }
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      if (this.plugin.module("combat")) {
         Player var2 = var1.getPlayer();
         if (this.tagged(var2) && !var2.isDead()) {
            this.tags.remove(var2.getUniqueId());
            var2.setHealth(0.0);
            Bukkit.broadcastMessage(
               U.color(this.plugin.getConfig().getString("prefix", "") + "&e" + var2.getName() + "&7 logged out during combat and was slain.")
            );
         }

         this.tags.remove(var2.getUniqueId());
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent var1) {
      this.tags.remove(var1.getEntity().getUniqueId());
   }
}
