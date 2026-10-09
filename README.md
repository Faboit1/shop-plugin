# DonutShop Plugin

A fully customizable DonutSMP-style shop plugin for Paper/Spigot 1.21+.

## Features

- **DonutSMP-style GUI** — Clean 9×3 layout with black glass pane borders, category icons, paginated item views
- **5 Shop Categories** — Blocks, Farming, Mob Drops, Food, Ores & Minerals
- **Normal Item Display** — Item names shown in normal Title Case formatting with configurable cost display
- **Insanely Customizable Config** — Change all items, sections, prices, add pages, modify GUI layout, colors, navigation buttons, sounds, and more
- **Dual Economy Support** — Works with Vault and CoinsEngine (CoinsEngine as fallback via reflection)
- **MiniMessage Support** — Full MiniMessage color/gradient/formatting in all text
- **Pagination** — Automatic page system for categories with many items (9 items per page)
- **Buy & Sell** — Left-click to buy, right-click to sell, shift+click for stacks, middle-click to sell all
- **Configurable Navigation** — Customize back/prev/next/close button materials, names, slots, and toggle visibility
- **Sound Effects** — Configurable sounds for buy, sell, error, navigation, and menu open
- **Transaction Settings** — Configurable shift-click amounts and middle-click sell-all toggle

## Halloween Event

Configured in `halloween.yml` (reload with `/shop reload`):

- **Pumpkin currency** — players earn 🎃 for playtime (default 1 every 5 minutes, idle players don't earn). Paid into an ExcellentEconomy currency (`pumpkins`), with a chat message and sounds on every reward.
- **Intro dialog** — a native Paper dialog opens 10 seconds after joining (once per player by default) explaining the event, with a button that opens `/shop halloween`.
- **Halloween shop category** — a normal category with a `currency` override and command items, so pumpkins buy money and crate keys.
- **`/halloween`** — shows your pumpkin balance, time until the next one, and reopens the dialog.

Categories can also override messages with a `messages:` section (`buy-success`, `not-enough-money`, `inventory-full`), e.g. to say "pumpkins" instead of "money".

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop` | Open the shop GUI | `donutshop.use` |
| `/shop <category>` | Open a specific category directly | `donutshop.use` |
| `/shop reload` | Reload the config | `donutshop.reload` |
| `/halloween` | Halloween event info and pumpkin progress | `donutshop.use` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `donutshop.use` | Access to the shop | Everyone |
| `donutshop.reload` | Reload config | OP |
| `donutshop.admin` | Full admin access | OP |

## Configuration

All configuration is in `config.yml`. You can customize:

- **Economy provider** — Choose between Vault, CoinsEngine, or auto-detect
- **Currency symbol** — Customize the currency display symbol
- **Messages** — All messages with MiniMessage formatting and `{prefix}` support
- **Main menu** — Title, size, filler material, category icon positions
- **Categories** — Add/remove categories, change icons, lore, glow effects
- **Shop items** — Add/remove items, set buy/sell prices, custom slots, custom model data
- **Pages** — Items auto-paginate, or set specific slot positions
- **Navigation buttons** — Customize material, name, slot, and visibility for back/prev/next/close/page-info
- **Sound effects** — Configure sounds for buy, sell, error, navigation, and menu open (set to "NONE" to disable)
- **Transaction settings** — Configure shift-click amounts and middle-click sell-all toggle
- **Item lore format** — Customize the hover tooltip with `{cost}`, `{buy}`, `{sell}`, `{item}` placeholders

## Dependencies

- **Required:** Paper 1.21.6+ (or compatible fork; dialogs need 1.21.6+)
- **Optional:** Vault (with an economy provider), CoinsEngine, ExcellentEconomy (needed for the pumpkin currency)

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/DonutShop-1.0-SNAPSHOT.jar`.
