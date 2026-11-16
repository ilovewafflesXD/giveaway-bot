package com.wafflemc.host.api;

import java.util.UUID;

/**
 * Minimal economy surface exposed to modules. Backed by the economy inside {@code core.jar}.
 * All methods must be called from the main server thread.
 */
public interface WaffleEconomy {
    double balance(UUID player);

    /** Adds money. Amount must be positive. */
    void deposit(UUID player, double amount);

    /** Removes money if the player can afford it. Returns false when funds are insufficient. */
    boolean withdraw(UUID player, double amount);

    /** Formats an amount using the server's configured currency symbol. */
    String format(double amount);

    /** Resolves a player name to a UUID, or null when unknown. */
    UUID find(String name);

    String nameOf(UUID player);
}
