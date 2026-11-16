package com.wafflemc.core;

import com.wafflemc.host.api.WaffleEconomy;
import java.util.UUID;
import org.bukkit.Bukkit;

/** Native economy API handed to host modules. Main thread only. */
final class EcoApi implements WaffleEconomy {
   private final Eco eco;

   EcoApi(Eco var1) {
      this.eco = var1;
   }

   private static void mainThread() {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("The economy may only be used from the main thread");
      }
   }

   @Override
   public double balance(UUID var1) {
      mainThread();
      return this.eco.get(var1);
   }

   @Override
   public void deposit(UUID var1, double var2) {
      mainThread();
      if (var2 > 0.0) {
         this.eco.add(var1, var2);
      }
   }

   @Override
   public boolean withdraw(UUID var1, double var2) {
      mainThread();
      return var2 <= 0.0 || this.eco.take(var1, var2);
   }

   @Override
   public String format(double var1) {
      return U.money(var1);
   }

   @Override
   public UUID find(String var1) {
      mainThread();
      return this.eco.find(var1);
   }

   @Override
   public String nameOf(UUID var1) {
      mainThread();
      return this.eco.nameOf(var1);
   }
}
