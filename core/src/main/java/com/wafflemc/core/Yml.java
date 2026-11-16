package com.wafflemc.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class Yml {
   final File file;
   YamlConfiguration cfg;
   boolean dirty;

   Yml(File var1) {
      this.file = var1;
      this.cfg = YamlConfiguration.loadConfiguration(var1);
   }

   /** Bumped on every change so caches built from this file know when they are stale. */
   volatile int version;

   void mark() {
      this.dirty = true;
      this.version++;
   }

   void reload() {
      this.cfg = YamlConfiguration.loadConfiguration(this.file);
      this.dirty = false;
      this.version++;
   }

   /** Writes through a temp file and an atomic rename so a crash never leaves a half-written store. */
   private void write(String var1) {
      try {
         File var2 = this.file.getAbsoluteFile();
         var2.getParentFile().mkdirs();
         Path var3 = var2.toPath().resolveSibling(var2.getName() + ".tmp");
         Files.writeString(var3, var1, StandardCharsets.UTF_8);

         try {
            Files.move(var3, var2.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var5) {
            Files.move(var3, var2.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var6) {
         WaffleCore.get().getLogger().warning("Could not save " + this.file.getName() + ": " + var6.getMessage());
      }
   }

   /** Synchronous save. Used on shutdown and for one-off imports. */
   void save() {
      this.dirty = false;
      String var1 = this.cfg.saveToString();
      WaffleCore.get().ioWait(() -> this.write(var1));
   }

   /** Serialises on the calling (main) thread, then writes on the background IO thread. */
   void saveIfDirty() {
      if (this.dirty) {
         this.dirty = false;
         String var1 = this.cfg.saveToString();
         WaffleCore.get().io(() -> this.write(var1));
      }
   }

   static void writeLoc(ConfigurationSection var0, Location var1) {
      var0.set("world", var1.getWorld() == null ? "world" : var1.getWorld().getName());
      var0.set("x", var1.getX());
      var0.set("y", var1.getY());
      var0.set("z", var1.getZ());
      var0.set("yaw", (double)var1.getYaw());
      var0.set("pitch", (double)var1.getPitch());
   }

   static Location readLoc(ConfigurationSection var0) {
      if (var0 == null) {
         return null;
      } else {
         World var1 = Bukkit.getWorld(var0.getString("world", ""));
         return var1 == null
            ? null
            : new Location(var1, var0.getDouble("x"), var0.getDouble("y"), var0.getDouble("z"), (float)var0.getDouble("yaw"), (float)var0.getDouble("pitch"));
      }
   }
}
