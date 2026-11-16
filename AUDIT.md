# WaffleCore audit

Scope: `core.jar` (the version in this repo) and `WaffleHost-2.0.0.jar`, decompiled and read. The repo has no source, so every finding below comes from the decompiled classes. Items marked "fixed in 2.1" are addressed by the new host or modules. Everything else needs a change inside `core.jar`, which needs its source rebuilt first.

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

## What 2.1 changes

| Finding | Status |
| --- | --- |
| 3 (blocking, unsafe saves) | Pattern provided and used by all new modules; core itself unchanged |
| 4 (economy API) | Bridge for modules; core unchanged |
| 6 (chat events) | New `chatguard` uses `AsyncChatEvent` |
| 11 (reload cancels tasks) | Fixed in the host |
| 1, 2, 5, 7, 8, 9, 10, 12, 13, 14 | Need core source; not changed |
