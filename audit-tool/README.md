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
- master = the signed sum of all its ledger rows. Amounts are stored unsigned, so the sign comes from the type code.
- slave, `TO_MASTER` = REQUESTED − RETURNED. It is 0 when REQUESTED has no follow-up row: a non-success reply writes
  no step, so nothing was applied.
- slave, `FROM_MASTER` = −DELIVERED. For ASYNC_BANK, DELIVERED means the master reported the debit as SUCCESS. It
  does not prove the items reached the slave world: download and terminal hand the items out before sending and take
  them back on failure without a journal row, and CRAFT_CONSUME hands nothing out. For a DROP, DELIVERED is what was
  dropped in the slave world and FAILED is what was not.

A follow-up row with no `items_json` means the full requested amount. Only the converter writes partial follow-ups:
CONFIRMED is the applied part and RETURNED the refunded part. FAILED moves nothing. For a DROP, the master's
Σ WITHDRAW − Σ WITHDRAW_REFUND equals the DELIVERED total, and the FAILED total equals the WITHDRAW_REFUND total.

Only totals per transfer are compared, never per-item units: ASYNC_BANK `items_json` is {bank-slot ItemID: raw}
(bound external coins at the provider ratio, keyed by the base-coin slot), CONVERTER_DEPOSIT is {money ItemID: cents},
and a DROP is worth × count for notes and count × 100 for other items. REQUESTED is written after the send, so a
master row can be older than the slave's REQUESTED. That is not an error.

Journal transfers whose REQUESTED row has `ledger_type` NULL are skipped when the master has no row for them. They
come from AsyncBank NO_LOG calls (StockMarket settlement), and the master writes no ledger row for those by design.
DROP rows never have a `ledger_type` and are never skipped.

| Class | Meaning |
|---|---|
| `ok` | Both sides agree. This includes partial converter deposits, rejections whose items were returned, a REQUESTED with no follow-up row (`not applied`), and a DROP with only FAILED rows and no master row (the leg was passed on to the next slave). |
| `parked` | Open admin case (TRANSFER_PARKED flag). Shows `acknowledged` once the admin has ticked it. |
| `duplicate` | Both sides have rows, but more value arrived than left. Examples: applied and RETURNED; REFUNDED after DELIVERED. |
| `lost` | Value left one side and never arrived. Examples: a debit with no slave row; REQUESTED + TIMED_OUT with no master row; FAILED without a refund. |
| `created from nothing` | The slave received value, but the master has no row for it. |
| `known gap` | Not a finding. (1) A DROP on a slot bound to an external currency whose totals differ: the master row is count × provider ratio (e.g. 81 for bound gold coins), the slave journal count × 100, because the slave does not know the ratio. Detected from the master's `BankSystem/BankAccountBindings.nbt` (slot bound at save time, before the row); a slot unbound before the save is not detected and shows as lost / duplicate. (2) Worlds written before BankSystem #136 only: a DROP with DELIVERED rows but no master row, because user-deletion payouts wrote the master ledger with `ref = null` (#127); or a linked deletion payout (ACCOUNT_DELETE_PAYOUT/LOSS) whose DELIVERED total does not match, because the payout dropped raw balances as item counts (100× the value). Since #136 each slave leg has `ref = transfer_id`. |
| `data gap` | Replaces lost / created from nothing when the side without rows cannot show the transfer. The master or slave save is not loaded; a slave has no diagnostics DB, or its file started after the transfer (the earlier of `Meta.created_ms` and its first row's `ts`, since the first rows can predate `created_ms`); or the master ledger has no `ref` column. |
| `after snapshot` | Replaces lost / created from nothing when the transfer is newer than the oldest loaded save (`level.dat` `Data.LastPlayed`). That copy may simply predate it. |

The `note` column adds `master ledger rows may have been rolled back` when a matched ledger row is newer than
`Meta.bank_last_save_ms`. A crash before the next bank save undoes the balance change but keeps the row. It is only a
"may": the key can be missing or stale. The async queue can drop it on overflow or when the shutdown drain passes 2 s,
a timer save with clean data does not update it, and the timer runs only while players are online. Slaves never
write it. Without the key the join warns `no bank-save info`. A world without `level.dat` has no last-save info. That
world is reported, and it does not count toward "after snapshot".

Limitation: a master row joins when its `ref` is a canonical UUID. BankSystem writes transfer ids that way and
StockMarket refs are prefixed (`sm:`), but the public `ITransactionLedger.log(ref)` accepts any string, so an add-on
that writes a bare UUID ref is joined as a transfer. TRANSFER and UNKNOWN_LEGACY rows of rejected transfers are signed
and have `ref = null`, so they never join.

The candles view shows 1-min candles (close = next open), with the player's buys as blue up-triangles and sells as
magenta down-triangles. Log price scale is on by default.

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
  - TRANSFER (3) amounts are signed. Legacy `TRANSFER_OUT` rows are negated on load, the same way the BankSystem
    migration does it. PAYOUT (20) and DIVIDEND (21) rows are signed per leg when `balance_after` is set; older rows
    (`balance_after` NULL) are unsigned on both legs and count as unknown sign. SHARE_TRADE (24) stays unknown sign:
    withdrawals are negative in new worlds, but a positive row can be a deposit or an older withdrawal.
- **Reconciliation:** the bank delta between two BalanceHistory snapshots is compared with the explained flow.
  - Trade flow comes from the ledger TRADE_BUY/SELL rows (`money_amount`, `currency_item`) when they exist. Fills
    before the first ledger trade row, or all fills in 2.1.2 worlds, come from OrderHistory.
  - Signed non-trade ledger rows are added to that. Rows with an unknown sign (ADMIN_SET, SHARE_*, legacy PAYOUT/DIVIDEND) are not counted;
    they only label the interval.
- **virtual_qty:** when it is present, the report splits each account's trade money into money from the virtual
  or bot book and player-to-player money.
- `SuspicionFlag` table and `BankSystem/audit/*.log`: the table is dumped generically, and the logs are listed
  (their schema is not final yet).
- **Diagnostics DBs** are optional and write-only for the mods. Older worlds have none, and the tool then shows
  "no diagnostics data" for the missing file or table.
  - `BankSystem/Database/banksystem_diagnostics.db`:
    - `Meta`: `schema_version`; `created_ms` (set when the writer thread first opens the file, so the first rows can
      be older); `bank_last_save_ms` (last successful `Bank_data` save; may be missing or stale, never on slaves).
    - `TransferJournal`: slaves only.
    - `NoteFlow`: notes created (+) or destroyed (−) outside the bank. The villager columns `villager_uuid`,
      `dimension`, `x`, `y`, `z` are read when present.
  - `StockMarket/Database/stockmarket_diagnostics.db` (master only):
    - `Meta`.
    - `OrderOrigin(ref, origin_server_uid, origin_name)`. `ref` is `sm:order:<order_id>`, or `sm:im:<group uuid>` for
      inter-market orders. `origin_name` is the slave name, and `''` means the order was placed on the master.
    - Files from dev builds before 1d617b6c also hold empty `TransferJournal`/`NoteFlow` tables. They are ignored.
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
