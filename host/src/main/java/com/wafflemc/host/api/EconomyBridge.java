package com.wafflemc.host.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Connects modules to the economy that lives inside the closed-source core.jar.
 *
 * <p>core.jar keeps its economy package-private, so the host reaches it by reflection once, when
 * core.jar starts, and hands modules a typed {@link WaffleEconomy}. If core.jar changes shape the
 * bridge fails loudly at load time instead of at the first payout. When core.jar is rebuilt from
 * source, expose a real API and delete this class.
 */
public final class EconomyBridge implements WaffleEconomy {
    private final Object eco;
    private final Method get;
    private final Method add;
    private final Method take;
    private final Method find;
    private final Method nameOf;
    private final Method money;

    private EconomyBridge(Object eco) throws ReflectiveOperationException {
        this.eco = eco;
        Class<?> c = eco.getClass();
        get = open(c.getDeclaredMethod("get", UUID.class));
        add = open(c.getDeclaredMethod("add", UUID.class, double.class));
        take = open(c.getDeclaredMethod("take", UUID.class, double.class));
        find = open(c.getDeclaredMethod("find", String.class));
        nameOf = open(c.getDeclaredMethod("nameOf", UUID.class));
        Method m = null;
        try {
            m = open(Class.forName("com.wafflemc.core.U", true, c.getClassLoader()).getDeclaredMethod("money", double.class));
        } catch (ReflectiveOperationException ignored) {
            // formatting falls back to a plain symbol
        }
        money = m;
    }

    /** Builds a bridge for a running core module, or throws when it has no economy. */
    static EconomyBridge forCore(Object coreModule) throws ReflectiveOperationException {
        Field f = coreModule.getClass().getDeclaredField("eco");
        f.setAccessible(true);
        Object eco = f.get(coreModule);
        if (eco == null) throw new IllegalStateException("core economy is not initialised");
        return new EconomyBridge(eco);
    }

    private static Method open(Method m) {
        m.setAccessible(true);
        return m;
    }

    private void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The economy may only be used from the main thread");
        }
    }

    @Override
    public double balance(UUID player) {
        mainThread();
        return (double) call(get, player);
    }

    @Override
    public void deposit(UUID player, double amount) {
        mainThread();
        if (amount > 0) call(add, player, amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        mainThread();
        return amount <= 0 || (boolean) call(take, player, amount);
    }

    @Override
    public String format(double amount) {
        if (money != null) {
            try {
                return (String) money.invoke(null, amount);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "$" + String.format(Locale.US, "%,.2f", amount);
    }

    @Override
    public UUID find(String name) {
        mainThread();
        return (UUID) call(find, name);
    }

    @Override
    public String nameOf(UUID player) {
        mainThread();
        return (String) call(nameOf, player);
    }

    private Object call(Method m, Object... args) {
        try {
            return m.invoke(eco, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Economy call failed: " + e.getCause(), e);
        }
    }

    public static void install(Object coreModule) throws ReflectiveOperationException {
        Services.economy(forCore(coreModule));
    }
}
