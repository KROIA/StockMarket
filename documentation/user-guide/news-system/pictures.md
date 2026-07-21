# News Event Pictures

News events can carry a picture that is printed with the headline -- in the newspaper, in the event details screen of the plugin management window, and as a thumbnail next to active events. Pictures are optional per event and fully admin-controlled: you drop PNG files into a config folder and reference them by file name from the event JSON described in [Configuring News Events](configuration.md).

## The Pictures Folder

All event pictures live in one drop-in folder next to the event JSON files:

```
config/StockMarket/news/pictures/
```

- The folder is created automatically. If it contains **no `.png` file** at load time (first run, or after you emptied it and ran `/stockmarket news reload`), the mod generates a set of **default pictures** -- one per shipped example event (see [Default Pictures](#default-pictures)). A folder that already contains any `.png` is never touched.
- The folder is rescanned on every `/stockmarket news reload` (and on server start), and picture problems are printed in the same validation report as the event problems.
- File names are the reference key: events point at pictures by their plain file name.

## Referencing a Picture from an Event

One optional field on the event:

```json
{
  "id": "diamond_rush",
  "picture": "diamond_rush.png",
  "headline": { "en_us": "Diamond rush in the northern mountains!" },
  ...
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `picture` | string | -- | Bare file name of a PNG inside `config/StockMarket/news/pictures/`. Absent = text-only event (the pre-picture behavior). |

The value must be a **file name, never a path**. Rejected values (validation error, the field is ignored but the **event still loads** -- a bad picture never kills an event):

- anything containing `/`, `\`, `..` or `:`,
- names starting with a dot,
- names not ending in `.png` (case-insensitive),
- non-string values.

Referencing a file that does not exist (yet) is only a **warning**: the event loads and simply publishes without a picture. The name is resolved against the folder **at publish time**, so dropping the file in later and reloading fixes future publishes without touching the JSON.

## File Requirements

| Requirement | Limit |
|-------------|-------|
| Format | PNG only (any color type and bit depth -- color images are fine, see below) |
| File size | at most **128 KiB** |
| Dimensions | **16 to 512 px** per side |
| Shape | **square (1:1) recommended** -- all display boxes are square |

A file that violates these limits produces an **error** in the reload report and is skipped; events referencing it load normally and publish picture-less (with a warning). Nothing is ever resized or rewritten on the server -- fix the file and reload.

The 128 KiB cap is deliberate and load-bearing: it is what lets clients fetch pictures in small batches without any chunking protocol. It will not be raised.

## The Newsprint Look

You can author pictures in **full color** -- every picture is automatically converted on the client when it is displayed: the image is reduced to its luminance and remapped onto the newspaper's ink-on-paper tones, with transparency preserved. Any source image gets the exact print look of the news screen, and all pictures on a page match visually. There is no way to opt out of the conversion; if you want control over the result, author in grayscale and check it in-game.

Because every display surface uses a **square box**, square sources render in full. Non-square sources are center-cropped to a square in the newspaper and the details screen, and scaled down whole (letterboxed) in the Active-tab thumbnails.

## Where Pictures Appear

- **The newspaper** ([news screen](overview.md#the-news-screen)) -- a square picture block between the headline and the article text. Text-only entries look exactly as before.
- **The event details screen** -- opened by clicking an event in the NewsPlugin's [management window](overview.md#the-news-plugin-management-window) (admins).
- **The Active tab** of the management window -- a small thumbnail on each active event's row (admins).

While a picture is still downloading, a flat paper-tone placeholder box of the final size is shown -- the layout never jumps when the picture arrives.

## Default Pictures

The shipped example events each reference a picture named after their event id (`diamond_rush.png`, `ore_market_crash.png`, ...). These defaults **self-heal per id**: on every reload, any shipped default picture missing from `pictures/` is written as `<eventId>.png` -- currently as **procedurally generated placeholders**: deterministic 384x384 grayscale newsprint motifs (skylines, mountains, bar charts), seeded by the event id so every server generates the same images. An existing `<eventId>.png` is **never** overwritten, so your own art and any default you modified are always preserved. They are meant to be replaced: overwrite them with your own art (keeping the file names) or edit the `picture` fields to point at your own files.

Two notes for existing servers:

- **New default pictures arrive automatically.** Because the heal adds only the missing files, a server updating to a version with new default events receives their pictures on the next reload without touching your existing images. (The matching `picture` references heal into `default_events.json` the same way -- see [Getting the New Default Events](advanced-events.md#getting-the-new-default-events).)
- To restore a single default you changed, delete that one `<eventId>.png` and reload; it is regenerated. To run entirely without default pictures, remove the `picture` fields from the events instead.

(For mod/modpack developers: a real PNG placed in the jar under `news_pictures/<eventId>.png` automatically takes precedence over the procedural generator -- no code change needed.)

## Publishing, History and Syncing

Pictures follow the same "snapshot at publish" rule as the rest of an event:

- When an event publishes, the picture file's **current bytes are snapshotted** into the world data (`world/data/StockMarket/News/pictures/`, stored by content hash) before the headline goes out. Swapping or deleting the config file afterwards only affects **future** publishes -- every history entry keeps exactly the picture it was published with, across restarts. Snapshots of pruned history entries are cleaned up automatically.
- If the referenced file went missing between reload and publish, the publish logs one warning and goes out picture-less -- a picture problem never blocks or delays a publish.
- **Clients fetch pictures automatically** -- there is nothing to install or configure on the client side. Pictures are requested in small batches (visible newspaper entries first, the rest prefetched in the background) and are **rate-limited per player** on the server, so a client catching up on a large picture history trickles it in over a few minutes instead of bursting. On [master/slave setups](overview.md#masterslave-servers) the requests pass through the slave to the master like all other news traffic.
- **Per-player rate limits (server-side):** each request carries at most **8 hashes**; a sliding **60-second window** caps every player at **60 hashes** and **4 MiB** of picture bytes. Over-limit requests get an empty response and one warning log per window; the client backs off and retries after the window slides. These limits are sized so a catch-up on a full 500-picture history trickles through in roughly ten minutes while keeping a misbehaving client from hammering the store's disk reads.
- Fetched pictures are cached **in memory for the session only**; nothing is written to the client's disk, and the cache is dropped on disconnect.
