package com.wafflemc.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.io.File;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

final class Moderation implements CommandExecutor, TabCompleter, Listener {
   private static final Pattern IP = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());
   private final WaffleCore plugin;
   private final Yml data;
   private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
   private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
   private final Set<UUID> jailed = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Moderation.MuteInfo> muted = new ConcurrentHashMap<>();
   private final Map<UUID, Long> reportCd = new ConcurrentHashMap<>();

   Moderation(WaffleCore var1) {
      this.plugin = var1;
      this.data = var1.store("moderation.yml");
      this.importLegacy();

      for (String var3 : this.data.cfg.getStringList("frozen")) {
         try {
            this.frozen.add(UUID.fromString(var3));
         } catch (IllegalArgumentException var5) {
         }
      }

      this.rebuildBans();
      Bukkit.getScheduler().runTaskTimer(var1.host, () -> {
         if (this.banIdx.version != this.data.version) {
            this.rebuildBans();
         }
      }, 1L, 1L);
      Bukkit.getScheduler().runTaskTimer(var1.host, this::tick, 100L, 100L);
      this.prune();

      for (Player var7 : Bukkit.getOnlinePlayers()) {
         this.loadState(var7);
      }
   }

   private void importLegacy() {
      if (!this.data.cfg.getBoolean("imported")) {
         File var1 = new File(this.plugin.getDataFolder().getParentFile(), "StaffCore");
         File var2 = new File(var1, "punishments.yml");
         int var3 = 0;
         if (var2.isFile()) {
            YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var2);
            ConfigurationSection var5 = var4.getConfigurationSection("punishments");
            if (var5 != null) {
               for (String var7 : var5.getKeys(false)) {
                  ConfigurationSection var8 = var5.getConfigurationSection(var7);
                  if (var8 != null) {
                     for (String var10 : var8.getKeys(false)) {
                        this.data.cfg.set("p." + var7 + "." + var10, var8.get(var10));
                     }

                     var3++;
                  }
               }
            }

            ConfigurationSection var19 = var4.getConfigurationSection("ip-history");
            if (var19 != null) {
               for (String var24 : var19.getKeys(false)) {
                  this.data.cfg.set("ip." + var24 + ".ip", var19.getString(var24 + ".ip"));
                  this.data.cfg.set("ip." + var24 + ".name", var19.getString(var24 + ".name"));
               }
            }

            ConfigurationSection var22 = var4.getConfigurationSection("jails");
            if (var22 != null) {
               for (String var27 : var22.getKeys(false)) {
                  ConfigurationSection var29 = var22.getConfigurationSection(var27);
                  if (var29 != null) {
                     for (String var12 : var29.getKeys(false)) {
                        this.data.cfg.set("jails." + var27 + "." + var12, var29.get(var12));
                     }
                  }
               }
            }
         }

         File var17 = new File(var1, "states.yml");
         int var18 = 0;
         if (var17.isFile()) {
            YamlConfiguration var20 = YamlConfiguration.loadConfiguration(var17);
            ConfigurationSection var23 = var20.getConfigurationSection("homes");
            Yml var26 = this.plugin.tp.homesStore();
            if (var23 != null) {
               for (String var30 : var23.getKeys(false)) {
                  ConfigurationSection var31 = var23.getConfigurationSection(var30);
                  if (var31 != null) {
                     for (String var13 : var31.getKeys(false)) {
                        ConfigurationSection var14 = var31.getConfigurationSection(var13);
                        if (var14 != null && !var26.cfg.contains(var30 + "." + var13)) {
                           for (String var16 : var14.getKeys(false)) {
                              var26.cfg.set(var30 + "." + var13 + "." + var16, var14.get(var16));
                           }

                           var18++;
                        }
                     }
                  }
               }
            }

            if (var18 > 0) {
               var26.mark();
            }
         }

         this.data.cfg.set("imported", true);
         this.data.mark();
         this.data.save();
         this.plugin.getLogger().info("Imported StaffCore data: " + var3 + " punishments, " + var18 + " homes.");
      }
   }

   private void saveFrozen() {
      ArrayList var1 = new ArrayList();

      for (UUID var3 : this.frozen) {
         var1.add(var3.toString());
      }

      synchronized (this.data) {
         this.data.cfg.set("frozen", var1);
         this.data.mark();
      }
   }

   private boolean can(CommandSender var1, String var2) {
      return U.can(var1, "mod", var2);
   }

   private boolean bypass(Player var1, String var2) {
      return var1.hasPermission("wafflecore.bypass." + var2) || (U.legacy() && var1.hasPermission("staffcore.bypass." + var2));
   }

   private boolean isStaff(CommandSender var1) {
      return var1.hasPermission("wafflecore.staff") || (U.legacy() && var1.hasPermission("staffcore.staff")) || var1.hasPermission("wafflecore.admin");
   }

   private String ipOf(Player var1) {
      InetSocketAddress var2 = var1.getAddress();
      return var2 != null && var2.getAddress() != null ? var2.getAddress().getHostAddress() : null;
   }

   private Moderation.PType typeOf(ConfigurationSection var1) {
      try {
         return Moderation.PType.valueOf(var1.getString("type", ""));
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private synchronized String record(Moderation.PType var1, UUID var2, String var3, String var4, String var5, String var6, long var7, String var9) {
      String var10 = UUID.randomUUID().toString();
      String var11 = "p." + var10;
      long var12 = System.currentTimeMillis();
      if (var2 != null) {
         this.data.cfg.set(var11 + ".targetUUID", var2.toString());
      }

      this.data.cfg.set(var11 + ".targetName", var3);
      this.data.cfg.set(var11 + ".targetIp", var4 == null ? "" : var4);
      this.data.cfg.set(var11 + ".staffName", var5);
      this.data.cfg.set(var11 + ".type", var1.name());
      this.data.cfg.set(var11 + ".reason", var6);
      this.data.cfg.set(var11 + ".timestamp", var12);
      this.data.cfg.set(var11 + ".duration", var7);
      this.data.cfg.set(var11 + ".expireTime", var7 < 0L ? -1L : var12 + var7 * 1000L);
      this.data.cfg.set(var11 + ".active", var1 != Moderation.PType.KICK);
      if (var9 != null) {
         this.data.cfg.set(var11 + ".jail", var9);
      }

      this.data.mark();
      return var10;
   }

   /** Immutable snapshot of active bans. Built on the main thread, read by the async login thread. */
   private static final class BanIndex {
      final int version;
      final Map<String, Moderation.BanHit> byUuid;
      final Map<String, Moderation.BanHit> byIp;
      final Map<String, Moderation.BanHit> byName;

      BanIndex(int var1, Map<String, Moderation.BanHit> var2, Map<String, Moderation.BanHit> var3, Map<String, Moderation.BanHit> var4) {
         this.version = var1;
         this.byUuid = var2;
         this.byIp = var3;
         this.byName = var4;
      }
   }

   private record BanHit(int order, ConfigurationSection section) {
   }

   private volatile Moderation.BanIndex banIdx = new Moderation.BanIndex(-1, Map.of(), Map.of(), Map.of());

   private void rebuildBans() {
      int var1 = this.data.version;
      HashMap<String, Moderation.BanHit> var2 = new HashMap<>();
      HashMap<String, Moderation.BanHit> var3 = new HashMap<>();
      HashMap<String, Moderation.BanHit> var4 = new HashMap<>();
      ConfigurationSection var5 = this.data.cfg.getConfigurationSection("p");
      int var6 = 0;
      if (var5 != null) {
         for (String var8 : var5.getKeys(false)) {
            ConfigurationSection var9 = var5.getConfigurationSection(var8);
            var6++;
            if (var9 != null && var9.getBoolean("active")) {
               Moderation.PType var10 = this.typeOf(var9);
               if (var10 == Moderation.PType.BAN || var10 == Moderation.PType.TEMPBAN || var10 == Moderation.PType.IP_BAN || var10 == Moderation.PType.TEMP_IP_BAN) {
                  MemoryConfiguration var11 = new MemoryConfiguration();

                  for (Entry<String, Object> var13 : var9.getValues(false).entrySet()) {
                     var11.set(var13.getKey(), var13.getValue());
                  }

                  Moderation.BanHit var14 = new Moderation.BanHit(var6, var11);
                  if (var10.ipBan()) {
                     String var15 = var9.getString("targetIp");
                     if (var15 != null && !var15.isEmpty()) {
                        var3.putIfAbsent(var15, var14);
                     }
                  } else {
                     String var16 = var9.getString("targetUUID");
                     if (var16 != null) {
                        var2.putIfAbsent(var16, var14);
                     }

                     String var17 = var9.getString("targetName");
                     if (var17 != null) {
                        var4.putIfAbsent(var17.toLowerCase(Locale.ROOT), var14);
                     }
                  }
               }
            }
         }
      }

      this.banIdx = new Moderation.BanIndex(var1, var2, var3, var4);
   }

   /** Thread-safe ban lookup against the snapshot. Safe to call from the async login thread. */
   private ConfigurationSection lookupBan(String var1, String var2, String var3) {
      Moderation.BanIndex var4 = this.banIdx;
      long var5 = System.currentTimeMillis();
      Moderation.BanHit var7 = null;
      if (var1 != null) {
         var7 = best(var7, var4.byUuid.get(var1), var5);
      }

      if (var2 != null && !var2.isEmpty()) {
         var7 = best(var7, var4.byIp.get(var2), var5);
      }

      if (var3 != null && var1 == null) {
         var7 = best(var7, var4.byName.get(var3.toLowerCase(Locale.ROOT)), var5);
      }

      return var7 == null ? null : var7.section();
   }

   private static Moderation.BanHit best(Moderation.BanHit var0, Moderation.BanHit var1, long var2) {
      if (var1 == null) {
         return var0;
      } else {
         long var4 = var1.section().getLong("expireTime", -1L);
         if (var4 > 0L && var4 < var2) {
            return var0;
         } else {
            return var0 != null && var0.order() <= var1.order() ? var0 : var1;
         }
      }
   }

   /** Drops finished punishments older than moderation.retention-days (0 keeps everything). */
   private void prune() {
      int var1 = this.plugin.getConfig().getInt("moderation.retention-days", 0);
      ConfigurationSection var2 = this.data.cfg.getConfigurationSection("p");
      if (var1 > 0 && var2 != null) {
         long var3 = System.currentTimeMillis() - (long)var1 * 86400000L;
         int var5 = 0;

         for (String var7 : new ArrayList<>(var2.getKeys(false))) {
            ConfigurationSection var8 = var2.getConfigurationSection(var7);
            if (var8 != null && !var8.getBoolean("active") && var8.getLong("timestamp") > 0L && var8.getLong("timestamp") < var3) {
               var2.set(var7, null);
               var5++;
            }
         }

         if (var5 > 0) {
            this.data.mark();
            this.plugin.getLogger().info("Pruned " + var5 + " old punishment records (retention " + var1 + " days).");
         }
      }
   }

   private synchronized ConfigurationSection find(String var1, String var2, String var3, Moderation.PType... var4) {
      ConfigurationSection var5 = this.data.cfg.getConfigurationSection("p");
      if (var5 == null) {
         return null;
      } else {
         long var6 = System.currentTimeMillis();

         for (String var9 : var5.getKeys(false)) {
            ConfigurationSection var10 = var5.getConfigurationSection(var9);
            if (var10 != null && var10.getBoolean("active")) {
               Moderation.PType var11 = this.typeOf(var10);
               if (var11 != null) {
                  boolean var12 = false;

                  for (Moderation.PType var16 : var4) {
                     if (var16 == var11) {
                        var12 = true;
                     }
                  }

                  if (var12) {
                     long var17 = var10.getLong("expireTime", -1L);
                     if (var17 > 0L && var17 < var6) {
                        var10.set("active", false);
                        this.data.mark();
                     } else if (var11.ipBan()) {
                        if (var2 != null && !var2.isEmpty() && var2.equals(var10.getString("targetIp"))) {
                           return var10;
                        }
                     } else {
                        if (var1 != null && var1.equals(var10.getString("targetUUID"))) {
                           return var10;
                        }

                        if (var3 != null && var1 == null && var3.equalsIgnoreCase(var10.getString("targetName"))) {
                           return var10;
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private synchronized int deactivate(String var1, String var2, Moderation.PType... var3) {
      ConfigurationSection var4 = this.data.cfg.getConfigurationSection("p");
      if (var4 == null) {
         return 0;
      } else {
         int var5 = 0;

         for (String var7 : var4.getKeys(false)) {
            ConfigurationSection var8 = var4.getConfigurationSection(var7);
            if (var8 != null && var8.getBoolean("active")) {
               Moderation.PType var9 = this.typeOf(var8);
               boolean var10 = false;

               for (Moderation.PType var14 : var3) {
                  if (var14 == var9) {
                     var10 = true;
                  }
               }

               if (var10) {
                  boolean var15 = var1 != null && var1.equalsIgnoreCase(var8.getString("targetName"))
                     || var2 != null && var2.equals(var8.getString("targetIp"));
                  if (var15) {
                     var8.set("active", false);
                     var5++;
                  }
               }
            }
         }

         if (var5 > 0) {
            this.data.mark();
         }

         return var5;
      }
   }

   private synchronized List<ConfigurationSection> history(String var1, String var2) {
      ArrayList<ConfigurationSection> var3 = new ArrayList<>();
      ConfigurationSection var4 = this.data.cfg.getConfigurationSection("p");
      if (var4 == null) {
         return var3;
      } else {
         for (String var6 : var4.getKeys(false)) {
            ConfigurationSection var7 = var4.getConfigurationSection(var6);
            if (var7 != null) {
               boolean var8 = var1 != null && var1.equals(var7.getString("targetUUID")) || var2 != null && var2.equalsIgnoreCase(var7.getString("targetName"));
               if (var8) {
                  var3.add(var7);
               }
            }
         }

         var3.sort((var0, var1x) -> Long.compare(var1x.getLong("timestamp"), var0.getLong("timestamp")));
         return var3;
      }
   }

   private synchronized Moderation.Target resolve(String var1) {
      Player var2 = Bukkit.getPlayerExact(var1);
      Moderation.Target var3 = new Moderation.Target();
      if (var2 != null) {
         var3.uuid = var2.getUniqueId();
         var3.name = var2.getName();
         var3.ip = this.ipOf(var2);
         var3.online = var2;
         return var3;
      } else {
         ConfigurationSection ips = this.data.cfg.getConfigurationSection("ip");
         String matchedKey = null;
         if (ips != null) {
            for (String key : ips.getKeys(false)) {
               if (var1.equalsIgnoreCase(ips.getString(key + ".name"))) {
                  try {
                     var3.uuid = UUID.fromString(key);
                     matchedKey = key;
                     break;
                  } catch (IllegalArgumentException var8) {
                  }
               }
            }
         }

         if (matchedKey == null) {
            OfflinePlayer var9 = Bukkit.getOfflinePlayerIfCached(var1);
            if (var9 != null) {
               var3.uuid = var9.getUniqueId();
               var3.name = var9.getName() == null ? var1 : var9.getName();
               var3.ip = this.data.cfg.getString("ip." + var3.uuid + ".ip");
               return var3;
            }

            return null;
         }

         var3.name = ips.getString(matchedKey + ".name");
         var3.ip = ips.getString(matchedKey + ".ip");
         return var3;
      }
   }

   private String remaining(ConfigurationSection var1) {
      long var2 = var1.getLong("expireTime", -1L);
      return var2 < 0L ? "never" : U.duration(Math.max(1L, (var2 - System.currentTimeMillis()) / 1000L));
   }

   private String banScreen(ConfigurationSection var1) {
      Moderation.PType var2 = this.typeOf(var1);
      String var3 = this.plugin.getConfig().getString("moderation.appeal", "discord.gg/BvFHUxveqt");
      String var4 = var2 != null && var2.ipBan() ? "&c&lYOUR IP ADDRESS IS BANNED" : "&c&lYOU ARE BANNED";
      ArrayList<String> var5 = new ArrayList<>();
      var5.add("&6&lWaffle&e&lMC");
      var5.add("");
      var5.add(var4);
      var5.add("");
      var5.add("&7Reason: &f" + var1.getString("reason", "No reason specified"));
      var5.add("&7Banned by: &f" + var1.getString("staffName", "?"));
      var5.add("&7Expires in: &e" + this.remaining(var1));
      var5.add("");
      var5.add("&7Appeal at: &e" + var3);
      return U.color(String.join("\n", var5));
   }

   private void kickWith(Player var1, String var2, String var3) {
      var1.kickPlayer(U.color("&6&lWaffle&e&lMC\n\n&c&lYOU HAVE BEEN KICKED\n\n&7Reason: &f" + var2 + "\n&7Kicked by: &f" + var3));
   }

   private void announce(Moderation.PType var1, String var2, String var3, String var4, long var5, boolean var7) {
      String var8 = switch (var1) {
         case BAN -> "permanently banned";
         case TEMPBAN -> "banned";
         case IP_BAN -> "permanently IP banned";
         case TEMP_IP_BAN -> "IP banned";
         case MUTE -> "permanently muted";
         case TEMP_MUTE -> "muted";
         case JAIL -> "permanently jailed";
         case TEMP_JAIL -> "jailed";
         case WARN -> "warned";
         default -> "kicked";
      };
      String var9 = var1.temp() ? " for &e" + U.duration(var5) : "";
      String var10 = "&c&lPUNISH &8» &f" + var2 + " &7was " + var8 + var9 + " &7by &f" + var3 + " &7for: &e" + var4;
      if (var7) {
         var10 = "&8[&7Silent&8] " + var10;
      }

      String var11 = U.color(var10);
      Bukkit.getConsoleSender().sendMessage(var11);

      for (Player var13 : Bukkit.getOnlinePlayers()) {
         if (!var7 || this.isStaff(var13)) {
            var13.sendMessage(var11);
         }
      }
   }

   private boolean execute(CommandSender var1, Moderation.PType var2, Moderation.Target var3, String var4, long var5, String var7, boolean var8) {
      if (var1 instanceof Player && ((Player)var1).getUniqueId().equals(var3.uuid)) {
         U.send(var1, "&cYou can't punish yourself.");
         return false;
      } else if (var3.online != null && var1 instanceof Player && this.bypass(var3.online, var2.bypass())) {
         U.send(var1, "&cYou can't punish &e" + var3.name + "&c, they have bypass permissions.");
         return false;
      } else if (var2.jail() && !this.jailNames().contains(var7)) {
         U.send(var1, "&cJail &e" + var7 + "&c doesn't exist. Create it with &e/setjail " + var7 + "&c.");
         return false;
      } else if (!var2.ipBan() || var3.ip != null && !var3.ip.isEmpty()) {
         String var9 = var1.getName();
         this.record(var2, var3.uuid, var3.name, var3.ip, var9, var4, var5, var7);
         long var10 = var5 < 0L ? Long.MAX_VALUE : System.currentTimeMillis() + var5 * 1000L;
         if (var2.ban()) {
            if (var3.online != null) {
               this.kickBanned(var3.online, var2);
            }
         } else if (var2.ipBan()) {
            for (Player var13 : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
               if (var3.ip.equals(this.ipOf(var13))) {
                  this.kickBanned(var13, var2);
               }
            }
         } else if (var2.mute()) {
            if (var3.uuid != null) {
               this.muted.put(var3.uuid, new Moderation.MuteInfo(var10, var4));
            }

            if (var3.online != null) {
               U.send(var3.online, "&cYou have been muted" + (var5 < 0L ? "" : " for &e" + U.duration(var5)) + "&c. Reason: &f" + var4);
            }
         } else if (var2.jail()) {
            if (var3.online != null) {
               this.sendToJail(var3.online, var7, var4, var9, var5);
            }

            if (var3.uuid != null) {
               this.jailed.add(var3.uuid);
            }
         } else if (var2 == Moderation.PType.WARN) {
            if (var3.online != null) {
               U.raw(var3.online, "&c&m                                                  ");
               U.raw(var3.online, "&c&lYOU HAVE RECEIVED A WARNING");
               U.raw(var3.online, "&7Reason: &f" + var4);
               U.raw(var3.online, "&7Warned by: &f" + var9);
               U.raw(var3.online, "&c&m                                                  ");
            }
         } else if (var3.online != null) {
            this.kickWith(var3.online, var4, var9);
         }

         this.announce(var2, var3.name, var9, var4, var5, var8);
         if (var2 == Moderation.PType.WARN) {
            this.autoBan(var1, var3);
         }

         return true;
      } else {
         U.send(var1, "&cNo IP address is known for &e" + var3.name + "&c.");
         return false;
      }
   }

   private void autoBan(CommandSender var1, Moderation.Target var2) {
      int var3 = this.plugin.getConfig().getInt("moderation.max-warnings", 3);
      if (var3 > 0) {
         int var4 = 0;

         for (ConfigurationSection var6 : this.history(var2.uuid == null ? null : var2.uuid.toString(), var2.name)) {
            if (this.typeOf(var6) == Moderation.PType.WARN && var6.getBoolean("active")) {
               var4++;
            }
         }

         if (var4 >= var3) {
            this.record(Moderation.PType.BAN, var2.uuid, var2.name, var2.ip, "WaffleCore", "Reached " + var3 + " warnings", -1L, null);
            if (var2.online != null) {
               this.kickBanned(var2.online, Moderation.PType.BAN);
            }

            this.announce(Moderation.PType.BAN, var2.name, "WaffleCore", "Reached " + var3 + " warnings", -1L, false);
         }
      }
   }

   private void kickBanned(Player var1, Moderation.PType var2) {
      ConfigurationSection var3 = this.find(
         var1.getUniqueId().toString(),
         this.ipOf(var1),
         var1.getName(),
         Moderation.PType.BAN,
         Moderation.PType.TEMPBAN,
         Moderation.PType.IP_BAN,
         Moderation.PType.TEMP_IP_BAN
      );
      if (var3 != null) {
         var1.kickPlayer(this.banScreen(var3));
      } else {
         var1.kickPlayer(U.color("&c&lYou are banned."));
      }
   }

   private List<String> jailNames() {
      synchronized (this.data) {
         ConfigurationSection var2 = this.data.cfg.getConfigurationSection("jails");
         return var2 == null ? new ArrayList<>() : new ArrayList<>(var2.getKeys(false));
      }
   }

   private Location jailLoc(String var1) {
      synchronized (this.data) {
         return Yml.readLoc(this.data.cfg.getConfigurationSection("jails." + var1));
      }
   }

   private void sendToJail(Player var1, String var2, String var3, String var4, long var5) {
      Location var7 = this.jailLoc(var2);
      if (var7 != null) {
         synchronized (this.data) {
            if (!this.data.cfg.contains("jailreturn." + var1.getUniqueId())) {
               Yml.writeLoc(this.data.cfg.createSection("jailreturn." + var1.getUniqueId()), var1.getLocation());
               this.data.mark();
            }
         }

         var1.teleportAsync(var7);
         U.raw(var1, "&c&m                                                  ");
         U.raw(var1, "&c&lYOU HAVE BEEN JAILED");
         U.raw(var1, "&7Jail: &e" + var2);
         U.raw(var1, "&7Reason: &f" + var3);
         if (var5 >= 0L) {
            U.raw(var1, "&7Time: &e" + U.duration(var5));
         }

         U.raw(var1, "&7Jailed by: &f" + var4);
         U.raw(var1, "&c&m                                                  ");
      }
   }

   private void release(Player var1) {
      this.jailed.remove(var1.getUniqueId());
      Location var2;
      synchronized (this.data) {
         var2 = Yml.readLoc(this.data.cfg.getConfigurationSection("jailreturn." + var1.getUniqueId()));
         this.data.cfg.set("jailreturn." + var1.getUniqueId(), null);
         this.data.mark();
      }

      if (var2 == null) {
         var2 = this.plugin.tp.spawnLocation();
      }

      if (var2 != null && var2.getWorld() != null) {
         var1.teleportAsync(var2);
      }

      U.send(var1, "&aYou have been released from jail.");
   }

   private void loadState(Player var1) {
      UUID var2 = var1.getUniqueId();
      ConfigurationSection var3 = this.find(var2.toString(), null, var1.getName(), Moderation.PType.MUTE, Moderation.PType.TEMP_MUTE);
      if (var3 != null) {
         long var4 = var3.getLong("expireTime", -1L);
         this.muted.put(var2, new Moderation.MuteInfo(var4 < 0L ? Long.MAX_VALUE : var4, var3.getString("reason", "No reason specified")));
      } else {
         this.muted.remove(var2);
      }

      ConfigurationSection var9 = this.find(var2.toString(), null, var1.getName(), Moderation.PType.JAIL, Moderation.PType.TEMP_JAIL);
      if (var9 != null) {
         this.jailed.add(var2);
         String var5 = var9.getString("jail", "");
         Location var6 = this.jailLoc(var5);
         if (var6 != null) {
            long var7 = var9.getLong("expireTime", -1L);
            Bukkit.getScheduler().runTaskLater(this.plugin.host, () -> {
               if (var1.isOnline()) {
                  var1.teleportAsync(var6);
               }
            }, 10L);
            U.send(
               var1,
               "&cYou are still jailed. " + (var7 < 0L ? "" : "Time left: &e" + U.duration(Math.max(1L, (var7 - System.currentTimeMillis()) / 1000L)) + "&c.")
            );
         }
      } else {
         this.jailed.remove(var2);
      }
   }

   private void tick() {
      long var1 = System.currentTimeMillis();

      for (Player var4 : Bukkit.getOnlinePlayers()) {
         UUID var5 = var4.getUniqueId();
         Moderation.MuteInfo var6 = this.muted.get(var5);
         if (var6 != null && var6.until <= var1) {
            this.muted.remove(var5);
            U.send(var4, "&aYou are no longer muted.");
         }

         if (this.jailed.contains(var5)) {
            ConfigurationSection var7 = this.find(var5.toString(), null, var4.getName(), Moderation.PType.JAIL, Moderation.PType.TEMP_JAIL);
            if (var7 == null) {
               this.release(var4);
               continue;
            }

            Location var8 = this.jailLoc(var7.getString("jail", ""));
            int var9 = this.plugin.getConfig().getInt("moderation.jail-radius", 12);
            if (var8 != null && (!var8.getWorld().equals(var4.getWorld()) || var8.distanceSquared(var4.getLocation()) > (double)(var9 * var9))) {
               var4.teleportAsync(var8);
            }
         }

         if (this.frozen.contains(var5)) {
            var4.sendTitle(U.color("&b&lYOU ARE FROZEN"), U.color("&7Do not log out. Wait for staff."), 0, 120, 10);
         }
      }
   }

   @EventHandler
   public void onPreLogin(AsyncPlayerPreLoginEvent var1) {
      ConfigurationSection var2 = this.lookupBan(
         var1.getUniqueId().toString(), var1.getAddress() == null ? null : var1.getAddress().getHostAddress(), var1.getName()
      );
      if (var2 != null) {
         var1.disallow(Result.KICK_BANNED, this.banScreen(var2));
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent var1) {
      Player var2 = var1.getPlayer();
      String var3 = this.ipOf(var2);
      synchronized (this.data) {
         if (var3 != null) {
            this.data.cfg.set("ip." + var2.getUniqueId() + ".ip", var3);
         }

         this.data.cfg.set("ip." + var2.getUniqueId() + ".name", var2.getName());
         this.data.mark();
      }

      this.loadState(var2);
      if (this.frozen.contains(var2.getUniqueId())) {
         U.send(var2, "&bYou are frozen. Wait for a staff member.");
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      Player var2 = var1.getPlayer();
      this.staffChat.remove(var2.getUniqueId());
      this.muted.remove(var2.getUniqueId());
      this.jailed.remove(var2.getUniqueId());
      if (this.frozen.contains(var2.getUniqueId())) {
         for (Player var4 : Bukkit.getOnlinePlayers()) {
            if (var4.hasPermission("wafflecore.freeze.alerts") || (U.legacy() && var4.hasPermission("staffcore.freeze.alerts"))) {
               U.send(var4, "&c" + var2.getName() + "&7 left the game while frozen.");
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent var1) {
      Player var2 = var1.getPlayer();
      Moderation.MuteInfo var3 = this.muted.get(var2.getUniqueId());
      if (var3 != null) {
         if (var3.until > System.currentTimeMillis()) {
            var1.setCancelled(true);
            String var5 = var3.until == Long.MAX_VALUE ? "permanent" : U.duration((var3.until - System.currentTimeMillis()) / 1000L + 1L);
            Bukkit.getScheduler().runTask(this.plugin.host, () -> U.send(var2, "&cYou are muted. &7Reason: &f" + var3.reason + " &7Time left: &e" + var5));
            return;
         }

         this.muted.remove(var2.getUniqueId());
      }

      if (this.staffChat.contains(var2.getUniqueId()) && this.isStaff(var2)) {
         var1.setCancelled(true);
         String var4 = PlainTextComponentSerializer.plainText().serialize(var1.message());
         Bukkit.getScheduler().runTask(this.plugin.host, () -> this.sendStaffChat(var2.getName(), var4));
      }
   }

   private void sendStaffChat(String var1, String var2) {
      String var3 = U.color("&8[&cStaff&8] &7" + var1 + "&8: &f" + var2);
      Bukkit.getConsoleSender().sendMessage(var3);

      for (Player var5 : Bukkit.getOnlinePlayers()) {
         if (var5.hasPermission("wafflecore.staff.chat") || (U.legacy() && var5.hasPermission("staffcore.staffchat")) || var5.hasPermission("wafflecore.admin")) {
            var5.sendMessage(var3);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onCommandUse(PlayerCommandPreprocessEvent var1) {
      Player var2 = var1.getPlayer();
      String var3 = var1.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
      int var4 = var3.indexOf(58);
      if (var4 >= 0) {
         var3 = var3.substring(var4 + 1);
      }

      if (this.frozen.contains(var2.getUniqueId())) {
         var1.setCancelled(true);
         U.send(var2, "&cYou can't use commands while frozen.");
      } else if (this.jailed.contains(var2.getUniqueId())) {
         var1.setCancelled(true);
         U.send(var2, "&cYou can't use commands while jailed.");
      } else {
         Moderation.MuteInfo var5 = this.muted.get(var2.getUniqueId());
         if (var5 != null
            && var5.until > System.currentTimeMillis()
            && this.plugin.getConfig().getStringList("moderation.muted-blocked-commands").contains(var3)) {
            var1.setCancelled(true);
            U.send(var2, "&cYou are muted.");
         }
      }
   }

   private boolean restricted(Player var1) {
      return this.frozen.contains(var1.getUniqueId()) || this.jailed.contains(var1.getUniqueId());
   }

   @EventHandler
   public void onMove(PlayerMoveEvent var1) {
      if (this.frozen.contains(var1.getPlayer().getUniqueId())) {
         Location var2 = var1.getFrom();
         Location var3 = var1.getTo();
         if (var3 != null) {
            if (var2.getX() != var3.getX() || var2.getY() != var3.getY() || var2.getZ() != var3.getZ()) {
               Location var4 = var2.clone();
               var4.setYaw(var3.getYaw());
               var4.setPitch(var3.getPitch());
               var1.setTo(var4);
            }
         }
      }
   }

   @EventHandler
   public void onBreak(BlockBreakEvent var1) {
      if (this.restricted(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onPlace(BlockPlaceEvent var1) {
      if (this.restricted(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent var1) {
      if (this.restricted(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onDrop(PlayerDropItemEvent var1) {
      if (this.restricted(var1.getPlayer())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onDamage(EntityDamageEvent var1) {
      if (var1.getEntity() instanceof Player && this.frozen.contains(var1.getEntity().getUniqueId())) {
         var1.setCancelled(true);
      }
   }

   @EventHandler
   public void onHit(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player && this.restricted((Player)var1.getDamager())) {
         var1.setCancelled(true);
      }
   }

   private boolean privateIp(String var1) {
      if (var1 == null) {
         return true;
      } else if (var1.startsWith("192.168.") || var1.startsWith("10.") || var1.startsWith("127.")) {
         return true;
      } else if (!var1.startsWith("172.")) {
         return false;
      } else {
         try {
            int var2 = Integer.parseInt(var1.split("\\.")[1]);
            return var2 >= 16 && var2 <= 31;
         } catch (Exception var3) {
            return true;
         }
      }
   }

   private List<String> altsOf(Moderation.Target var1) {
      ArrayList var2 = new ArrayList();
      if (var1.ip != null && !var1.ip.isEmpty() && !this.privateIp(var1.ip)) {
         synchronized (this.data) {
            ConfigurationSection var4 = this.data.cfg.getConfigurationSection("ip");
            if (var4 == null) {
               return var2;
            } else {
               for (String var6 : var4.getKeys(false)) {
                  if ((var1.uuid == null || !var6.equals(var1.uuid.toString())) && var1.ip.equals(var4.getString(var6 + ".ip"))) {
                     String var7 = var4.getString(var6 + ".name", var6);
                     boolean var8 = this.find(var6, null, var7, Moderation.PType.BAN, Moderation.PType.TEMPBAN) != null;
                     boolean var9 = Bukkit.getPlayerExact(var7) != null;
                     var2.add((var8 ? "&c" : "&f") + var7 + (var8 ? " &8(banned)" : (var9 ? " &a(online)" : "")));
                  }
               }

               return var2;
            }
         }
      } else {
         return var2;
      }
   }

   private boolean alts(CommandSender var1, String[] var2) {
      if (!this.can(var1, "alts")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /alts <player>");
         return true;
      } else {
         Moderation.Target var3 = this.resolve(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cPlayer &e" + var2[0] + "&c was not found or has never joined.");
            return true;
         } else {
            List<String> var4 = this.altsOf(var3);
            if (var4.isEmpty()) {
               U.send(var1, "&e" + var3.name + "&7 has no known alt accounts.");
               return true;
            } else {
               U.send(var1, "Accounts sharing an IP with &e" + var3.name + "&7:");

               for (String var6 : var4) {
                  U.raw(var1, "  &8- " + var6);
               }

               return true;
            }
         }
      }
   }

   private List<String> notesOf(String var1) {
      synchronized (this.data) {
         return new ArrayList<>(this.data.cfg.getStringList("notes." + var1));
      }
   }

   private boolean notes(CommandSender var1, String[] var2) {
      if (!this.can(var1, "notes")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /notes <player> [add <text> | remove <number>]");
         return true;
      } else {
         Moderation.Target var3 = this.resolve(var2[0]);
         if (var3 != null && var3.uuid != null) {
            String var4 = "notes." + var3.uuid;
            if (var2.length >= 3 && var2[1].equalsIgnoreCase("add")) {
               String var14 = String.join(" ", Arrays.copyOfRange(var2, 2, var2.length));
               List var17 = this.notesOf(var3.uuid.toString());
               var17.add(DATE.format(Instant.now()) + "|" + var1.getName() + "|" + var14);
               synchronized (this.data) {
                  this.data.cfg.set(var4, var17);
                  this.data.mark();
               }

               U.send(var1, "Note added to &e" + var3.name + "&7.");
               return true;
            } else if (var2.length >= 3 && var2[1].equalsIgnoreCase("remove")) {
               List var13 = this.notesOf(var3.uuid.toString());

               try {
                  int var15 = Integer.parseInt(var2[2]) - 1;
                  if (var15 < 0 || var15 >= var13.size()) {
                     throw new NumberFormatException();
                  }

                  var13.remove(var15);
               } catch (NumberFormatException var12) {
                  U.send(var1, "&cInvalid note number.");
                  return true;
               }

               synchronized (this.data) {
                  this.data.cfg.set(var4, var13.isEmpty() ? null : var13);
                  this.data.mark();
               }

               U.send(var1, "Note removed.");
               return true;
            } else {
               List<String> var5 = this.notesOf(var3.uuid.toString());
               if (var5.isEmpty()) {
                  U.send(var1, "&e" + var3.name + "&7 has no notes. Add one with &e/notes " + var3.name + " add <text>&7.");
                  return true;
               } else {
                  U.send(var1, "Notes for &e" + var3.name + "&7:");
                  int var6 = 1;

                  for (String var8 : var5) {
                     String[] var9 = var8.split("\\|", 3);
                     if (var9.length == 3) {
                        U.raw(var1, "  &7#" + var6 + " &8[" + var9[0] + "] &e" + var9[1] + "&8: &f" + var9[2]);
                     } else {
                        U.raw(var1, "  &7#" + var6 + " &f" + var8);
                     }

                     var6++;
                  }

                  return true;
               }
            }
         } else {
            U.send(var1, "&cPlayer &e" + var2[0] + "&c was not found or has never joined.");
            return true;
         }
      }
   }

   private boolean staffList(CommandSender var1) {
      ArrayList var2 = new ArrayList();

      for (Player var4 : Bukkit.getOnlinePlayers()) {
         if (this.isStaff(var4) && (!(var1 instanceof Player) || ((Player)var1).canSee(var4))) {
            var2.add(var4.getName());
         }
      }

      if (var2.isEmpty()) {
         U.send(var1, "No staff are online right now.");
      } else {
         U.send(var1, "Staff online (&e" + var2.size() + "&7): &f" + String.join("&7, &f", var2));
      }

      return true;
   }

   private boolean report(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (var2.length < 2) {
         U.send(var1, "&cUsage: /report <player> <reason>");
         return true;
      } else {
         Moderation.Target var4 = this.resolve(var2[0]);
         if (var4 == null) {
            U.send(var1, "&cPlayer &e" + var2[0] + "&c was not found.");
            return true;
         } else if (var3.getUniqueId().equals(var4.uuid)) {
            U.send(var1, "&cYou can't report yourself.");
            return true;
         } else {
            long var5 = this.plugin.getConfig().getLong("moderation.report-cooldown", 60L) * 1000L;
            Long var7 = this.reportCd.get(var3.getUniqueId());
            long var8 = System.currentTimeMillis();
            if (var7 != null && var8 - var7 < var5 && !this.isStaff(var3)) {
               U.send(var1, "&cWait &e" + U.duration((var5 - (var8 - var7)) / 1000L + 1L) + "&c before sending another report.");
               return true;
            } else {
               this.reportCd.put(var3.getUniqueId(), var8);
               String var10 = String.join(" ", Arrays.copyOfRange(var2, 1, var2.length));
               String var11 = String.valueOf(var8);
               synchronized (this.data) {
                  String var13 = "reports." + var11;
                  this.data.cfg.set(var13 + ".reporter", var3.getUniqueId().toString());
                  this.data.cfg.set(var13 + ".reporterName", var3.getName());
                  this.data.cfg.set(var13 + ".target", var4.name);
                  this.data.cfg.set(var13 + ".reason", var10);
                  this.data.cfg.set(var13 + ".time", var8);
                  this.data.cfg.set(var13 + ".open", true);
                  this.data.mark();
               }

               U.send(var1, "Report sent. Staff will look into it.");
               String var16 = U.color("&c&lREPORT &8» &f" + var3.getName() + " &7reported &f" + var4.name + "&7: &e" + var10 + " &8(/reports)");
               Bukkit.getConsoleSender().sendMessage(var16);

               for (Player var14 : Bukkit.getOnlinePlayers()) {
                  if (this.isStaff(var14)) {
                     var14.sendMessage(var16);
                  }
               }

               return true;
            }
         }
      }
   }

   private boolean reports(CommandSender var1) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "reports")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else {
         this.openReports((Player)var1);
         return true;
      }
   }

   private void openReports(Player var1) {
      ArrayList<ConfigurationSection> var2 = new ArrayList<>();
      synchronized (this.data) {
         ConfigurationSection var4 = this.data.cfg.getConfigurationSection("reports");
         if (var4 != null) {
            for (String var6 : var4.getKeys(false)) {
               ConfigurationSection var7 = var4.getConfigurationSection(var6);
               if (var7 != null && var7.getBoolean("open")) {
                  var2.add(var7);
               }
            }
         }
      }

      var2.sort((var0, var1x) -> Long.compare(var1x.getLong("time"), var0.getLong("time")));
      int var12 = Gui.rowsFor(Math.min(35, var2.size()));
      Gui var13 = new Gui("reports");
      Inventory var14 = var13.make(var12 * 9, "&6&lWaffle&e&lMC &8» &7Reports &8(" + var2.size() + ")");
      Gui.border(var14, Material.ORANGE_STAINED_GLASS_PANE);
      List var15 = Gui.inner(var12);

      for (int var16 = 0; var16 < var15.size() && var16 < var2.size(); var16++) {
         ConfigurationSection var8 = (ConfigurationSection)var2.get(var16);
         String var9 = var8.getString("target", "?");
         boolean var10 = Bukkit.getPlayerExact(var9) != null;
         var14.setItem(
            (Integer)var15.get(var16),
            Gui.item(
               Material.WRITABLE_BOOK,
               "&c&l" + var9,
               "&7Reported by: &f" + var8.getString("reporterName", "?"),
               "&7Reason: &f" + var8.getString("reason", "?"),
               "&7When: &f" + DATE.format(Instant.ofEpochMilli(var8.getLong("time"))),
               "&7Target: " + (var10 ? "&aonline" : "&coffline"),
               "",
               "&eLeft-click &7open check menu",
               "&eShift-click &7teleport to target",
               "&cRight-click &7mark resolved"
            )
         );
         var13.at.put((Integer)var15.get(var16), String.valueOf(var8.getLong("time")));
      }

      if (var2.isEmpty()) {
         var14.setItem(var12 * 9 / 2, Gui.item(Material.LIME_DYE, "&aNo open reports", "&7Everything is quiet."));
      }

      var14.setItem(4, Gui.item(Material.BELL, "&6&lOpen reports", "&7" + var2.size() + " waiting"));
      var14.setItem(var12 * 9 - 5, Gui.item(Material.BARRIER, "&cClose"));
      Gui.pad(var14, 0, Material.GRAY_STAINED_GLASS_PANE);
      var1.openInventory(var14);
   }

   @EventHandler
   public void onReportsClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("reports")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  int var4 = var1.getSlot();
                  if (var4 == var1.getInventory().getSize() - 5) {
                     var3.closeInventory();
                  } else {
                     String var5 = var2.at.get(var4);
                     if (var5 != null && this.can(var3, "reports")) {
                        String var6;
                        synchronized (this.data) {
                           var6 = this.data.cfg.getString("reports." + var5 + ".target");
                        }

                        if (var6 != null) {
                           if (var1.isRightClick()) {
                              synchronized (this.data) {
                                 this.data.cfg.set("reports." + var5 + ".open", false);
                                 this.data.mark();
                              }

                              U.send(var3, "Report resolved.");
                              this.openReports(var3);
                           } else {
                              Moderation.Target var12 = this.resolve(var6);
                              if (var12 != null) {
                                 if (!var1.isShiftClick()) {
                                    this.openCheck(var3, var12);
                                 } else if (var12.online == null) {
                                    U.send(var3, "&cThat player is offline.");
                                 } else {
                                    var3.closeInventory();
                                    var3.teleportAsync(var12.online.getLocation());
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

   private boolean punishMenu(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "punish")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /punish <player>");
         return true;
      } else {
         Moderation.Target var3 = this.resolve(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cPlayer &e" + var2[0] + "&c was not found or has never joined.");
            return true;
         } else {
            this.openPunish((Player)var1, var3);
            return true;
         }
      }
   }

   private Moderation.PType presetType(String var1) {
      String var2 = var1 == null ? "" : var1.toLowerCase(Locale.ROOT);
      switch (var2) {
         case "ban":
            return Moderation.PType.BAN;
         case "tempban":
            return Moderation.PType.TEMPBAN;
         case "mute":
            return Moderation.PType.MUTE;
         case "tempmute":
            return Moderation.PType.TEMP_MUTE;
         case "warn":
            return Moderation.PType.WARN;
         case "kick":
            return Moderation.PType.KICK;
         default:
            return null;
      }
   }

   private String presetCmd(Moderation.PType var1) {
      return var1.name().replace("_", "").toLowerCase(Locale.ROOT);
   }

   private void openPunish(Player var1, Moderation.Target var2) {
      ConfigurationSection var3 = this.plugin.getConfig().getConfigurationSection("moderation.presets");
      ArrayList var4 = new ArrayList();
      if (var3 != null) {
         for (String var6 : var3.getKeys(false)) {
            if (this.presetType(var3.getString(var6 + ".type")) != null) {
               var4.add(var6);
            }
         }
      }

      int var15 = Gui.rowsFor(var4.size());
      Gui var16 = new Gui("punish");
      var16.extra = var2.name;
      Inventory var7 = var16.make(var15 * 9, "&6&lWaffle&e&lMC &8» &7Punish: " + var2.name);
      Gui.border(var7, Material.ORANGE_STAINED_GLASS_PANE);
      List var8 = Gui.inner(var15);

      for (int var9 = 0; var9 < var8.size() && var9 < var4.size(); var9++) {
         String var10 = (String)var4.get(var9);
         Moderation.PType var11 = this.presetType(var3.getString(var10 + ".type"));
         String var12 = var3.getString(var10 + ".duration", "");
         Material var13 = Material.matchMaterial(var3.getString(var10 + ".icon", "PAPER"));
         ArrayList<String> var14 = new ArrayList<>();
         var14.add("&7Action: &f" + var11.label);
         if (var11.temp()) {
            var14.add("&7Length: &f" + U.duration(Math.max(0L, U.parseDuration(var12))));
         }

         var14.add("&7Reason: &f" + var3.getString(var10 + ".reason", var3.getString(var10 + ".name", var10)));
         var14.add("");
         var14.add("&eClick &7to apply");
         var14.add("&eShift-click &7to apply silently");
         var7.setItem(
            (Integer)var8.get(var9), Gui.item(var13 == null ? Material.PAPER : var13, var3.getString(var10 + ".name", var10), var14.toArray(new String[0]))
         );
         var16.at.put((Integer)var8.get(var9), var10);
      }

      var7.setItem(4, Gui.item(Material.NETHERITE_SWORD, "&c&lPunish &f" + var2.name, "&7Pick a preset below.", "&7Or use the commands for a custom reason."));
      var7.setItem(var15 * 9 - 5, Gui.item(Material.BARRIER, "&cClose"));
      Gui.pad(var7, 0, Material.GRAY_STAINED_GLASS_PANE);
      var1.openInventory(var7);
   }

   @EventHandler
   public void onPunishClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("punish")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  int var4 = var1.getSlot();
                  if (var4 == var1.getInventory().getSize() - 5) {
                     var3.closeInventory();
                  } else {
                     String var5 = var2.at.get(var4);
                     if (var5 != null && var2.extra != null) {
                        ConfigurationSection var6 = this.plugin.getConfig().getConfigurationSection("moderation.presets." + var5);
                        if (var6 != null) {
                           Moderation.PType var7 = this.presetType(var6.getString("type"));
                           if (var7 != null && this.can(var3, this.presetCmd(var7))) {
                              Moderation.Target var8 = this.resolve(var2.extra);
                              if (var8 != null) {
                                 long var9 = -1L;
                                 if (var7.temp()) {
                                    var9 = U.parseDuration(var6.getString("duration", ""));
                                    if (var9 <= 0L || var9 == Long.MIN_VALUE) {
                                       U.send(var3, "&cThat preset has an invalid duration in the config.");
                                       return;
                                    }
                                 }

                                 var3.closeInventory();
                                 this.execute(var3, var7, var8, var6.getString("reason", var6.getString("name", var5)), var9, null, var1.isShiftClick());
                              }
                           } else {
                              U.send(var3, "&cYou don't have permission for that action.");
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      switch (var5) {
         case "ban":
            return this.punish(var1, Moderation.PType.BAN, var4, var5);
         case "tempban":
            return this.punish(var1, Moderation.PType.TEMPBAN, var4, var5);
         case "ipban":
            return this.punish(var1, Moderation.PType.IP_BAN, var4, var5);
         case "tempipban":
            return this.punish(var1, Moderation.PType.TEMP_IP_BAN, var4, var5);
         case "mute":
            return this.punish(var1, Moderation.PType.MUTE, var4, var5);
         case "tempmute":
            return this.punish(var1, Moderation.PType.TEMP_MUTE, var4, var5);
         case "jail":
            return this.punish(var1, Moderation.PType.JAIL, var4, var5);
         case "tempjail":
            return this.punish(var1, Moderation.PType.TEMP_JAIL, var4, var5);
         case "warn":
            return this.punish(var1, Moderation.PType.WARN, var4, var5);
         case "kick":
            return this.punish(var1, Moderation.PType.KICK, var4, var5);
         case "unban":
            return this.revoke(var1, var5, var4);
         case "unbanip":
            return this.revoke(var1, var5, var4);
         case "unmute":
            return this.revoke(var1, var5, var4);
         case "unjail":
            return this.revoke(var1, var5, var4);
         case "setjail":
            return this.setJail(var1, var4);
         case "deljail":
            return this.delJail(var1, var4);
         case "freeze":
            return this.freeze(var1, var4);
         case "check":
            return this.check(var1, var4);
         case "chat":
            return this.chat(var1, var4);
         case "staffchat":
            return this.staffchat(var1, var4);
         case "punish":
            return this.punishMenu(var1, var4);
         case "alts":
            return this.alts(var1, var4);
         case "notes":
            return this.notes(var1, var4);
         case "report":
            return this.report(var1, var4);
         case "reports":
            return this.reports(var1);
         case "staff":
            return this.staffList(var1);
         default:
            return false;
      }
   }

   private boolean punish(CommandSender var1, Moderation.PType var2, String[] var3, String var4) {
      if (!this.can(var1, var4)) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else {
         boolean var5 = var2.jail();
         boolean var6 = var2.temp();
         int var7 = 1 + (var5 ? 1 : 0) + (var6 ? 1 : 0);
         if (var3.length < var7) {
            String var16 = "/"
               + var4
               + " <"
               + (var2.ipBan() ? "player|ip" : "player")
               + ">"
               + (var5 ? " <jail>" : "")
               + (var6 ? " <duration>" : "")
               + (
                  var2 != Moderation.PType.WARN && var2 != Moderation.PType.KICK && !var2.ban() && !var2.mute() && !var5 && !var2.ipBan()
                     ? ""
                     : " [reason] [-s]"
               );
            U.send(var1, "&cUsage: " + var16);
            return true;
         } else {
            int var8 = 1;
            String var9 = null;
            long var10 = -1L;
            if (var5) {
               var9 = var3[var8].toLowerCase(Locale.ROOT);
               var8++;
            }

            if (var6) {
               var10 = U.parseDuration(var3[var8]);
               if (var10 <= 0L || var10 == Long.MIN_VALUE) {
                  U.send(var1, "&cInvalid duration. Examples: &e30m&c, &e2h&c, &e1d&c, &e7d&c.");
                  return true;
               }

               var8++;
            }

            StringBuilder var12 = new StringBuilder();
            boolean var13 = false;

            for (int var14 = var8; var14 < var3.length; var14++) {
               if (var3[var14].equalsIgnoreCase("-s")) {
                  var13 = true;
               } else {
                  if (var12.length() > 0) {
                     var12.append(' ');
                  }

                  var12.append(var3[var14]);
               }
            }

            String var17 = var12.length() == 0 ? "No reason specified" : var12.toString();
            Moderation.Target var15;
            if (var2.ipBan() && IP.matcher(var3[0]).matches()) {
               var15 = new Moderation.Target();
               var15.name = var3[0];
               var15.ip = var3[0];
            } else {
               var15 = this.resolve(var3[0]);
            }

            if (var15 == null) {
               U.send(var1, "&cPlayer &e" + var3[0] + "&c was not found or has never joined.");
               return true;
            } else {
               this.execute(var1, var2, var15, var17, var10, var9, var13);
               return true;
            }
         }
      }
   }

   private boolean revoke(CommandSender var1, String var2, String[] var3) {
      if (!this.can(var1, var2)) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var3.length < 1) {
         U.send(var1, "&cUsage: /" + var2 + " <" + (var2.equals("unbanip") ? "player|ip" : "player") + ">");
         return true;
      } else {
         switch (var2) {
            case "unban":
               int var11 = this.deactivate(var3[0], null, Moderation.PType.BAN, Moderation.PType.TEMPBAN);
               if (var11 == 0) {
                  U.send(var1, "&e" + var3[0] + "&c is not banned.");
               } else {
                  U.send(var1, "&aUnbanned &e" + var3[0] + "&a.");
               }

               return true;
            case "unbanip":
               String var12 = IP.matcher(var3[0]).matches() ? var3[0] : null;
               Moderation.Target var13 = var12 == null ? this.resolve(var3[0]) : null;
               if (var13 != null) {
                  var12 = var13.ip;
               }

               if (var12 == null) {
                  U.send(var1, "&cNo IP found for &e" + var3[0] + "&c.");
                  return true;
               }

               int var10 = this.deactivate(null, var12, Moderation.PType.IP_BAN, Moderation.PType.TEMP_IP_BAN);
               if (var10 == 0) {
                  U.send(var1, "&cIP &e" + var12 + "&c is not banned.");
               } else {
                  U.send(var1, "&aUnbanned IP &e" + var12 + "&a.");
               }

               return true;
            case "unmute":
               int var9 = this.deactivate(var3[0], null, Moderation.PType.MUTE, Moderation.PType.TEMP_MUTE);
               if (var9 == 0) {
                  U.send(var1, "&e" + var3[0] + "&c is not muted.");
                  return true;
               }

               Player var7 = Bukkit.getPlayerExact(var3[0]);
               if (var7 != null) {
                  this.muted.remove(var7.getUniqueId());
                  U.send(var7, "&aYou have been unmuted.");
               }

               U.send(var1, "&aUnmuted &e" + var3[0] + "&a.");
               return true;
            default:
               int var4 = this.deactivate(var3[0], null, Moderation.PType.JAIL, Moderation.PType.TEMP_JAIL);
               if (var4 == 0) {
                  U.send(var1, "&e" + var3[0] + "&c is not jailed.");
                  return true;
               } else {
                  Player var8 = Bukkit.getPlayerExact(var3[0]);
                  if (var8 != null) {
                     this.release(var8);
                  }

                  U.send(var1, "&aReleased &e" + var3[0] + "&a from jail.");
                  return true;
               }
         }
      }
   }

   private boolean setJail(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "setjail")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /setjail <name>");
         return true;
      } else {
         String var3 = var2[0].toLowerCase(Locale.ROOT);
         synchronized (this.data) {
            Yml.writeLoc(this.data.cfg.createSection("jails." + var3), ((Player)var1).getLocation());
            this.data.mark();
         }

         U.send(var1, "Jail &e" + var3 + "&7 set at your location.");
         return true;
      }
   }

   private boolean delJail(CommandSender var1, String[] var2) {
      if (!this.can(var1, "setjail")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /deljail <name>");
         return true;
      } else {
         String var3 = var2[0].toLowerCase(Locale.ROOT);
         if (!this.jailNames().contains(var3)) {
            U.send(var1, "&cJail &e" + var3 + "&c doesn't exist.");
            return true;
         } else {
            synchronized (this.data) {
               this.data.cfg.set("jails." + var3, null);
               this.data.mark();
            }

            U.send(var1, "Jail &e" + var3 + "&7 deleted.");
            return true;
         }
      }
   }

   private boolean freeze(CommandSender var1, String[] var2) {
      if (!this.can(var1, "freeze")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /freeze <player>");
         return true;
      } else {
         Player var3 = Bukkit.getPlayerExact(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cThat player isn't online.");
            return true;
         } else {
            this.toggleFreeze(var1, var3);
            return true;
         }
      }
   }

   private void toggleFreeze(CommandSender var1, Player var2) {
      if (this.frozen.remove(var2.getUniqueId())) {
         this.saveFrozen();
         var2.resetTitle();
         U.send(var2, "&aYou have been unfrozen.");
         U.send(var1, "Unfroze &e" + var2.getName() + "&7.");
      } else if (var1 instanceof Player && this.bypass(var2, "freeze")) {
         U.send(var1, "&cYou can't freeze &e" + var2.getName() + "&c, they have bypass permissions.");
      } else {
         this.frozen.add(var2.getUniqueId());
         this.saveFrozen();
         U.send(var2, "&bYou have been frozen by staff. Do not log out.");
         U.send(var1, "Froze &e" + var2.getName() + "&7.");
      }
   }

   private boolean chat(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player var3)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!U.can(var1, "staff", "chat") && !(U.legacy() && var3.hasPermission("staffcore.staffchat"))) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /chat <staff|all>");
         return true;
      } else {
         if (var2[0].equalsIgnoreCase("staff")) {
            this.staffChat.add(var3.getUniqueId());
            U.send(var1, "Your messages now go to &cstaff chat&7.");
         } else if (var2[0].equalsIgnoreCase("all")) {
            this.staffChat.remove(var3.getUniqueId());
            U.send(var1, "Your messages now go to &fpublic chat&7.");
         } else {
            U.send(var1, "&cUsage: /chat <staff|all>");
         }

         return true;
      }
   }

   private boolean staffchat(CommandSender var1, String[] var2) {
      if (!U.can(var1, "staff", "chat") && !(U.legacy() && var1.hasPermission("staffcore.staffchat"))) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length == 0) {
         if (!(var1 instanceof Player var3)) {
            return true;
         } else {
            if (this.staffChat.remove(var3.getUniqueId())) {
               U.send(var1, "Staff chat &coff&7.");
            } else {
               this.staffChat.add(var3.getUniqueId());
               U.send(var1, "Staff chat &aon&7.");
            }

            return true;
         }
      } else {
         this.sendStaffChat(var1.getName(), String.join(" ", var2));
         return true;
      }
   }

   private boolean check(CommandSender var1, String[] var2) {
      if (!(var1 instanceof Player)) {
         U.send(var1, "&cPlayers only.");
         return true;
      } else if (!this.can(var1, "check")) {
         U.send(var1, "&cYou don't have permission.");
         return true;
      } else if (var2.length < 1) {
         U.send(var1, "&cUsage: /check <player>");
         return true;
      } else {
         Moderation.Target var3 = this.resolve(var2[0]);
         if (var3 == null) {
            U.send(var1, "&cPlayer &e" + var2[0] + "&c was not found or has never joined.");
            return true;
         } else {
            this.openCheck((Player)var1, var3);
            return true;
         }
      }
   }

   private ItemStack entry(ConfigurationSection var1) {
      Moderation.PType var2 = this.typeOf(var1);
      long var3 = var1.getLong("expireTime", -1L);
      boolean var5 = var1.getBoolean("active") && (var3 < 0L || var3 > System.currentTimeMillis());
      String var6 = var2 == Moderation.PType.KICK ? "&7Logged" : (var5 ? "&a● Active" : "&8● Ended");
      ArrayList<String> var7 = new ArrayList<>();
      var7.add("&7Status: " + var6);
      var7.add("&7By: &f" + var1.getString("staffName", "?"));
      var7.add("&7Reason: &f" + var1.getString("reason", "?"));
      var7.add("&7Date: &f" + DATE.format(Instant.ofEpochMilli(var1.getLong("timestamp"))));
      if (var2 != null && var2.temp()) {
         var7.add("&7Length: &f" + U.duration(var1.getLong("duration")));
      } else if (var2 != null && var2 != Moderation.PType.WARN && var2 != Moderation.PType.KICK) {
         var7.add("&7Length: &fpermanent");
      }

      if (var5 && var3 > 0L) {
         var7.add("&7Remaining: &e" + this.remaining(var1));
      }

      return Gui.item(
         var2 == null ? Material.PAPER : var2.icon, (var5 ? "&c&l" : "&7&l") + (var2 == null ? "Unknown" : var2.label), var7.toArray(new String[0])
      );
   }

   private void openCheck(Player var1, Moderation.Target var2) {
      List<ConfigurationSection> var3 = this.history(var2.uuid == null ? null : var2.uuid.toString(), var2.name);
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      int var8 = 0;
      ArrayList<String> var9 = new ArrayList<>();
      long var10 = System.currentTimeMillis();

      for (ConfigurationSection var13 : var3) {
         Moderation.PType var14 = this.typeOf(var13);
         if (var14 != null) {
            if (var14.ban() || var14.ipBan()) {
               var4++;
            } else if (var14.mute()) {
               var5++;
            } else if (var14 == Moderation.PType.WARN) {
               var6++;
            } else if (var14 == Moderation.PType.KICK) {
               var7++;
            } else {
               var8++;
            }

            long var15 = var13.getLong("expireTime", -1L);
            if (var13.getBoolean("active") && var14 != Moderation.PType.KICK && var14 != Moderation.PType.WARN && (var15 < 0L || var15 > var10)) {
               var9.add("&c" + var14.label + " &7- &f" + var13.getString("reason", "?"));
            }
         }
      }

      Gui var27 = new Gui("check");
      var27.extra = var2.uuid == null ? var2.name : var2.uuid.toString();
      var27.keys.add(var2.name);
      Inventory var28 = var27.make(54, "&6&lWaffle&e&lMC &8» &7Check: " + var2.name);
      Gui.border(var28, Material.ORANGE_STAINED_GLASS_PANE);
      Player var29 = Bukkit.getPlayerExact(var2.name);
      ItemStack var30 = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta var16 = (SkullMeta)var30.getItemMeta();
      if (var2.uuid != null) {
         var16.setOwningPlayer(Bukkit.getOfflinePlayer(var2.uuid));
      }

      var16.setDisplayName(U.color("&r&6&l" + var2.name));
      ArrayList var17 = new ArrayList();
      var17.add(U.color("&r&7Status: " + (var29 != null ? "&aOnline" : "&cOffline")));
      if (var2.uuid != null) {
         var17.add(U.color("&r&7UUID: &f" + var2.uuid.toString().substring(0, 8) + "…"));
      }

      if (var1.hasPermission("wafflecore.check.ip") || (U.legacy() && var1.hasPermission("staffcore.check.ip")) || var1.hasPermission("wafflecore.admin")) {
         var17.add(U.color("&r&7IP: &f" + (var2.ip != null && !var2.ip.isEmpty() ? var2.ip : "unknown")));
      }

      if (var29 != null) {
         var17.add(U.color("&r&7Mode: &f" + var29.getGameMode().name().toLowerCase(Locale.ROOT)));
         var17.add(U.color("&r&7Health: &f" + (int)var29.getHealth() + " &7Food: &f" + var29.getFoodLevel()));
         var17.add(U.color("&r&7World: &f" + var29.getWorld().getName()));
         var17.add(
            U.color("&r&7Position: &f" + var29.getLocation().getBlockX() + ", " + var29.getLocation().getBlockY() + ", " + var29.getLocation().getBlockZ())
         );
         var17.add(U.color("&r&7Ping: &f" + var29.getPing() + "ms"));
         if (this.frozen.contains(var29.getUniqueId())) {
            var17.add(U.color("&r&bFrozen"));
         }
      }

      var16.setLore(var17);
      var30.setItemMeta(var16);
      var28.setItem(4, var30);
      var28.setItem(20, Gui.item(Material.BARRIER, "&c&lBans: &f" + var4, "&7Total bans on record"));
      var28.setItem(21, Gui.item(Material.PAPER, "&e&lMutes: &f" + var5, "&7Total mutes on record"));
      var28.setItem(22, Gui.item(Material.GOLD_NUGGET, "&6&lWarnings: &f" + var6, "&7Total warnings on record"));
      var28.setItem(23, Gui.item(Material.LEATHER_BOOTS, "&b&lKicks: &f" + var7, "&7Total kicks on record"));
      var28.setItem(24, Gui.item(Material.IRON_BARS, "&7&lJails: &f" + var8, "&7Total jails on record"));
      ArrayList<String> var18 = new ArrayList<>();
      if (var9.isEmpty()) {
         var18.add("&aNone");
      } else {
         var18.addAll(var9);
      }

      var28.setItem(13, Gui.item(var9.isEmpty() ? Material.LIME_DYE : Material.RED_DYE, "&f&lActive punishments", var18.toArray(new String[0])));
      ArrayList var19 = new ArrayList();

      for (int var20 = 28; var20 <= 34; var20++) {
         var19.add(var20);
      }

      for (int var31 = 37; var31 <= 43; var31++) {
         var19.add(var31);
      }

      for (int var32 = 0; var32 < var19.size() && var32 < var3.size(); var32++) {
         var28.setItem((Integer)var19.get(var32), this.entry((ConfigurationSection)var3.get(var32)));
      }

      if (var3.isEmpty()) {
         var28.setItem(31, Gui.item(Material.LIME_STAINED_GLASS_PANE, "&aClean record", "&7No punishments on file."));
      }

      boolean var33 = var2.uuid != null && this.muted.containsKey(var2.uuid);
      List var21 = this.altsOf(var2);
      ArrayList<String> var22 = new ArrayList<>();
      if (var21.isEmpty()) {
         var22.add("&aNone found");
      } else {
         for (int var23 = 0; var23 < var21.size() && var23 < 8; var23++) {
            var22.add((String)var21.get(var23));
         }
      }

      var28.setItem(11, Gui.item(Material.PLAYER_HEAD, "&d&lAlt accounts: &f" + var21.size(), var22.toArray(new String[0])));
      List<String> var34 = var2.uuid == null ? new ArrayList<String>() : this.notesOf(var2.uuid.toString());
      ArrayList<String> var24 = new ArrayList<>();
      if (var34.isEmpty()) {
         var24.add("&8No notes. Use /notes " + var2.name + " add <text>");
      }

      for (int var25 = Math.max(0, var34.size() - 5); var25 < var34.size(); var25++) {
         String[] var26 = ((String)var34.get(var25)).split("\\|", 3);
         var24.add(var26.length == 3 ? "&e" + var26[1] + "&8: &f" + var26[2] : "&f" + (String)var34.get(var25));
      }

      var28.setItem(15, Gui.item(Material.WRITABLE_BOOK, "&a&lStaff notes: &f" + var34.size(), var24.toArray(new String[0])));
      var28.setItem(45, Gui.item(Material.NETHERITE_SWORD, "&c&lPunish", "&7Open the punish menu"));
      var28.setItem(46, Gui.item(Material.ENDER_PEARL, "&b&lTeleport", var29 == null ? "&8Player is offline" : "&7Click to teleport to them"));
      var28.setItem(47, Gui.item(Material.CHEST, "&e&lInventory", var29 == null ? "&8Player is offline" : "&7Click to open their inventory"));
      var28.setItem(
         48,
         Gui.item(
            Material.ICE, this.frozen.contains(var2.uuid) ? "&b&lUnfreeze" : "&b&lFreeze", var29 == null ? "&8Player is offline" : "&7Click to toggle freeze"
         )
      );
      var28.setItem(49, Gui.item(Material.GOLD_NUGGET, "&6&lWarn", "&7Click to warn (reason: Staff check)"));
      var28.setItem(50, Gui.item(Material.LEATHER_BOOTS, "&6&lKick", var29 == null ? "&8Player is offline" : "&7Click to kick"));
      var28.setItem(
         51,
         Gui.item(
            var33 ? Material.LIME_DYE : Material.PAPER, var33 ? "&a&lUnmute" : "&e&lMute 30m", var33 ? "&7Click to unmute" : "&7Click to mute for 30 minutes"
         )
      );
      var28.setItem(52, Gui.item(Material.BARRIER, "&c&lBan 1 day", "&7Shift-click to confirm"));
      var28.setItem(53, Gui.item(Material.RED_STAINED_GLASS_PANE, "&cClose"));
      Gui.pad(var28, 0, Material.GRAY_STAINED_GLASS_PANE);
      var1.openInventory(var28);
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      if (var1.getInventory().getHolder() instanceof Gui) {
         Gui var2 = (Gui)var1.getInventory().getHolder();
         if (var2.type.equals("check")) {
            var1.setCancelled(true);
            if (var1.getWhoClicked() instanceof Player) {
               Player var3 = (Player)var1.getWhoClicked();
               if (var1.getClickedInventory() != null && var1.getClickedInventory() == var1.getInventory()) {
                  int var4 = var1.getSlot();
                  if (var4 >= 45) {
                     if (var4 == 53) {
                        var3.closeInventory();
                     } else if (this.can(var3, "check")) {
                        String var5 = var2.keys.isEmpty() ? null : var2.keys.get(0);
                        if (var5 != null) {
                           Moderation.Target var6 = this.resolve(var5);
                           if (var6 != null) {
                              Player var7 = var6.online;
                              switch (var4) {
                                 case 45:
                                    if (!this.can(var3, "punish")) {
                                       return;
                                    }

                                    this.openPunish(var3, var6);
                                    return;
                                 case 46:
                                    if (var7 == null) {
                                       return;
                                    }

                                    var3.closeInventory();
                                    var3.teleportAsync(var7.getLocation());
                                    return;
                                 case 47:
                                    if (var7 == null) {
                                       return;
                                    }

                                    var3.openInventory(var7.getInventory());
                                    return;
                                 case 48:
                                    if (var7 != null && this.can(var3, "freeze")) {
                                       this.toggleFreeze(var3, var7);
                                       this.openCheck(var3, var6);
                                       return;
                                    }

                                    return;
                                 case 49:
                                    if (!this.can(var3, "warn")) {
                                       return;
                                    }

                                    this.execute(var3, Moderation.PType.WARN, var6, "Staff check", -1L, null, false);
                                    this.openCheck(var3, var6);
                                    return;
                                 case 50:
                                    if (var7 != null && this.can(var3, "kick")) {
                                       var3.closeInventory();
                                       this.execute(var3, Moderation.PType.KICK, var6, "Staff check", -1L, null, false);
                                       return;
                                    }

                                    return;
                                 case 51:
                                    if (var6.uuid != null && this.muted.containsKey(var6.uuid)) {
                                       if (!this.can(var3, "unmute")) {
                                          return;
                                       }

                                       this.deactivate(var6.name, null, Moderation.PType.MUTE, Moderation.PType.TEMP_MUTE);
                                       this.muted.remove(var6.uuid);
                                       if (var7 != null) {
                                          U.send(var7, "&aYou have been unmuted.");
                                       }

                                       U.send(var3, "&aUnmuted &e" + var6.name + "&a.");
                                    } else {
                                       if (!this.can(var3, "tempmute")) {
                                          return;
                                       }

                                       this.execute(var3, Moderation.PType.TEMP_MUTE, var6, "Staff check", 1800L, null, false);
                                    }

                                    this.openCheck(var3, var6);
                                    return;
                                 case 52:
                                    if (var1.isShiftClick() && this.can(var3, "tempban")) {
                                       var3.closeInventory();
                                       this.execute(var3, Moderation.PType.TEMPBAN, var6, "Staff check", 86400L, null, false);
                                       return;
                                    }

                                    return;
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
      String var5 = var2.getName().toLowerCase(Locale.ROOT);
      if (var5.equals("notes") && var4.length == 2) {
         return U.filter(List.of("add", "remove"), var4[1]);
      } else if (!var5.equals("punish") && !var5.equals("alts") && !var5.equals("notes") && !var5.equals("report")) {
         boolean var6 = var5.equals("jail") || var5.equals("tempjail");
         boolean var7 = var5.startsWith("temp");
         if (var4.length != 1) {
            if (var4.length == 2 && var6) {
               return U.filter(this.jailNames(), var4[1]);
            } else {
               int var8 = var6 ? 2 : 1;
               return var7 && var4.length == var8 + 1 ? U.filter(List.of("30m", "1h", "6h", "1d", "7d", "30d"), var4[var8]) : List.of();
            }
         } else if (var5.equals("deljail")) {
            return U.filter(this.jailNames(), var4[0]);
         } else if (var5.equals("setjail")) {
            return List.of();
         } else if (var5.equals("chat")) {
            return U.filter(List.of("staff", "all"), var4[0]);
         } else {
            return !var5.equals("staffchat") && !var5.equals("reports") && !var5.equals("staff") ? Eco.onlineNames(var4[0]) : List.of();
         }
      } else {
         return var4.length == 1 ? Eco.onlineNames(var4[0]) : List.of();
      }
   }

   private static final class MuteInfo {
      final long until;
      final String reason;

      MuteInfo(long var1, String var3) {
         this.until = var1;
         this.reason = var3;
      }
   }

   static enum PType {
      BAN("Ban", Material.BARRIER),
      TEMPBAN("Temp ban", Material.BARRIER),
      IP_BAN("IP ban", Material.REDSTONE_BLOCK),
      TEMP_IP_BAN("Temp IP ban", Material.REDSTONE_BLOCK),
      MUTE("Mute", Material.PAPER),
      TEMP_MUTE("Temp mute", Material.PAPER),
      JAIL("Jail", Material.IRON_BARS),
      TEMP_JAIL("Temp jail", Material.IRON_BARS),
      WARN("Warning", Material.GOLD_NUGGET),
      KICK("Kick", Material.LEATHER_BOOTS);

      final String label;
      final Material icon;

      private PType(String nullxx, Material nullxxx) {
         this.label = nullxx;
         this.icon = nullxxx;
      }

      boolean ban() {
         return this == BAN || this == TEMPBAN;
      }

      boolean ipBan() {
         return this == IP_BAN || this == TEMP_IP_BAN;
      }

      boolean mute() {
         return this == MUTE || this == TEMP_MUTE;
      }

      boolean jail() {
         return this == JAIL || this == TEMP_JAIL;
      }

      boolean temp() {
         return this == TEMPBAN || this == TEMP_IP_BAN || this == TEMP_MUTE || this == TEMP_JAIL;
      }

      String bypass() {
         if (this.ban() || this.ipBan()) {
            return "ban";
         } else if (this.mute()) {
            return "mute";
         } else if (this.jail()) {
            return "jail";
         } else {
            return this == WARN ? "warn" : "kick";
         }
      }
   }

   private static final class Target {
      UUID uuid;
      String name;
      String ip;
      Player online;
   }
}
