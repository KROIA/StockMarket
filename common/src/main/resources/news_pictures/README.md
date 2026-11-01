# Baked default news pictures

PNG files dropped into this folder are bundled into the mod jar and used as the
**baked default pictures** for the shipped news events.

How it works (`DefaultNewsPictures`):

- On reload, if `config/StockMarket/news/pictures/` contains no `.png`, the server
  extracts one default picture per shipped event id (`<eventId>.png`).
- For each event id, a jar resource `news_pictures/<eventId>.png` **takes precedence**
  over the procedural placeholder generator. No code change needed — just drop the
  file here with the exact event id as its name.
- If no resource exists for an id (this folder currently ships no art), a
  deterministic square grayscale placeholder is generated instead.

Constraints (enforced by `NewsPictureLibrary`, invalid files fall back to the
generator with a warning):

- PNG format only
- max file size: 128 KiB (load-bearing for the network batching design — do not raise)
- width/height: 16..512 px each; square (1:1) is expected by the UI display boxes

Expected file names (see `DefaultNewsEvents.DEFAULT_EVENT_IDS`):

- `diamond_rush.png`
- `iron_supply_disruption.png`
- `ore_market_crash.png`
- `gold_reserve_standard.png`
- `emerald_counterfeit_scandal.png`
- `netherite_insider_leak.png`
- `redstone_breakthrough.png`
- `lumber_construction_boom.png`
