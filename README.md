# Dex Cosmetics

A server-side cosmetics mod for Fabric 1.21.1: hats, back items, item skins, and visual effects. Polymer supplies custom models and menus through a resource pack. Hats, back items, glow, and vanilla particles work with vanilla clients. Snowstorm effects require Cobblemon on both server and client; clients do not need Dex Cosmetics.

## Requirements and installation

Use Java 21 or newer and Fabric Loader 0.19.2 or newer. Install these alongside the mod:

| Dependency | Tested version | Installation |
| --- | --- | --- |
| Fabric API | 0.116.12+1.21.1 | Required |
| Fabric Language Kotlin | 1.13.11+kotlin.2.3.21 | Required |
| Polymer core, resource pack, virtual entity, autohost | 0.9.19+1.21.1 | Required Polymer modules |
| placeholder-api | 2.4.2+1.21 | Required; may be supplied by your dependency bundle |
| sgui | 1.6.1+1.21.1 | Already bundled in the mod JAR |
| LuckPerms | API 5.4 | Optional permissions integration |
| Cobblemon | 1.7.3+1.21.1 | Optional; needed for Snowstorm effects |

1. Put the built mod JAR and required dependencies in the server's `mods/` directory.
2. Start the server. It creates `config/dex_cosmetics/` with settings, examples, and editable GUI art.
3. Configure Polymer autohost for your network. Players must be able to reach its advertised pack URL.
4. Join, accept the resource pack, and run `/cosmetics`.

The mod marks its Polymer pack required. `resource_pack_mode` controls Polymer autohost's `enabled` setting and sets `setup_early=false` so the startup build follows model registration. Pack delivery, network addresses, and acceptance handling belong to Polymer. For manual hosting, set `resource_pack_mode` to `manual`, restart, publish `polymer/resource_pack.zip`, and set `resource-pack` and `require-resource-pack=true` in `server.properties`.

## Configuration

Global settings live in `config/dex_cosmetics/config.json`:

```json
{
  "particle_tick_budget_per_player": 32,
  "admin_permission": "dexcosmetics.admin",
  "allow_item_skin_sharing": true,
  "rename_item_to_skin_name": false,
  "resource_pack_mode": "auto",
  "custom_model_data_start": 2000,
  "menu_categories": {
    "ITEM": true,
    "HAT": true,
    "BACK": true,
    "EFFECT": true,
    "BALLOON": false,
    "POKEMON": false
  },
  "menu_focus": "HAT"
}
```

| Setting | Behavior | Apply changes |
| --- | --- | --- |
| `particle_tick_budget_per_player` | Maximum built-in style particles emitted by one player in one tick, 0..4096. Zero disables those particles. Raw command output and Cobblemon emitter internals are outside this cap. | Reload |
| `admin_permission` | LuckPerms node for admin commands. Without LuckPerms, permission level 2 is required. An explicit LuckPerms denial is respected. | Reload |
| `allow_item_skin_sharing` | Keep skins on items picked up by players who do not own the skin. | Reload |
| `rename_item_to_skin_name` | Use the cosmetic display name on the client copy of a skinned item. Does not rename the stored item. | Reload |
| `resource_pack_mode` | `auto` uses autohost; `manual` leaves delivery to the server owner. | Restart |
| `custom_model_data_start` | Starting model ID per carrier item, 1..1000000. Pick a range clear of your other packs. | Restart |
| `menu_categories` | Ordered enabled carousel categories. Four or more gives the intended animation. BALLOON and POKEMON are menu placeholders. | Restart, because order changes font codepoints |
| `menu_focus` | Initially focused enabled category, falling back to the first enabled entry. | Reload |

`/cosmetics reload` rereads global settings and definitions, validates the complete candidate, rebuilds the pack, and refreshes online players' equipped cosmetics. Invalid files or restart-only changes reject the reload. Registration/build failure restores the previous configuration and last pack file. Players must reconnect to download changed pack content. A player whose cosmetic definition disappeared is unequipped; ownership is retained so restoring that definition restores access.

### Migration from earlier builds

Remove `require_resource_pack`, `resource_pack_kick_message`, and `autosave_interval_seconds`. They previously had no effect and are now ignored with a log notice. Pack acceptance is handled by Polymer, or by `server.properties` in manual mode. Cosmetic changes are committed immediately to SQLite; there is no periodic autosave queue. Invalid definitions that were previously silently skipped now reject startup/reload with a filename and reason. Cosmetic IDs must use lowercase letters, digits, `_`, or `-`.

Existing databases and valid definition files keep their format. Model IDs are retained throughout a running process, including across reloads; removed IDs are not recycled until restart. New models are appended. Initial allocation uses sorted cosmetic IDs. Already connected clients still need the updated pack after adding/changing content.

## Adding cosmetics

One JSON file per cosmetic, under `config/dex_cosmetics/cosmetics/<category>/<id>.json`:

| Directory | Type | Behavior |
| --- | --- | --- |
| `hats` | `HAT` | Fake helmet equipment; real helmet inventory remains intact |
| `back` | `BACK` | Invisible armor-stand passenger; hides during swimming, sleep, elytra flight, and spin attack |
| `items` | `ITEM` | A skin applied to a compatible held item |
| `effects` | `EFFECT` | Vanilla particles, team-colored glow, or Cobblemon Snowstorm |

```json
{
  "id": "my_hat",
  "type": "HAT",
  "display_name": "<gold>My Hat</gold>",
  "description": ["An example hat"],
  "model": "dex_cosmetics:hats/my_hat",
  "texture": "dex_cosmetics:hats/my_hat/my_hat",
  "model_parent": "minecraft:item/generated",
  "transform": {
    "offset_x": 0, "offset_y": 0, "offset_z": 0,
    "scale_x": 1, "scale_y": 1, "scale_z": 1
  }
}
```

Place a Blockbench Java item/block model at `model/hats/my_hat.json`, and its texture at `textures/hats/my_hat/my_hat.png`, under the same config directory. Without a model file, a simple parent/layer0 model is generated. Texture paths map to `assets/dex_cosmetics/textures/` in the pack; adjacent `.png.mcmeta` files pass through for animation. Hats bake offset/scale into `display.head`. Back items use `scale_x` uniformly; other back transforms are not supported.

Item skins need `item_skin: { "compatible_items": ["minecraft:diamond_sword", "#minecraft:swords"] }`. IDs and populated tags are validated after server tag loading. Use a handheld model with appropriate `display` transforms for tools. Skin application always checks current ownership and compatibility, including when the menu was already open during a revocation.

Effects use `effect.kind`:

- `particle`: `particle.style` is `point`, `trail`, `halo`, `orbit`, or `square`, with `particle_type`, `count`, `radius`, offsets, and an optional dust `color`. Alternatively, omit `style` and provide `command` or `commands`. Commands run as the wearer at permission level 4 with silent feedback; only trusted administrators should author them. Commands do not inherit the built-in style budget.
- `glow`: `glow.color` is a six-digit hex color or one of the 16 vanilla color names. The outline uses the nearest vanilla team color. Existing non-Dex scoreboard teams take precedence. Glow refreshes once per second.
- `snowstorm`: `snowstorm.particle_id` references a Bedrock `.particle.json` under `snowstorm_particles/`; `interval_ticks`, offsets, and `broadcast_radius` control dispatch. Cobblemon controls the emitter simulation on compatible clients.

Use the bundled definitions as complete examples. Counts are limited to 1..4096, intervals to 1..72000 ticks, particle radius to 0..64 blocks, offsets to -64..64, and scale to 0.01..16. Explicit JSON nulls, duplicate IDs, unknown items/tags, and unsupported effect kinds are errors.

Editable carousel art lives under `gui/cards/`, plus `gui/background.png` and `gui/frame.png`. The motion templates remain bundled. The optional [preview tool](tools/menu-preview/README.md) helps inspect card layout.

## Commands and storage

| Command | Access | Behavior |
| --- | --- | --- |
| `/cosmetics` | Everyone | Open the menu |
| `/cosmetics remove` | Everyone | Remove the held item's skin |
| `/cosmetics give <player> <id>` | Admin | Grant ownership |
| `/cosmetics take <player> <id>` | Admin | Revoke ownership and refresh rendering |
| `/cosmetics list <player>` | Admin | List owned cosmetics |
| `/cosmetics reload` | Admin | Reload supported settings/definitions and rebuild the pack |
| `/cosmetics generatepack` | Admin | Rebuild pack content without reloading definitions |

Shops/crates can use the grant and revoke console commands. Multi-player selectors commit one player at a time; an error can leave earlier players completed, and is reported in chat and the server log.

Ownership/equipment is stored in `<world>/dex_cosmetics/cosmetics.db`. Small SQLite writes complete on the server thread before success is reported and before the cache changes. Failed writes leave that action's cache state intact and can be retried. Revocation removes ownership and equipment in one transaction. This deliberately favors durability and simple ordering; slow disks can increase command/menu latency. Back up the world with the server stopped, including the database and any SQLite sidecar files.

## Development and license

See [CONTRIBUTING.md](CONTRIBUTING.md) for build commands, architecture, tests, and release checks. The existing [CC0-1.0 license](LICENSE) is retained; [ASSETS.md](ASSETS.md) records asset provenance separately.
