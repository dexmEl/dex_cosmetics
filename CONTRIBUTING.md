# Contributing

## Build

Install a Java 21 JDK, clone the repository, and use its Gradle wrapper. No local IDE configuration is required.

```powershell
# Windows
.\gradlew.bat spotlessApply
.\gradlew.bat clean build
```

```sh
# Linux/macOS
./gradlew spotlessApply
./gradlew clean build
```

`build/libs/dex_cosmetics-<version>.jar` is the distributable remapped mod. The `-sources.jar` contains source. The build checks formatting and runs JUnit tests. `./gradlew test` runs regression tests alone; `./gradlew spotlessCheck` checks style. CI runs a clean build on Java 21. Initial compile-time dependency downloads include Cobblemon and can take several minutes. `./gradlew runServer -PwithCobblemon=true` includes it in the development server; the default runtime omits it to exercise the optional integration.

The wrapper, Loom, Kotlin, and dependencies are pinned. Keep version changes explicit and verify a clean build. Generated output, compiler logs, server worlds, and local preview images do not belong in Git.

## Architecture

- `cosmetic/CosmeticService`: current ownership checks, grants/revokes, equipment changes, item-skin application, and renderer refresh notifications. New commands and menu actions use this service.
- `data/`: synchronous SQLite storage and a server-thread cache. Storage succeeds before cache mutation. Keep multi-statement actions transactional; do not add fire-and-forget writes.
- `config/ConfigLoader`: reads candidate files without changing live state. `ConfigJson` preserves Kotlin defaults and rejects nulls; `DefinitionValidator` checks input; `CosmeticFactory` resolves Minecraft registries after tags load. `ConfigPaths` owns directory layout; `DefaultAssets` seeds missing files.
- `config/ConfigurationReloader`: stages settings/registry snapshots and restores them if model registration or pack generation fails. Restart-only fields must be checked before mutation.
- `render/`: packet/entity state and effect dispatch. Each renderer cleans its active and parked state on disconnect or world/player replacement.
- `polymer/`: resource-pack assets and item conversion. `PolymerCompatibility` contains version-sensitive API calls. It uses the public resource-pack creator, and configures autohost through its config file before startup. The remaining Polymer implementation reference checks its asynchronous build flag before a reload. Model allocations remain stable for the lifetime of the process.
- `gui/` and `command/`: presentation and user feedback. They do not decide ownership rules or perform database writes directly.

All player-state access happens on the server thread. Concurrent collections do not make multi-step game operations safe to run asynchronously. Tests cover failed writes/retries, reconnect durability, transactional revocation, ownership changes while a menu is open, invalid reloads, stable model IDs, asset completeness, and particle counts.

The default-asset index is generated from `src/main/resources/default_cosmetics/` during the build. Add files there without maintaining a second handwritten list. Include adjacent animation metadata and record asset sources in `ASSETS.md`.

## Runtime verification before a release

JUnit uses Fabric Loader's test launcher for Minecraft access/mixins, but does not run a complete server or client. It cannot verify packet ordering, client rendering, or autohost delivery. On a disposable Fabric 1.21.1 server:

1. Start with only required dependencies and a fresh config/world. Confirm example copying, pack generation, menu rendering, and joins from a vanilla client.
2. Grant/equip/revoke a hat and back item. Check immediate removal, helmet slot clicks, respawn, dimension changes, swimming, sleep, elytra, and disconnect/rejoin while the back item is hidden.
3. Open a skin menu, revoke the skin from console, and verify the old menu cannot apply it. Test skin removal and both sharing modes, including a full inventory or pickup delay.
4. Reload a valid definition/global setting. Then try duplicate IDs, invalid JSON, an invalid model transform, and a restart-only setting change. Verify rejection preserves the working configuration/pack and reports an error.
5. Check the built-in particle budget at zero and one. Test glow with/without another scoreboard team. Test LuckPerms denial and a server without LuckPerms.
6. Test auto and manual pack hosting across separate restarts. With Cobblemon installed, check Snowstorm on compatible clients and confirm packets are filtered for clients that cannot receive them.

Record Minecraft, Fabric, Polymer, and optional integration versions with release notes. Update `mod_version` deliberately for a release; cleanup work does not publish or tag a release automatically.
