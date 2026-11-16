package com.wafflemc.host.api;

import java.util.Optional;

/** Tiny static registry that lets modules reach services owned by the host. */
public final class Services {
    private static volatile WaffleEconomy economy;
    private static volatile String prefix = "&6&lWaffle&e&lMC &8» &7";

    private Services() {
    }

    /** The shared economy, or empty when core.jar is not running. */
    public static Optional<WaffleEconomy> economy() {
        return Optional.ofNullable(economy);
    }

    public static void economy(WaffleEconomy value) {
        economy = value;
    }

    public static String prefix() {
        return prefix;
    }

    public static void prefix(String value) {
        if (value != null && !value.isBlank()) {
            prefix = value;
        }
    }
}
