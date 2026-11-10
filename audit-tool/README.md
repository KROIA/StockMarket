# StockMarket / BankSystem offline audit tool

Read-only forensics on a **copy** of a world that runs StockMarket + BankSystem. Python 3 stdlib only
(sqlite3, tkinter, gzip/struct NBT, csv, argparse). It has a CLI and a thin Tkinter GUI over the same core,
and it also ships as two Windows exes.

## Usage

`<world>` can be the world dir (`world/`), `world/data/`, or any dir that holds `BankSystem/` and `StockMarket/`.

```
python -I audit-tool report  <world> --out report.md [--csv-dir out/] [--from 2024-01-01 --to 2024-01-02]
python -I audit-tool flags   <world> [--csv flags.csv]
python -I audit-tool pnl     <world> [--player <name>] [--item <id>] [--from ...] [--to ...] [--csv pnl.csv]
python -I audit-tool trades  <world> --player <name> [--item <id>] [--from "2024-01-01 12:00"] [--csv t.csv]
python -I audit-tool timeline <world> --player <name> [--item 6] [--min-delta 1000000]   (or --account <n>)
python -I audit-tool candles <world> --item <id> --from "2024-01-01 12:00" --to "2024-01-01 12:30" [--player <name>]
python -I audit-tool names   <world> [--items]
python -I audit-tool diag    <world> [--table meta|journal|noteflow|origin] [--from ...] [--to ...] [--csv d.csv]
python -I audit-tool join    <master world> <slave world> [<slave world> ...] [--csv transfers.csv]
python -I audit-tool gui     [<world>[;<world>...]]
python -I audit-tool selftest            (or: python -I audit-tool/selftest.py)
```

Run it as `python -I audit-tool ...` (directory execution). `python -I -m audit` does not work: `-I` drops the
current directory from `sys.path` on purpose, and the folder name has a dash.

The exes take the same arguments: `audit.exe report <world> --out report.md`, and `audit-gui.exe` opens the GUI.

- `--player` takes a name (case-insensitive) or a UUID prefix. Times are UTC: `YYYY-MM-DD[ HH:MM[:SS]]` or epoch ms.
- `--no-copy` opens the DBs in place with `mode=ro` instead of copying them to a temp dir. It is faster on the
  700 MB `stockdata.db`, but a hot journal can then not be rolled back.
- `report` takes 1 to 2 minutes on a large world (700 MB `stockdata.db`). Most of that time is the MarketPrice scans for typical prices and
  spikes. `--no-spikes` skips the spike scan.

### Anomaly flags (thresholds are CLI options)

| Flag | Rule | Option (default) |
|---|---|---|
| `pump_round_trip` | Same player buys, then sells on one item. The sell average price is more than N× the buy price, within W seconds, with no other player order on that item in between. | `--pump-ratio 2`, `--pump-window 30` |
| `order_rate` | More than N orders by one player inside any 1 s window. | `--max-rate 5` |
| `pnl_per_hour` | Realized P&L (average cost) per player and clock hour above $X. | `--pnl-hour 1000000` |
| `absurd_price` | A fill price more than N× or less than 1/N× the market's typical price. Typical = median 1-min candle open, falling back to the median fill price. | `--price-ratio 10` |
| `candle_spike` | A market with 1-min candles whose high is more than N× the low. | `--spike-ratio 5` |
| `unexplained_balance` | A BalanceHistory interval where the bank delta minus (trades + ledger) exceeds the threshold. | `--recon-threshold 1000` ($ or items) |
| `ledger_mismatch` / `ledger_missing` | New format only. The ledger rows for `sm:order:<id>` do not add up to the OrderHistory volume, or the order has no ledger rows at all. | n/a |

### GUI

Load a world. Pick a player, an ItemID and From/To, then click Trades, P&L, Timeline, Candles or Flags. Export CSV
writes the current table. For Transfers, load the master and its slaves together: separate the paths with `;` in the
path box, or add each one with `+ World`. All other views use the master.

## Multi-save join (`join`, GUI Transfers)

Load the master and its slave worlds together. The tool joins every cross-server transfer by `transfer_id`, and only by
id, never by time. Server roles and names come from `data/ModUtilities/server_identity.json`. A world without that
file counts as the master if it has `banksystem.db`, otherwise as a slave. The tool warns when two saves share a
`server_uid` (a copied world) or when a slave's `master_uid` is not the loaded master.

| Side | Source |
|---|---|
| Slave | `TransferJournal` rows in the slave's `banksystem_diagnostics.db`. Only slaves write the journal. |
| Master | `TransactionLog` rows whose `ref` is the transfer_id (a UUID), and `SuspicionFlag` rows with `flag_type` 6 (TRANSFER_PARKED) and that `ref`. |

Each transfer compares value in raw units, counted as value going into the master bank:
- master = the signed sum of its ledger rows;
- slave = REQUESTED − RETURNED for `TO_MASTER`, or −DELIVERED for `FROM_MASTER`.

A follow-up row with no `items_json` means the full requested amount. FAILED moves nothing.

| Class | Meaning |
|---|---|
| `ok` | Both sides agree. This includes partial converter deposits, and rejections whose items were returned. |
| `parked` | Open admin case (TRANSFER_PARKED flag). Shows `acknowledged` once the admin has ticked it. |
| `duplicate` | Both sides have rows, but more value arrived than left. Examples: applied and RETURNED; REFUNDED after DELIVERED. |
| `lost` | Value left one side and never arrived. Examples: a debit with no slave row; REQUESTED + TIMED_OUT with no master row; FAILED without a refund. |
| `created from nothing` | The slave received value, but the master has no row for it. |
| `known gap` | A DROP with no master row. User-deletion payouts (BankSystem #127) write the master ledger with `ref = null`, so the master row is not linked. Not a finding. |
| `data gap` | Replaces lost / created from nothing when the side without rows cannot show the transfer. The master or slave save is not loaded; a slave has no diagnostics DB, or its `Meta.created_ms` is after the transfer; or the master ledger has no `ref` column. |
| `after snapshot` | Replaces lost / created from nothing when the transfer is newer than the oldest loaded save (`level.dat` `Data.LastPlayed`). That copy may simply predate it. |

The `note` column adds `master ledger rows may have been rolled back` when a matched ledger row is newer than
`Meta.bank_last_save_ms`. A crash before the next bank save undoes the balance change but keeps the row. A world
without `level.dat` has no last-save info. That world is reported, and it does not count toward "after snapshot". The candles view shows 1-min candles (close = next open), with the player's buys as
blue up-triangles and sells as magenta down-triangles. Log price scale is on by default.

## Safety

- World data is treated as untrusted input. Nothing in it is executed or eval'd.
- DBs are copied to a temp dir and opened with `mode=ro` plus `PRAGMA query_only`. The originals are never opened
  for writing. If a copy has a hot journal, only the temp copy is opened writable, so SQLite can roll it back.
- **Malformed DBs** ("database disk image is malformed", common for copies taken while the server runs): a failing
  query is re-run over bisected id or time ranges. Every readable range is kept, and lost ranges are listed in the
  report's Warnings.
- The NBT parser caps decompressed size (256 MB) and nesting depth (512), and rejects list and array lengths that
  exceed the input. JSON files over 64 MB are skipped.
- Keep world copies, reports and CSV output in `audit-tool/local/` (git-ignored) or outside the repo. Never commit
  real player names, UUIDs or server names.
- Always run Python with `-I`, so a planted `sqlite3.py` or similar in the current directory cannot be imported.

## Build the exes

```
python audit-tool/build.py
```

The script creates `audit-tool/.venv` and installs PyInstaller there (pinned in `requirements-build.txt`, never
global). It builds `dist/audit.exe` (console) and `dist/audit-gui.exe` (`--windowed`, so no console window), each
`--onefile`, then runs `audit.exe selftest`.

**Why two exes:** a windowed exe has no stdout or stdin, so the CLI cannot print or pipe, and a console exe always
opens a console window when double-clicked. Hiding that window from inside a single console exe would need a
ctypes hack and still flashes the window, so two plain builds are simpler.

CI: `.github/workflows/audit-tool.yml` (windows-latest).
- A tag push `audit-tool-v*` builds the exes and attaches them to that tag's GitHub release. The tool has its own
  tag prefix so it never touches the mod's `v<version>` releases.
- `workflow_dispatch` builds and uploads an artifact only. It never publishes.

## Format notes

- **Units:** money raw = cents (`ITEM_FRACTION_SCALE_FACTOR = 100`). Item balances and order volumes are raw = items ×100.
  `OrderHistory.price` is cents per whole item, so the cents of a fill = `amount * price / 100` (Java truncation).
  All times are epoch ms, shown in UTC.
- **ItemID 32767** = `BalanceHistoryRecord.WEALTH_ITEM_ID`. It is a synthetic per-account total-wealth series
  (cash + items × price), shown as "wealth (synthetic)" and excluded from reconciliation.
- **Names:** BankSystem and StockMarket `users/chunk_*.nbt` (`userUUID` int[4], `userName`), then `usercache.json`
  (world dir, or up to two levels above it), then playerdata `bukkit.lastKnownName` (Paper/hybrid servers).
  Item names come from `BankSystem/ItemIDs.nbt` (`itemIDs[].itemID.id` → `itemStack.id`, `itemIDAliases[] {from,to}`),
  then from the `Meta_data.nbt` `itemIDShortNameDigest`.
- **OrderHistory** (`StockMarket/Database/stockdata.db`) has one row per completed player order. Bot and virtual
  orders are never recorded. `amount` > 0 is a buy and < 0 is a sell; `type` 0 = market, 1 = limit. The UUID is
  stored in `userid_one`/`userid_two` (msb/lsb). The optional `order_id` (or `orderId`) column is detected via
  PRAGMA table_info.
- **MarketPrice** has 1-min candles: `open, low, high, time, traded_volume` (no close).
- **BankSystem** (`BankSystem/Database/banksystem.db`):
  - `BalanceHistory` holds snapshots of balance and locked balance per account and item.
  - `TransactionLog` is the ledger. In 2.1.2 it has a `kind` text column. New worlds add `type_code`, `ref`,
    `money_amount`, `price`, `currency_item`, `virtual_qty`, `tag`, `balance_after` and `actor_name`, and all of
    them are detected.
  - **The type-code table is the single dict `LEDGER_TYPES` in `core.py`** (CODE TABLE v2 from the BankSystem ledger
    spec). `TODO(confirm)` marks the signs that still have to be confirmed.
  - Only TRANSFER (3) amounts are signed. Legacy `TRANSFER_OUT` rows are negated on load, the same way the BankSystem
    migration does it.
- **Reconciliation:** the bank delta between two BalanceHistory snapshots is compared with the explained flow.
  - Trade flow comes from the ledger TRADE_BUY/SELL rows (`money_amount`, `currency_item`) when they exist. Fills
    before the first ledger trade row, or all fills in 2.1.2 worlds, come from OrderHistory.
  - Signed non-trade ledger rows are added to that. Rows with an unknown sign (ADMIN_SET, SHARE_*) are not counted;
    they only label the interval.
- **virtual_qty:** when it is present, the report splits each account's trade money into money from the virtual
  or bot book and player-to-player money.
- `SuspicionFlag` table and `BankSystem/audit/*.log`: the table is dumped generically, and the logs are listed
  (their schema is not final yet).
- **Diagnostics DBs** are optional and write-only for the mods. Older worlds have none, and the tool then shows
  "no diagnostics data" for the missing file or table.
  - `BankSystem/Database/banksystem_diagnostics.db`:
    - `Meta`: `schema_version`; `created_ms` (first write to the file); `bank_last_save_ms` (last successful
      `Bank_data` save).
    - `TransferJournal`: slaves only.
    - `NoteFlow`: notes created (+) or destroyed (−) outside the bank. The villager columns `villager_uuid`,
      `dimension`, `x`, `y`, `z` are read when present.
  - `StockMarket/Database/stockmarket_diagnostics.db` (master only):
    - `Meta`.
    - `OrderOrigin(ref, origin_server_uid, origin_name)`. `ref` is `sm:order:<order_id>`, or `sm:im:<group uuid>` for
      inter-market orders. `origin_name` is the slave name, and `''` means the order was placed on the master.
    - BankSystem's writer may also add empty `TransferJournal`/`NoteFlow` tables to this file. They are ignored.
  - The report's Data table shows each file's row counts, the server identity and the last save. It also shows how
    many ledger rows are newer than `bank_last_save_ms` ("may have been rolled back").
- **Trades** have an `origin` column: the slave name, `master`, or empty. It is empty for bot orders, orders placed
  before OrderOrigin existed, and test orders created without a request. The BalanceHistory timeline has no order refs,
  so it shows no origin.

## Known gaps

- OrderHistory has one row per completed order, not per fill. Partial limit-order fills and the ledger's 10-minute
  PERIODIC rows land at different times than the balance snapshots, so expect small, paired residuals around large orders.
- Realized P&L uses average cost per player and item. Sells beyond the bought position (mined or deposited items)
  are reported as `unmatched_sold` and not as profit.
- No counterparty data exists in 2.1.2 worlds, so virtual-vs-player money is only available with `virtual_qty`.
- Market state (target price, net flow, plugin settings) is only on disk in `MarketManager/markets/*` chunks, and
  the tool does not read those yet. Region and chest contents are not scanned for banknotes; only playerdata is.
