# Baked default news pictures

PNG files dropped into this folder are bundled into the mod jar and used as the
**baked default pictures** for the shipped news events.

How it works (`DefaultNewsPictures`), on every reload:

- **Upgrade:** an existing `config/StockMarket/news/pictures/<eventId>.png` that differs
  from the current picture is replaced with it only if the file is still exactly a
  version the mod shipped before: an earlier picture from this folder (its SHA-1 is
  listed in `legacy_hashes.json`) or the procedural placeholder. Anything else is admin
  art and is never touched.
- **Missing files:** a default `<eventId>.png` that does not exist in the config folder
  is written.
- For each event id, a jar resource `news/pictures/<eventId>.png` **takes precedence**
  over the procedural placeholder generator. No code change needed — just drop the
  file here with the exact event id as its name.
- If no resource exists for an id (today every default event ships one), a
  deterministic square grayscale placeholder is generated instead.

When redrawn pictures ship, run `python scripts/update_legacy_picture_hashes.py` after
the new files are in place. It rebuilds `legacy_hashes.json` from git history, so the
replaced versions get upgraded on existing installs.

Constraints (enforced by `NewsPictureLibrary`, invalid files fall back to the
generator with a warning):

- PNG format only
- max file size: 128 KiB (load-bearing for the network batching design — do not raise)
- width/height: 16..512 px each; square (1:1) is expected by the UI display boxes

Expected file names: `<eventId>.png` for each id listed in `../index.json`.
