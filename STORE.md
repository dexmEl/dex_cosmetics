# Dex Cosmetics

**Hats. Wings. Glowing trails. Custom sword skins. All server-side. Your players don't install a thing.**

A complete cosmetics system for your Fabric Cobblemon server — drop in the mod, restart, and your players have a full cosmetics menu instantly.

## What players see

Run `/cosmetics`. A clean custom menu opens with four categories:

- **Hats** — custom 3D models worn on the head
- **Back items** — wings, capes, backpacks, anything that hangs off the back
- **Effects** — glowing outlines, particle trails, custom emitters
- **Item skins** — reskin any sword, pickaxe, tool with a custom model

Click a cosmetic you own. It equips instantly. No commands to memorize and no chat clutter.

## 100% server-side

Your players can use vanilla for the standard cosmetics or a Cobblemon client for Snowstorm effects. **No Dex Cosmetics client install.** Hats, back items, glow, and vanilla particles work with vanilla clients; Snowstorm effects require a Cobblemon client. The mod auto-serves a tiny resource pack on connect via Polymer.

## Drop-in crate / shop integration

Already using a crate plugin, GUI shop, or loot table system? Cosmetics are granted with one console command:

```
/cosmetics give <player> <cosmetic_id>
```

Plug it into whatever you already use. Owned cosmetics are stored in SQLite.

## Config-driven

Every cosmetic is one JSON file plus a Blockbench-exported model and a PNG texture. Drop them in `config/dex_cosmetics/` and you're good to go.

## Real cosmetic types — not just hats

- **Hats** ride your players' real head bone while still allowing them to equip armor to their helmet slot, that also means head rotation and looking up/down work perfectly.
- **Back items** attached to player as invisible armor-stand passenger and follows movement flawlessly, auto-hiding when you swim, sleep, or use elytra.
- **Item skins** swap a sword's model for a custom one - keep the stats, change the look. Each player applies skins to their own gear.
- **Glow effects** give your players colored outlines visible through walls — use one of 16 vanilla outline colors, with RGB values mapped to the nearest palette color.
- **Snowstorm particles** play full Bedrock-format `.particle.json` effects via Cobblemon, with custom textures, motion, and lifetime.
- **Vanilla particles** play custom particle emitters via commands in config.

## Server admin features

- **Per-player ownership** — SQLite-backed, persistent, fast. Players see the full catalog but can only equip what they own.
- **Permissions integration** — LuckPerms-aware for admin commands (`give`, `take`, `list`, `reload`). Falls back to op-level if LuckPerms isn't installed.
- **Skin sharing toggle** — choose whether dropped skinned items keep their skin when picked up by someone else.
- **Auto-name items** — optional config to rename skinned items to the cosmetic's display name on hover.
- **Auto OR manual pack delivery** — let polymer-autohost serve the pack automatically, or flip to manual mode and host `polymer/resource_pack.zip` yourself on your CDN / web server. `/cosmetics generatepack` rebuilds on demand.
- **Configurable CustomModelData start** — set the base CMD value (default 2000) so the mod's allocations don't collide with other resource packs or item-NBT mods already on your server.

## Quick start

1. Drop the mod into `mods/` along with Fabric API, Fabric Language Kotlin, Polymer, and placeholder-api. sgui is already bundled.
2. Start the server — example cosmetics generate on first run.
3. Players connect, accept the auto-served pack, type `/cosmetics`.

Bundled out of the box: a Pikachu hat, Giratina origin form arms back piece, Giratina sword skin, soul-aura particle trail, snowstorm spark effect, and 16 glow-outline color variants. Everything you need to see the system working — and a template for your own.
