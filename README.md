# WaffleCore

All-in-one server core for WaffleMC (Paper 26.3, Java 25). Two layers:

- **WaffleHost** (`WaffleHost-2.2.0.jar`) is the actual Bukkit plugin. It loads the existing `core.jar` and any number of module jars, each in its own class loader, and can hot-reload them one at a time.
- **Modules** are small jars in `plugins/WaffleCore/modules/`. Each one has its own `config.yml`, commands and permissions.

`core.jar` (economy, homes, warps, TPA, RTP, kits, auction house, orders, moderation, staff tools) now builds from source in `core/`. The source was recovered by decompiling the old jar, so local variable names are `var1`, `var2` and so on, and comments are gone. See [AUDIT.md](AUDIT.md) for what was reviewed and fixed.

## Install

1. Replace `plugins/WaffleHost-2.0.0.jar` (or 2.1.0) with `dist/WaffleHost-2.2.0.jar`.
2. Replace `plugins/WaffleCore/core.jar` with `dist/core.jar`.
3. Copy the jars you want from `dist/modules/` into `plugins/WaffleCore/modules/`.
4. Restart once. After that, `/wafflecore reload` applies code and config changes without a restart.

Existing data and config are kept. New settings are read from defaults inside the jar, so you do not need to edit `config.yml`. To go back, put the 2.0.0 host and the old `core.jar` back (see git history) and delete the `modules` folder.

### Changes to know about in 2.2.0

- **Old `staffcore.*` permissions no longer grant anything by default.** Core used to accept `staffcore.admin`, `staffcore.staff`, `staffcore.staffchat`, `staffcore.bypass.*`, `staffcore.freeze.alerts`, `staffcore.check.ip` and `staffcore.command.*`. If any staff group still relies on those nodes, either grant the matching `wafflecore.*` nodes or set `legacy-staffcore-permissions: true` in `plugins/WaffleCore/config.yml`.
- **Economy log.** Every balance change is appended to `plugins/WaffleCore/economy.log` with the player, old and new balance, and the reason (`/pay`, `/eco`).
- **Retention.** `moderation.retention-days` (default `0`, keep everything) deletes finished punishment records older than that many days on startup.
- **Saves.** Data files are written on a background thread to a temp file and then renamed, so a crash cannot leave a half-written file. They are flushed on shutdown and on reload.

## Host commands

| Command | What it does |
| --- | --- |
| `/wafflecore status` | Shows core and every module, running or stopped |
| `/wafflecore reload` | Reloads everything (core first, then modules) |
| `/wafflecore reload <module>` | Reloads one module, for example `/wc reload jobs` |

If a reload fails, the previous version keeps running. Reloading `core` reloads everything, because `core.jar` cancels every scheduled task owned by the plugin when it stops. To keep a module off, add its name to `disabled-modules` in `plugins/WaffleCore/host.yml`.

## Modules

| Module | Commands | What it adds |
| --- | --- | --- |
| `chatguard` | none | Spam, repeat, caps, flood, blocked-word and link filtering. Staff alerts, then escalating punishments by running your existing `/warn` and `/tempmute` |
| `modlog` | `/modlog [text] [lines]` | Searchable audit log of staff and moderation commands. Optional Discord webhook alerts. Old logs are deleted after 30 days |
| `xrayalert` | `/xray <player>`, `/xray alerts` | Alerts staff when a player digs out many fully hidden ores. Click an alert to teleport |
| `perf` | `/perf`, `/perf chunks`, `/perf clear` | TPS, tick time, memory, busiest chunks, timed item clearing with countdown, optional mob limits, low-TPS alerts |
| `daily` | `/daily` | Daily reward with streaks and milestone bonuses, paid through the core economy |
| `jobs` | `/jobs` (`list`, `join`, `leave`, `top`) | Miner, lumberjack, farmer, hunter and fisher jobs with levels, pay scaling, exploit protection and a daily income cap |
| `scoreboard` | `/sidebar`, `/sb` | Configurable sidebar with balance, ping, TPS, playtime and more |

Permissions: `wafflecore.chatguard.bypass`, `wafflecore.chatguard.alerts`, `wafflecore.modlog`, `wafflecore.xray`, `wafflecore.xray.alerts`, `wafflecore.perf`, `wafflecore.perf.alerts` (all default to ops), and `wafflecore.daily`, `wafflecore.jobs`, `wafflecore.scoreboard` (default to everyone).

Each module writes its default config to `plugins/WaffleCore/modules/<name>/config.yml` on first start.

## Building

```
./gradlew dist
```

Needs JDK 25. The host, `core.jar` and module jars are written to `dist/`. The core source is in `core/src/main/java/com/wafflemc/core/`.

## Writing a module

1. Add a folder under `modules/` and list it in `settings.gradle.kts`.
2. Extend `ModuleBase` and implement `enable()`.
3. Add `module.properties` (`name`, `version`, `main`, optional `depends`) and `module-config.yml` under `src/main/resources`.

`ModuleBase` tracks commands, listeners, tasks, permissions and data files, and removes them all when the module stops, so a reload cannot leak anything. Use `data("file.yml")` for crash-safe, off-thread saves. Use `Services.economy()` to read or change balances from the main thread.

`core.jar` registers its economy through `Services.economy(...)`, so modules get a typed `WaffleEconomy` with no reflection. If an older `core.jar` is installed, the host falls back to a reflection bridge.

## Notes

- `Main.py`, `Procfile`, `config.json` and `requirements.txt` are an unrelated Discord giveaway bot. They are not part of the plugin.
- The `WaffleCore-1.0.x.jar` files are old releases kept for history.
