# DonutShop Plugin

A fully customizable DonutSMP-style shop plugin for Paper/Spigot 1.21+.

## Features

- **DonutSMP-style GUI** — Clean black glass pane borders, category icons, paginated item views
- **9 Shop Categories** — Blocks, Farming, Mob Drops, Food, Ores & Minerals, Redstone, Dyes & Colors, Miscellaneous, Treasures
- **Small Capital Letters** — All text displayed in Unicode small caps (ʟɪᴋᴇ ᴛʜɪs)
- **Insanely Customizable Config** — Change all items, sections, prices, add pages, modify GUI layout, colors, and more
- **Dual Economy Support** — Works with Vault and CoinsEngine (CoinsEngine as fallback via reflection)
- **MiniMessage Support** — Full MiniMessage color/gradient/formatting in all text
- **Pagination** — Automatic page system for categories with many items (28 items per page)
- **Buy & Sell** — Left-click to buy, right-click to sell, shift+click for stacks, middle-click to sell all

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop` | Open the shop GUI | `donutshop.use` |
| `/shop <category>` | Open a specific category directly | `donutshop.use` |
| `/shop reload` | Reload the config | `donutshop.reload` |

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

## Dependencies

- **Required:** Paper 1.21+ (or compatible fork)
- **Optional:** Vault (with an economy provider), CoinsEngine

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/DonutShop-1.0-SNAPSHOT.jar`.
