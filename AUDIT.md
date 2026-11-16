# WaffleCore audit

Scope: `core.jar` and `WaffleHost-2.0.0.jar` as found in this repo, decompiled and read. The findings below come from the decompiled classes. As of 2.2.0 the core source lives in `core/` (recovered by decompiling, so variable names are `var1`, `var2`, and so on) and most findings are fixed. The status table at the end shows what changed.

## High priority

1. **Ban checks race with the main thread.** `Moderation.onPreLogin` runs on the async login thread and calls `find()`, which walks the shared `moderation.yml` configuration and can write to it (`set("active", false)` on expired bans). The main thread edits the same object when staff punish someone. Bukkit's YAML classes are not thread-safe, so a login during a `/ban` can throw or return a wrong result. Fix: keep an immutable, concurrent index of active bans (by UUID, IP and name), rebuilt when a punishment changes.
2. **Ban checks scan every punishment ever issued.** `find()` loops through the whole `p` section, including inactive entries, on every login and under a lock. Cost grows forever. Fix: the same index as above, plus archiving expired entries.
3. **All data saves run on the main thread and are not crash-safe.** `WaffleCore` saves every dirty store with `runTaskTimer` (synchronous) every 30 seconds, and `Yml.save` writes the target file directly. A large `economy.yml` or `moderation.yml` causes a lag spike at each save. A crash or full disk mid-write truncates the file with no backup. Fix: serialise on the main thread, write on a background thread to a temp file, then rename. 2.1 ships this as `AtomicYaml`, which the new modules use. Longer term, move to SQLite.
4. **The economy cannot be used by other plugins.** It is package-private with no Vault provider, so shops, jobs plugins and placeholders cannot see balances. 2.1 adds a typed bridge for WaffleCore modules only. Fix: expose a real API (or Vault) from the rebuilt core.

## Medium priority

5. **Another plugin's permissions grant WaffleCore powers.** `U.can` and several moderation checks accept `staffcore.admin`, `staffcore.command.*`, `staffcore.staffchat` and similar. This looks like a migration shim. If any group or plugin still grants those nodes, those players get WaffleCore staff powers. Remove it once the migration is done.
6. **Chat uses deprecated APIs.** `Moderation` and `Auction` listen to `AsyncPlayerChatEvent` and use legacy `ChatColor`. On Paper, `AsyncChatEvent` and Adventure are the supported path, and chat-format plugins work better with them. The new `chatguard` uses `AsyncChatEvent`.
7. **Commands are injected straight into the command map.** `WaffleCore.bind` writes into `getKnownCommands()` to take over vanilla `/ban`, `/kick`, `/gamemode`, `/msg` and others. It works today but can break on a Paper update. The wrapper also prints a bare `/<name>` when a handler returns false, instead of usage.
8. **The economy does linear work in commands.** `/baltop` reads every balance and sorts on the main thread each time. Name lookup (`find`) scans the whole name table. Fine at a few hundred players, poor beyond that. Fix: keep a cached top list that refreshes every minute, and a name-to-UUID map.
9. **No economy audit trail.** Nothing records who paid whom or why, so duplication or abuse cannot be investigated. Fix: append-only transaction log.
10. **All moderation data sits in one file and grows without limit.** Punishments, notes, IP history and jails share `moderation.yml`. IP addresses are personal data under UK GDPR, so add a retention period and a way to erase a player's record.

## Lower priority

11. **Host reload cancels every task of the plugin.** `WaffleCore.stop` calls `cancelTasks(host)`. With one module that was safe. With several it would kill the others' timers, so 2.1 reloads everything when `core` is reloaded and tracks each module's own tasks.
12. **Everything in core is package-private and a static singleton** (`WaffleCore.get()`). Nothing outside it can integrate or be unit-tested.
13. **Hard-coded values.** The appeal link, the Essentials and StaffCore import paths and the default prefix are in code or defaults rather than clearly documented settings.
14. **No tests, no CI, no source.** The repo holds only jars, including six old releases. A rebuild from source is not possible today.

## Giveaway bot (`Main.py`)

Unrelated to the plugin, but it will not run as committed:

- `Procfile` runs `python main.py`, but the file is `Main.py`. On Linux hosting this fails to start.
- `config.json` is not valid JSON (`"port": host port`), so `json.load` fails.
- OAuth login has no `state` check (login CSRF) and does not URL-encode the redirect URI.
- Blocking `requests` calls run inside async handlers, which stall the web server and the bot.
- Giveaways live in memory and use `asyncio.sleep`, so a restart loses them and they never end.
- The user session stores the whole Discord user object, and the dashboard shows nothing except a greeting.

## Status in 2.2.0

| Finding | Status |
| --- | --- |
| 1 (ban check race) | Fixed. Login checks read an immutable snapshot of active bans, rebuilt on the main thread within one tick of any change. The async thread no longer touches the YAML tree |
| 2 (linear ban scan) | Fixed. The snapshot is keyed by UUID, IP and name, so a login is a few hash lookups. Expired bans are skipped at lookup time |
| 3 (blocking, unsafe saves) | Fixed in core. Data is serialised on the main thread, written on a background thread to a temp file, and renamed into place. Shutdown and reload flush synchronously |
| 4 (economy API) | Fixed for modules. Core registers a typed `WaffleEconomy`. A Vault provider is not included |
| 5 (`staffcore.*` permissions) | Fixed. Off by default, behind `legacy-staffcore-permissions` |
| 6 (chat events) | `Moderation` and `Auction` now use Paper's `AsyncChatEvent`. Legacy `ChatColor` is still used for colour codes |
| 7 (command wrapper) | Partly fixed. A handler that returns false now shows the command and its description. The command-map takeover is unchanged |
| 8 (linear economy work) | Fixed. Name lookup uses a cached map, and `/baltop` is cached for 15 seconds and invalidated on any balance change |
| 9 (no economy audit trail) | Fixed. Append-only `economy.log` written off-thread |
| 10 (unbounded moderation data) | Partly fixed. `moderation.retention-days` prunes finished punishments. IP history retention and a per-player erase command are not done |
| 11 (reload cancels tasks) | Host reloads everything when core reloads. Core still calls `cancelTasks` on stop |
| 12 (package-private singleton) | Unchanged. Only the economy is exposed |
| 13 (hard-coded values) | Unchanged |
| 14 (no source, tests or CI) | Source is now in the repo. There are still no automated tests or CI |

## How 2.2.0 was checked

The jar was built and run on a Paper 26.3 test server. Checked: startup, `/eco`, `/baltop`, `/ban`, `/tempban`, `/unban`, `/ban-ip`, `/wafflecore reload` and `/wafflecore reload core`. The login check was exercised with synthetic login events: a banned UUID was kicked, an unrelated player was allowed, an unbanned player was allowed, and an IP ban kicked a different account on that address. Flows that need a real player connected were not tested: GUIs, teleports, kits, the auction house, orders, mining, combat tagging and chat. Chat in particular changed event type, so watch mute, staff chat and auction search input on first use.
