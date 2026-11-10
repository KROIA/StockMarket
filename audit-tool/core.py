"""Core of the offline StockMarket + BankSystem audit tool. Shared by the CLI (__main__.py) and GUI (gui.py).

Units (both mods): money raw = cents, item balances and order volumes raw = items x100, times = epoch ms UTC.
OrderHistory.price is cents per whole item, so money of a fill in cents = amount * price / 100.
"""
import bisect, collections, csv, datetime as dt, functools, glob, json, math, os, shutil, sqlite3, statistics, tempfile
import urllib.request, uuid

import nbt

SCALE = 100                # ITEM_FRACTION_SCALE_FACTOR
WEALTH_ITEM_ID = 32767     # BalanceHistoryRecord.WEALTH_ITEM_ID: synthetic per-account total-wealth series
MAX_JSON_BYTES = 64 * 1024 * 1024

# ---------------------------------------------------------------------------------------------
# BankSystem ledger type codes (TransactionLog.type_code). THE one table to edit when codes change.
# Source: .claude/specs/banksystem_ledger_api.md, "CODE TABLE v2" (2026-11-08). Codes are append-only (27+ may follow).
# sign = effect of `amount` on balance+locked of item_id:
#   +1 / -1 (amount is unsigned), 1 for TRANSFER (amount is already signed: - out / + in),
#   0 = no net change, None = unknown or absolute set (interval gets labelled, not explained),
#   'buy' / 'sell' = trade row: item_id moves +-amount, currency_item moves -+money_amount.
# TODO(confirm): final table follows once BankSystem #109 builds green; signs marked TODO are guesses.
# ---------------------------------------------------------------------------------------------
LEDGER_TYPES = {
    0: ('UNKNOWN_LEGACY', None),
    1: ('DEPOSIT', +1),
    2: ('WITHDRAW', -1),
    3: ('TRANSFER', 1),               # signed amount; other_account = counterparty
    4: ('TRADE_BUY', 'buy'),
    5: ('TRADE_SELL', 'sell'),
    6: ('WITHDRAW_REFUND', +1),
    7: ('ADMIN_SET', None),           # absolute set; balance_after holds the new value
    8: ('ADMIN_ADD', +1),
    9: ('ADMIN_REMOVE', -1),
    10: ('ADMIN_UNLOCK', 0),          # TODO(confirm): locked -> free leaves balance+locked unchanged
    11: ('ADMIN_ACCOUNT_EDIT', None),
    12: ('BANK_CLOSED', -1),          # TODO(confirm sign)
    13: ('CONVERTER_DEPOSIT', +1),
    14: ('CONVERTER_WITHDRAW', -1),
    15: ('UPLOAD_DEPOSIT', +1),
    16: ('DOWNLOAD_WITHDRAW', -1),
    17: ('CRAFT_CONSUME', -1),
    18: ('CRAFT_OUTPUT', +1),
    19: ('STARTING_BALANCE', +1),
    20: ('PAYOUT', -1),               # TODO(confirm): written on the source account only?
    21: ('DIVIDEND', +1),             # TODO(confirm sign)
    22: ('SHARE_STAMP', None),        # TODO(confirm sign)
    23: ('SHARE_REDEEM', None),       # TODO(confirm sign)
    24: ('SHARE_TRADE', None),        # TODO(confirm sign)
    25: ('ACCOUNT_DELETE_PAYOUT', -1),  # balance handed out as items on user deletion (outflow)
    26: ('ACCOUNT_FREEZE', 0),        # TODO(confirm): freeze moves nothing?
    27: ('ADMIN_DISALLOW_ITEM', -1),  # every holder's balance of a disallowed item is cleared
    28: ('ACCOUNT_DELETE_LOSS', -1),  # deleted user's balance that could not be handed out, destroyed
}
# Legacy `kind` text (worlds without type_code) -> (code, factor applied to the unsigned amount).
LEGACY_KINDS = {'DEPOSIT': (1, 1), 'WITHDRAW': (2, 1), 'TRANSFER_OUT': (3, -1), 'TRANSFER_IN': (3, 1),
                'PAYOUT': (20, 1), 'DIVIDEND': (21, 1), 'SHARE_STAMP': (22, 1), 'SHARE_REDEEM': (23, 1),
                'SHARE_TRADE': (24, 1)}
TRADE_CODES = {c for c, (_, s) in LEDGER_TYPES.items() if s in ('buy', 'sell')}
ORDER_ID_COLUMNS = ('order_id', 'orderId')   # persisted StockMarket order id (spec: order_id)
LEDGER_OPTIONAL = ('type_code', 'ref', 'money_amount', 'price', 'currency_item', 'tag', 'virtual_qty',
                   'source_kind', 'note', 'balance_after', 'actor_name', 'other_account')

# Diagnostics DBs (write-only for the mods, optional: older worlds have none). Meta keys: schema_version, created_ms
# (set when the writer thread first opens the file, so the first rows can be older, see World.diag_start); the
# BankSystem file also bank_last_save_ms (last successful Bank_data save; may be missing or stale, never on slaves).
# Only StockMarket files from dev builds before 1d617b6c also hold empty TransferJournal / NoteFlow tables; those
# are ignored, only its Meta and OrderOrigin are read.
DIAG_BS = 'BankSystem/Database/banksystem_diagnostics.db'
DIAG_SM = 'StockMarket/Database/stockmarket_diagnostics.db'
JOURNAL_COLS = ('id', 'transfer_id', 'ts', 'direction', 'path', 'step', 'account_number', 'player_uuid', 'items_json',
                'ledger_type', 'context', 'detail')
NOTEFLOW_COLS = ('id', 'ts', 'source', 'player_uuid', 'item_id', 'amount', 'context',
                 'villager_uuid', 'dimension', 'x', 'y', 'z')   # villager columns: BankSystem 0a6023c1, absent before
TRANSFER_PARKED = 6        # BankSystem SuspicionChecks.TRANSFER_PARKED: DROP outcome unknown, held for the admin


# ----------------------------------------------------------------------------------- helpers
def ts(ms):
    if not ms: return '0'
    try:
        return dt.datetime.fromtimestamp(ms / 1000, dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')
    except (OverflowError, OSError, ValueError):
        return str(ms)


def parse_time(s):
    """'YYYY-MM-DD[ HH:MM[:SS]]' (UTC) or epoch ms -> epoch ms. None passes through."""
    if s is None or s == '': return None
    if str(s).lstrip('-').isdigit(): return int(s)
    for f in ('%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M', '%Y-%m-%dT%H:%M:%S', '%Y-%m-%dT%H:%M', '%Y-%m-%d'):
        try:
            return int(dt.datetime.strptime(s, f).replace(tzinfo=dt.timezone.utc).timestamp() * 1000)
        except ValueError:
            pass
    raise ValueError('bad time %r (use YYYY-MM-DD HH:MM, UTC)' % s)


def usd(cents):
    return '$%s' % format(round(cents / 100, 2), ',.2f')


def qty(raw):
    return format(raw / SCALE, ',.2f')


def money_of(amount, price):
    """Cents moved by a fill, truncated toward zero like Java long division. + for buys (cost)."""
    v = abs(amount * price) // 100
    return v if amount >= 0 else -v


def longs_to_uuid(m, l):
    return str(uuid.UUID(bytes=(m & (2**64 - 1)).to_bytes(8, 'big') + (l & (2**64 - 1)).to_bytes(8, 'big')))


def ints_to_uuid(a):
    return str(uuid.UUID(bytes=b''.join((x & 0xffffffff).to_bytes(4, 'big') for x in a)))


Fill = collections.namedtuple('Fill', 'id item account uuid type amount price time g1 g2 order_id')


# ----------------------------------------------------------------------------------- world
class World:
    """Read-only view of one world copy. DBs are copied to a temp dir by default and opened mode=ro."""

    def __init__(self, root, copy=True):
        self.warnings, self.lost = [], []
        self.copy, self._tmp = copy, None
        root = os.path.abspath(root)
        for d in (root, os.path.join(root, 'data'), os.path.join(root, 'world', 'data')):
            if os.path.isdir(os.path.join(d, 'BankSystem')) or os.path.isdir(os.path.join(d, 'StockMarket')):
                self.data = d; break
        else:
            raise FileNotFoundError('no BankSystem/ or StockMarket/ folder under %s (pass the world or world/data dir)' % root)
        up = os.path.dirname(self.data)
        self.playerdata = next((p for p in (os.path.join(self.data, 'playerdata'), os.path.join(up, 'playerdata'))
                                if os.path.isdir(p)), None)
        self._load_identity()
        self.slave = self.identity.get('role') == 'SLAVE'   # slaves have no main DBs and may lack ItemIDs.nbt: no warnings
        self.sm = self._open('StockMarket/Database/stockdata.db', quiet=self.slave)
        self.bs = self._open('BankSystem/Database/banksystem.db', quiet=self.slave)
        self.bsd = self._open(DIAG_BS, quiet=True)   # None = no diagnostics data
        self.smd = self._open(DIAG_SM, quiet=True)
        self._load_items()
        self._load_names()
        self._load_accounts()
        self._fills = self._ledger = None

    # -- sqlite
    def _open(self, rel, quiet=False):
        p = os.path.join(self.data, rel)
        if not os.path.isfile(p):
            if not quiet: self.warn('missing ' + rel)
            return None
        if self.copy:
            self._tmp = self._tmp or tempfile.mkdtemp(prefix='mc_audit_')
            dst = os.path.join(self._tmp, os.path.basename(p))
            for sfx in ('', '-wal', '-journal'):
                if os.path.isfile(p + sfx): shutil.copyfile(p + sfx, dst + sfx)
            p = dst
        try:
            c = sqlite3.connect('file:' + urllib.request.pathname2url(p) + '?mode=ro', uri=True)
            c.execute('SELECT count(*) FROM sqlite_master').fetchone()
        except sqlite3.DatabaseError as e:
            if not self.copy:
                self.warn('%s unreadable read-only (%s); retry without --no-copy' % (rel, e)); return None
            try:   # a hot journal needs a writable copy to roll back; this is our temp copy, never the original
                c = sqlite3.connect(p); c.execute('SELECT count(*) FROM sqlite_master').fetchone()
            except sqlite3.DatabaseError as e2:
                self.warn('%s unreadable: %s' % (rel, e2)); return None
        c.execute('PRAGMA query_only = 1')
        return c

    def rows(self, db, sql, params=(), rng=None):
        """Run `sql` (must contain a WHERE clause, no ORDER BY). On 'malformed' errors and with rng=(col, lo, hi,
        min_width), bisect the range and keep every readable sub-range; lost ranges go to self.lost."""
        if db is None: return []
        try:
            return db.execute(sql, params).fetchall()
        except sqlite3.DatabaseError as e:
            if rng is None:
                self.warn('query failed (%s): %s' % (e, sql[:80])); return []
            col, lo, hi, minw = rng
            self.warn('%s -> salvaging by %s ranges' % (e, col))
            out, stack = [], [(lo, hi)]
            while stack:
                a, b = stack.pop()
                try:
                    out += db.execute(sql + ' AND %s BETWEEN ? AND ?' % col, (*params, a, b)).fetchall()
                except sqlite3.DatabaseError:
                    if b - a <= minw:
                        self.lost.append((sql[:60], col, a, b)); continue
                    m = (a + b) // 2
                    stack += [(m + 1, b), (a, m)]
            return out

    def idr(self, db, table, col='id'):
        """Salvage range over the table's id column (tight bounds keep the bisection cheap)."""
        try:
            lo, hi = db.execute('SELECT min(%s), max(%s) FROM "%s"' % (col, col, table)).fetchone()
        except sqlite3.DatabaseError:
            lo = hi = None
        if lo is None:
            try:
                hi = db.execute('SELECT seq FROM sqlite_sequence WHERE name=?', (table,)).fetchone()[0]; lo = 0
            except (sqlite3.DatabaseError, TypeError):
                lo, hi = 0, 2**63 - 1
        return (col, lo, hi, 0)

    def columns(self, db, table):
        if db is None: return set()
        try:
            return {r[1] for r in db.execute('PRAGMA table_info("%s")' % table.replace('"', ''))}
        except sqlite3.DatabaseError:
            return set()

    def warn(self, msg):
        if msg not in self.warnings: self.warnings.append(msg)

    def close(self):
        for c in (self.sm, self.bs, self.bsd, self.smd):
            if c: c.close()
        if self._tmp: shutil.rmtree(self._tmp, ignore_errors=True)

    def __enter__(self): return self

    def __exit__(self, *a): self.close()

    # -- NBT / JSON metadata
    def _nbt(self, path, optional=False):
        if optional and not os.path.isfile(path): return None
        try:
            return nbt.load(path)
        except (OSError, nbt.NBTError) as e:
            self.warn('%s: %s' % (os.path.basename(path), e)); return None

    def _chunks(self, sub):
        out = []
        for f in sorted(glob.glob(os.path.join(self.data, sub, 'chunk_*.nbt'))):
            d = self._nbt(f)
            if isinstance(d, dict) and isinstance(d.get('data'), list): out += d['data']
        return out

    def _load_identity(self):
        """ModUtilities `data/ModUtilities/server_identity.json` (server_uid, role, slave_id, master_uid, first_start_ms,
        known_slaves {slave_id: {uid, last_seen_ms}}) and `level.dat` Data.LastPlayed (last world save, epoch ms).
        Missing or unreadable -> {} / None (older worlds; "no last-save info")."""
        self.identity, f = {}, os.path.join(self.data, 'ModUtilities', 'server_identity.json')
        if os.path.isfile(f) and os.path.getsize(f) < MAX_JSON_BYTES:
            try:
                with open(f, encoding='utf-8') as fh:
                    d = json.load(fh)
                if isinstance(d, dict): self.identity = d
            except ValueError as e:
                self.warn('server_identity.json: %s' % e)
        f = os.path.join(os.path.dirname(self.data), 'level.dat')
        d = self._nbt(f, True)
        lp = d['Data'].get('LastPlayed') if isinstance(d, dict) and isinstance(d.get('Data'), dict) else None
        self.last_played = lp if isinstance(lp, int) and lp > 0 else None

    def role(self):
        """MASTER / SLAVE / STANDALONE from server_identity.json. Without it, a world with banksystem.db counts as
        MASTER (slaves have no main BankSystem DB), any other as SLAVE."""
        r = self.identity.get('role')
        return r.upper() if isinstance(r, str) and r else ('MASTER' if self.bs else 'SLAVE')

    def label(self):
        """Short server name for multi-save output: the slave_id, else the role."""
        return self.identity.get('slave_id') or self.role().lower()

    def _load_items(self):
        self.items = {}
        d = self._nbt(os.path.join(self.data, 'BankSystem', 'ItemIDs.nbt'), self.slave) or {}
        for e in d.get('itemIDs', []):
            try:
                st = e['itemStack']
                self.items[e['itemID']['id']] = st['id'] + ('+components' if st.get('components') else '')
            except (KeyError, TypeError):
                pass
        for a in d.get('itemIDAliases', []):
            try:
                self.items.setdefault(a['from'], '%s (alias of #%d)' % (self.items.get(a['to'], '?'), a['to']))
            except (KeyError, TypeError):
                pass
        m = self._nbt(os.path.join(self.data, 'BankSystem', 'Meta_data.nbt'), self.slave) or {}
        for e in m.get('itemIDShortNameDigest', []):
            if isinstance(e, dict) and 's' in e and 'n' in e: self.items.setdefault(e['s'], e['n'])
        self.money_id = next((i for i, n in self.items.items() if n == 'banksystem:money'), 6)

    def iname(self, i):
        if i == WEALTH_ITEM_ID: return 'wealth (synthetic)#%d' % i
        n = self.items.get(i)
        if n is None: return '#%s?' % i
        return '%s#%d' % (n[10:] if n.startswith('minecraft:') else n, i)

    def _load_names(self):
        self.names, self.notes = {}, {}
        for sub in ('BankSystem/Bank_data/users', 'StockMarket/MarketManager/users'):
            for u in self._chunks(sub):
                try:
                    self.names.setdefault(ints_to_uuid(u['userUUID']), u['userName'])
                except (KeyError, TypeError, ValueError):
                    pass
        up = os.path.dirname(self.data)
        for p in (up, os.path.dirname(up), os.path.dirname(os.path.dirname(up))):
            f = os.path.join(p, 'usercache.json')
            if os.path.isfile(f) and os.path.getsize(f) < MAX_JSON_BYTES:
                try:
                    for e in json.load(open(f, encoding='utf-8')):
                        self.names.setdefault(str(uuid.UUID(e['uuid'])), e['name'])
                except (ValueError, KeyError, TypeError) as e:
                    self.warn('usercache.json: %s' % e)
                break
        for f in sorted(glob.glob(os.path.join(self.playerdata or '/nonexistent', '*.dat'))):
            u = os.path.basename(f)[:-4]
            d = self._nbt(f)
            if not isinstance(d, dict): continue
            n = d.get('bukkit', {}).get('lastKnownName') if isinstance(d.get('bukkit'), dict) else None
            if n: self.names.setdefault(u, n)
            acc = [0]; _walk_notes(d, acc, 0)
            if acc[0]: self.notes[u] = acc[0]

    def pname(self, u):
        return self.names.get(u) or u[:8]

    def find_player(self, s):
        """Name (case-insensitive) or UUID prefix -> uuid; also matches players only seen in OrderHistory."""
        s = s.lower()
        known = set(self.names) | {f.uuid for f in self.fills()}
        hits = [u for u in known if (self.names.get(u, '').lower() == s) or u.startswith(s)]
        if len(hits) != 1:
            raise ValueError('player %r matches %d players' % (s, len(hits)))
        return hits[0]

    def _load_accounts(self):
        self.accounts = {}
        for a in self._chunks('BankSystem/Bank_data/bankAccounts'):
            try:
                owner = ints_to_uuid(a['personalBankOwnerDataUUID']) if 'personalBankOwnerDataUUID' in a else None
                bal = {b['itemID']['id']: (b['balance'], b['lockedBalance']) for b in a.get('banks', [])}
                self.accounts[a['accountNumber']] = {'name': a.get('accountName', ''), 'owner': owner, 'banks': bal}
            except (KeyError, TypeError, ValueError):
                pass

    def accounts_of(self, u):
        return sorted({n for n, a in self.accounts.items() if a['owner'] == u} |
                      {f.account for f in self.fills() if f.uuid == u})

    # -- StockMarket
    def fills(self):
        """All OrderHistory rows (completed player orders) sorted by (time, id)."""
        if self._fills is None:
            cols = self.columns(self.sm, 'OrderHistory')
            self.order_id_col = next((c for c in ORDER_ID_COLUMNS if c in cols), None)
            g = 'intermarket_group_one, intermarket_group_two' if 'intermarket_group_two' in cols else '0, 0'
            out = []
            if cols:
                sql = ('SELECT id,itemid,accountid,userid_one,userid_two,type,amount,price,time,%s,%s '
                       'FROM OrderHistory WHERE 1' % (g, self.order_id_col or 'NULL'))
                for r in self.rows(self.sm, sql, (), self.idr(self.sm, 'OrderHistory')):
                    try:
                        out.append(Fill(r[0], r[1], r[2], longs_to_uuid(r[3], r[4]), r[5], int(r[6]), int(r[7]),
                                        int(r[8] or 0), r[9], r[10], r[11]))
                    except (TypeError, ValueError):
                        self.warn('skipped unparsable OrderHistory row id=%s' % (r[0],))
            out.sort(key=lambda f: (f.time, f.id))
            self._fills = out
        return self._fills

    def candles(self, item, t0, t1):
        """1-min candles (time, open, low, high, volume_raw) of one market. Close = next candle's open."""
        r = self.rows(self.sm, 'SELECT time,open,low,high,traded_volume FROM MarketPrice WHERE marketid=? AND time BETWEEN ? AND ?',
                      (item, t0, t1), ('time', t0, t1, 60000))
        return sorted(r)

    # -- BankSystem
    def balance_history(self, account=None, item=None):
        """BalanceHistory rows (account, item, balance, locked, time) sorted by time."""
        sql, p = 'SELECT account_number,item_id,balance,locked_balance,time FROM BalanceHistory WHERE 1', []
        if account is not None: sql += ' AND account_number=?'; p.append(account)
        if item is not None: sql += ' AND item_id=?'; p.append(item)
        return sorted(self.rows(self.bs, sql, tuple(p), self.idr(self.bs, 'BalanceHistory')), key=lambda r: (r[4], r[1]))

    def ledger(self):
        """TransactionLog rows as dicts with a resolved `code` (type_code, or mapped from legacy `kind`)."""
        if self._ledger is None:
            cols = self.columns(self.bs, 'TransactionLog')
            self.ledger_cols = cols
            base = ['id', 'account_number', 'actor_uuid', 'kind', 'item_id', 'amount', 'ts']
            sel = [c for c in base + list(LEDGER_OPTIONAL) if c in cols]
            out = []
            if cols:
                for r in self.rows(self.bs, 'SELECT %s FROM TransactionLog WHERE 1' % ','.join(sel), (),
                                   self.idr(self.bs, 'TransactionLog')):
                    d = dict(zip(sel, r))
                    code = d.get('type_code') or 0
                    if not code:   # legacy row: map kind text, sign legacy TRANSFER_OUT like the v2 migration does
                        code, k = LEGACY_KINDS.get(d.get('kind') or '', (0, 1))
                        d['amount'] = (d.get('amount') or 0) * k
                    d['code'] = code
                    out.append(d)
            out.sort(key=lambda d: (d.get('ts') or 0, d['id']))
            self._ledger = out
        return self._ledger

    # -- diagnostics DBs (read-only; every reader returns empty data when the file or table is missing)
    def meta(self, db):
        """Meta key -> value of a diagnostics DB; {} when the DB or table is missing."""
        if 'key' not in self.columns(db, 'Meta'): return {}
        return dict(self.rows(db, 'SELECT key, value FROM Meta WHERE 1'))

    def meta_ms(self, db, key):
        """An epoch-ms Meta value as int, None when missing or not a number (= unknown)."""
        try:
            return int(self.meta(db).get(key))
        except (TypeError, ValueError):
            return None

    def diag_rows(self, db, table, cols):
        """Rows of a diagnostics table as dicts, only the columns this file has; [] when the DB or table is missing."""
        have = self.columns(db, table)
        sel = [c for c in cols if c in have]
        if not sel: return []
        rng = self.idr(db, table, 'id' if 'id' in have else 'rowid')
        return [dict(zip(sel, r)) for r in self.rows(db, 'SELECT %s FROM "%s" WHERE 1' % (','.join(sel), table), (), rng)]

    @functools.cached_property
    def journal(self):
        """BankSystem TransferJournal rows (slaves only), sorted by (ts, id)."""
        return sorted(self.diag_rows(self.bsd, 'TransferJournal', JOURNAL_COLS), key=lambda r: (r.get('ts') or 0, r.get('id') or 0))

    @functools.cached_property
    def noteflow(self):
        """BankSystem NoteFlow rows (notes created + / destroyed - outside the bank), sorted by (ts, id)."""
        return sorted(self.diag_rows(self.bsd, 'NoteFlow', NOTEFLOW_COLS), key=lambda r: (r.get('ts') or 0, r.get('id') or 0))

    @functools.cached_property
    def origins(self):
        """StockMarket OrderOrigin (master only): ledger ref ("sm:order:<id>" / "sm:im:<group uuid>") ->
        (origin_server_uid or None, origin_name: slave name, '' = placed on the master)."""
        return {r['ref']: (r.get('origin_server_uid'), r.get('origin_name') or '')
                for r in self.diag_rows(self.smd, 'OrderOrigin', ('ref', 'origin_server_uid', 'origin_name')) if r.get('ref')}

    def origin_of(self, f):
        """Origin server of a fill: slave name, 'master', or '' when unknown (bot order, older world, no row)."""
        if f.g1 or f.g2: ref = 'sm:im:' + longs_to_uuid(f.g1 or 0, f.g2 or 0)
        elif f.order_id is not None: ref = 'sm:order:%s' % f.order_id
        else: return ''
        o = self.origins.get(ref)
        return '' if o is None else (o[1] or 'master')

    def rolled_back(self):
        """Ledger rows newer than Meta.bank_last_save_ms: a crash before the next bank save may have undone their
        balance change. Only "may have been": the key can be stale (a dropped async write, a timer save with clean
        data). None = no bank-save info (key missing; slaves never write it)."""
        t = self.meta_ms(self.bsd, 'bank_last_save_ms')
        return None if t is None else [r for r in self.ledger() if (r.get('ts') or 0) > t]

    def diag_start(self):
        """When the BankSystem diagnostics file started recording: the earlier of Meta.created_ms and its first row ts.
        created_ms is set by the writer thread when it opens the file, after the first rows took their ts on the
        caller thread, and a file recreated after corruption gets a created_ms newer than its queued rows.
        None = unknown."""
        t = [r.get('ts') for r in self.journal + self.noteflow] + [self.meta_ms(self.bsd, 'created_ms')]
        return min((x for x in t if x), default=None)

    def diag_status(self):
        """Per diagnostics DB: row count per table, or 'no diagnostics data' for a missing file or table."""
        out = {}
        for rel, db, tables in ((DIAG_BS, self.bsd, ('Meta', 'TransferJournal', 'NoteFlow')), (DIAG_SM, self.smd, ('Meta', 'OrderOrigin'))):
            if db is None:
                out[os.path.basename(rel)] = 'no diagnostics data (file missing)'; continue
            out[os.path.basename(rel)] = ', '.join(
                '%s %s' % (t, (self.rows(db, 'SELECT count(*) FROM "%s" WHERE 1' % t) or [('unreadable',)])[0][0] if self.columns(db, t)
                else 'missing (no diagnostics data)') for t in tables)
        return out

    def schema_summary(self):
        oh = self.columns(self.sm, 'OrderHistory'); tl = self.columns(self.bs, 'TransactionLog')
        self.fills()
        rb = self.rolled_back()
        return {
            'OrderHistory order id column': self.order_id_col or 'absent (2.1.2 format)',
            'TransactionLog.type_code': 'present' if 'type_code' in tl else 'absent (legacy kind text)',
            'TransactionLog.money_amount/ref': 'present' if {'money_amount', 'ref'} <= tl else 'absent',
            'TransactionLog.virtual_qty': 'present' if 'virtual_qty' in tl else 'absent',
            'SuspicionFlag table': 'present' if self.columns(self.bs, 'SuspicionFlag') else 'absent',
            'OrderHistory columns': ','.join(sorted(oh)) or 'missing',
            **self.diag_status(),
            'Server identity': ('%s %s' % (self.role(), self.identity.get('slave_id') or '')).strip() if self.identity
                               else 'no server_identity.json (older world)',
            'Last save (level.dat LastPlayed)': ts(self.last_played) if self.last_played else 'no last-save info',
            'Ledger rows after the last bank save': 'no bank-save info' if rb is None
                                                    else '%d (may have been rolled back)' % len(rb),
        }


def _walk_notes(o, acc, depth):
    """Sum banknote value (cents) in a playerdata tree (inventory, ender chest, nested containers)."""
    if depth > 64: return
    if isinstance(o, dict):
        i = o.get('id')
        if isinstance(i, str) and i.startswith('banksystem:money'):
            s = i[len('banksystem:money'):]
            w = 100 if s == '' else int(s[5:]) if s.startswith('_cent') and s[5:].isdigit() else int(s) * 100 if s.isdigit() else 0
            c = o.get('count', o.get('Count', 1))
            acc[0] += w * (c if isinstance(c, int) else 1)
        for v in o.values(): _walk_notes(v, acc, depth + 1)
    elif isinstance(o, list):
        for v in o: _walk_notes(v, acc, depth + 1)


# ----------------------------------------------------------------------------------- analyses
def filter_fills(fills, uuid_=None, item=None, t0=None, t1=None):
    return [f for f in fills if (uuid_ is None or f.uuid == uuid_) and (item is None or f.item == item)
            and (t0 is None or f.time >= t0) and (t1 is None or f.time < t1)]


def pnl(fills):
    """Per (uuid, item): buy/sell totals, cash P&L (sell proceeds - buy cost) and realized P&L (average cost;
    sells beyond the bought position have unknown cost and are counted as `unmatched`, not as profit).
    Returns (per_item dict, per_hour dict[(uuid, hour_ms)] -> realized cents)."""
    st = collections.defaultdict(lambda: dict(n=0, bq=0, bc=0, sq=0, sp=0, pos=0, cost=0.0, real=0.0, unmatched=0))
    hourly = collections.defaultdict(float)
    for f in fills:
        s = st[(f.uuid, f.item)]; s['n'] += 1
        m = money_of(f.amount, f.price)
        if f.amount > 0:
            s['bq'] += f.amount; s['bc'] += m; s['pos'] += f.amount; s['cost'] += m
        elif f.amount < 0:
            q = -f.amount; s['sq'] += q; s['sp'] += -m
            k = min(q, s['pos'])
            if k:
                avg = s['cost'] / s['pos']
                r = k * f.price / 100 - k * avg
                s['real'] += r; s['cost'] -= k * avg; s['pos'] -= k
                hourly[(f.uuid, f.time // 3600000 * 3600000)] += r
            s['unmatched'] += q - k
    for s in st.values(): s['cash'] = s['sp'] - s['bc']
    return st, hourly


def flag(kind, w, u, item, t, value, detail):
    return {'flag': kind, 'player': w.pname(u) if u else '', 'uuid': u or '', 'item': w.iname(item) if item is not None else '',
            'time': ts(t), 'value': value, 'detail': detail}


def flag_pump(w, fills, ratio=2.0, window_s=30):
    """Same player: buy then sell on one item, sell avg price > ratio x buy avg price, within window, and no other
    player order on that item in between (consecutive in the item's order stream)."""
    out, by = [], collections.defaultdict(list)
    for f in fills:
        if f.time > 0: by[f.item].append(f)
    for item, fs in by.items():
        for a, b in zip(fs, fs[1:]):
            if (a.uuid == b.uuid and a.amount > 0 and b.amount < 0 and b.price > ratio * a.price
                    and 0 <= b.time - a.time <= window_s * 1000):
                out.append(flag('pump_round_trip', w, a.uuid, item, b.time, round(b.price / max(a.price, 1), 2),
                                'ids %d->%d: buy %s @ %s, sell %s @ %s after %.1fs' % (
                                    a.id, b.id, qty(a.amount), usd(a.price), qty(-b.amount), usd(b.price), (b.time - a.time) / 1000)))
    return out


def flag_rate(w, fills, max_per_s=5):
    """Players with more than max_per_s orders inside any 1 s window."""
    out, by = [], collections.defaultdict(list)
    for f in fills:
        if f.time > 0: by[f.uuid].append(f.time)
    for u, t in by.items():
        j, peak, secs = 0, 0, set()
        for i, x in enumerate(t):
            while x - t[j] >= 1000: j += 1
            c = i - j + 1
            if c > max_per_s: secs.add(x // 1000); peak = max(peak, c)
        if secs:
            out.append(flag('order_rate', w, u, None, min(secs) * 1000, peak,
                            '%d seconds over %d orders/s, peak %d in 1 s, last %s' % (len(secs), max_per_s, peak, ts(max(secs) * 1000))))
    return out


def flag_pnl_hour(w, hourly, threshold_usd=1e6):
    return [flag('pnl_per_hour', w, u, None, h, round(r / 100), 'realized %s in hour starting %s' % (usd(r), ts(h)))
            for (u, h), r in sorted(hourly.items(), key=lambda kv: -kv[1]) if r > threshold_usd * 100]


def flag_prices(w, fills, ratio=10.0, max_examples=5):
    """Fills priced > ratio x or < 1/ratio x the market's typical price (median candle open, else median fill)."""
    out, by = [], collections.defaultdict(list)
    for f in fills:
        if f.time > 0: by[f.item].append(f)
    for item, fs in by.items():
        opens = [r[0] for r in w.rows(w.sm, 'SELECT open FROM MarketPrice WHERE marketid=?', (item,), ('time', 0, 2**42, 3600000)) if r[0] > 0]
        ref = statistics.median(opens) if opens else statistics.median([f.price for f in fs])
        bad = [f for f in fs if f.price > ratio * ref or f.price * ratio < ref]
        for f in sorted(bad, key=lambda f: -abs(math.log(max(f.price, 1) / max(ref, 1))))[:max_examples]:
            out.append(flag('absurd_price', w, f.uuid, item, f.time, round(f.price / max(ref, 1), 3),
                            'id %d %s %s @ %s vs typical %s (%d such fills on this market)' % (
                                f.id, 'buy' if f.amount > 0 else 'sell', qty(abs(f.amount)), usd(f.price), usd(ref), len(bad))))
    return out


def flag_spikes(w, ratio=5.0):
    """Markets with 1-min candles whose high > ratio x low (full MarketPrice scan, salvages corrupt pages)."""
    rows = w.rows(w.sm, 'SELECT marketid,time,low,high,traded_volume FROM MarketPrice WHERE high > ? * (CASE WHEN low < 1 THEN 1 ELSE low END)',
                  (ratio,), w.idr(w.sm, 'MarketPrice'))
    by = collections.defaultdict(list)
    for r in rows: by[r[0]].append(r)
    out = []
    for m, rs in by.items():
        top = max(rs, key=lambda r: r[3] / max(r[2], 1))
        out.append(flag('candle_spike', w, None, m, top[1], len(rs),
                        '%d candles with high > %gx low, %s..%s; worst %s low %s high %s vol %s' % (
                            len(rs), ratio, ts(min(r[1] for r in rs)), ts(max(r[1] for r in rs)), ts(top[1]),
                            usd(top[2]), usd(top[3]), qty(top[4]))))
    return sorted(out, key=lambda f: -f['value'])


def reconcile(w, fills=None, threshold_raw=100000):
    """Per (account, item) and BalanceHistory snapshot interval: bank delta vs explained flows.
    Explained = trade flow (ledger TRADE_BUY/SELL rows with money_amount when present, OrderHistory fills before the
    first ledger trade row) + signed non-trade ledger rows. Returns (rows above threshold, mode string)."""
    fills = w.fills() if fills is None else fills
    L = w.ledger()
    trade = [r for r in L if r['code'] in TRADE_CODES and r.get('money_amount') is not None]
    cutoff = min((r['ts'] for r in trade), default=float('inf'))
    ev = collections.defaultdict(list)    # (acct, item) -> [(t, delta)]
    odd = collections.defaultdict(list)   # (acct, item) -> [(t, code)] rows of unknown sign
    for f in fills:
        if f.time < cutoff:
            ev[(f.account, f.item)].append((f.time, f.amount))
            ev[(f.account, w.money_id)].append((f.time, -money_of(f.amount, f.price)))
    for r in L:
        a, i, t, amt = r['account_number'], r['item_id'], r.get('ts') or 0, r.get('amount') or 0
        sign = LEDGER_TYPES.get(r['code'], ('?', None))[1]
        if sign in ('buy', 'sell'):
            if r.get('money_amount') is None: continue
            s = 1 if sign == 'buy' else -1
            ev[(a, i)].append((t, s * amt))
            ev[(a, r.get('currency_item') or w.money_id)].append((t, -s * r['money_amount']))
        elif sign is None:
            odd[(a, i)].append((t, r['code']))
        else:
            ev[(a, i)].append((t, sign * amt))
    snaps = collections.defaultdict(list)
    for a, i, b, l, t in w.balance_history():
        if i != WEALTH_ITEM_ID: snaps[(a, i)].append((t, b + l))
    out = []
    for key, ss in snaps.items():
        e = sorted(ev.get(key, []))
        et = [x[0] for x in e]; cum = [0]
        for x in e: cum.append(cum[-1] + x[1])
        S = lambda t: cum[bisect.bisect_right(et, t)]
        ot = sorted(odd.get(key, []))
        for (t0, b0), (t1, b1) in zip(ss, ss[1:]):
            ex = S(t1) - S(t0); res = (b1 - b0) - ex
            if abs(res) > threshold_raw:
                codes = sorted({LEDGER_TYPES.get(c, ('code%d' % c,))[0] for t, c in ot if t0 < t <= t1})
                out.append({'account': key[0], 'owner': w.pname(w.accounts.get(key[0], {}).get('owner') or '') if w.accounts.get(key[0], {}).get('owner') else '',
                            'item': w.iname(key[1]), 'item_id': key[1], 'from': ts(t0), 'to': ts(t1), 'bank_delta': b1 - b0,
                            'explained': ex, 'residual': res, 'note': ('unsigned ledger rows: ' + ','.join(codes)) if codes else ''})
    mode = ('ledger trade rows from %s, OrderHistory before' % ts(cutoff)) if trade else 'OrderHistory fills (no ledger trade rows)'
    return sorted(out, key=lambda r: -abs(r['residual'])), mode


def virtual_split(w):
    """Per account: money from/to virtual (bot) liquidity vs player-to-player, from ledger virtual_qty.
    None when the column is absent."""
    w.ledger()
    if 'virtual_qty' not in w.ledger_cols: return None
    out = collections.defaultdict(lambda: [0.0, 0.0])   # acct -> [virtual net cents, p2p net cents]
    for r in w.ledger():
        if r['code'] not in TRADE_CODES or r.get('money_amount') is None or not r.get('amount'): continue
        s = 1 if LEDGER_TYPES[r['code']][1] == 'sell' else -1
        v = min(max(r.get('virtual_qty') or 0, 0), r['amount']) / r['amount']
        out[r['account_number']][0] += s * r['money_amount'] * v
        out[r['account_number']][1] += s * r['money_amount'] * (1 - v)
    return out


def ledger_order_mismatch(w):
    """Closed orders whose ledger rows (ref sm:order:<id>) do not sum to the OrderHistory volume. None if either
    side lacks the columns."""
    w.fills()
    if not w.order_id_col or 'ref' not in w.columns(w.bs, 'TransactionLog'): return None
    led = {}
    for r in w.ledger():
        ref = r.get('ref') or ''
        if r['code'] in TRADE_CODES and ref.startswith('sm:order:'):
            led[ref[9:]] = led.get(ref[9:], 0) + (r.get('amount') or 0)
    out = []
    for f in w.fills():
        if f.order_id is None: continue
        key = str(f.order_id)
        if key not in led:
            out.append(flag('ledger_missing', w, f.uuid, f.item, f.time, abs(f.amount) / SCALE,
                            'order %s (%s) has no ledger trade row' % (f.order_id, qty(abs(f.amount)))))
            continue
        got = led.pop(key)
        if got != abs(f.amount):
            out.append(flag('ledger_mismatch', w, f.uuid, f.item, f.time, got - abs(f.amount),
                            'order %s: OrderHistory %s, ledger %s' % (f.order_id, qty(abs(f.amount)), qty(got))))
    # refs without an OrderHistory row are open orders (PERIODIC rows) or lost history; report them as info
    for oid, q in led.items():
        out.append({'flag': 'ledger_without_order', 'player': '', 'uuid': '', 'item': '', 'time': '', 'value': q,
                    'detail': 'ref sm:order:%s has %s ledger volume but no OrderHistory row (open order?)' % (oid, qty(q))})
    return out


def suspicion_flags(w, limit=50):
    """Generic dump of the BankSystem SuspicionFlag table (schema not final). None if absent."""
    cols = w.columns(w.bs, 'SuspicionFlag')
    if not cols: return None
    cur = w.bs.execute('SELECT * FROM SuspicionFlag ORDER BY id DESC LIMIT ?', (limit,))
    return [d[0] for d in cur.description], cur.fetchall()


def audit_logs(w):
    return sorted(glob.glob(os.path.join(w.data, 'BankSystem', 'audit', '*.log')))


# ----------------------------------------------------------------------------------- multi-save join
def _is_uuid(s):
    try:
        return isinstance(s, str) and str(uuid.UUID(s)) == s
    except ValueError:
        return False


def _raw_sum(items_json):
    """Total raw units of a TransferJournal items_json ({"<item_id>": raw}); None when absent or unparsable."""
    try:
        return sum(int(v) for v in json.loads(items_json).values())
    except (TypeError, ValueError, AttributeError):
        return None


def _into_bank(r, direction):
    """Signed raw value a master ledger row moves into the bank (+) or out of it (-). Codes without a fixed sign
    (UNKNOWN_LEGACY, SHARE_TRADE, codes newer than LEDGER_TYPES) follow the journal direction, else the amount's sign."""
    s, a = LEDGER_TYPES.get(r['code'], ('?', None))[1], r.get('amount') or 0
    if isinstance(s, int): return s * a
    return -abs(a) if direction == 'FROM_MASTER' else abs(a) if direction == 'TO_MASTER' else a


UNLINKED_DROP = 'DROP payout, master row not linked (BankSystem #127 ref=null, until #136)'
DELETE_CODES = (25, 28)    # ACCOUNT_DELETE_PAYOUT, ACCOUNT_DELETE_LOSS


def _unlinked_drop(J, L, mv, sv):
    """Known gap, not a finding, until BankSystem #136 lands. User-deletion payouts (ServerBankManager.dropOnSlaves)
    drop to slaves under a transfer_id the master never writes as ledger ref (#127: ACCOUNT_DELETE_PAYOUT/LOSS rows
    have ref = null), so a DROP with DELIVERED rows and no master row is this gap. Before #136 the payout also dropped
    raw balances as item COUNTS (100x the value), so a linked deletion payout whose DELIVERED total does not match is
    the same gap. A FAILED-only DROP is not: that leg was passed on to the next slave. Remove with old-data support."""
    if not any(r.get('path') == 'DROP' and r.get('step') == 'DELIVERED' for r in J): return False
    return not L or (mv != sv and any(r['code'] in DELETE_CODES for r in L))


def join(worlds):
    """Join master + slave saves by transfer_id (plan v0.3 "What the tool will check", BankSystem delta v0.4).

    Slave side: TransferJournal rows (slaves only). Master side: TransactionLog rows with ref = transfer_id (all
    summed) and TRANSFER_PARKED SuspicionFlag rows with ref = transfer_id. Matched by id only, never by time; only
    totals per transfer are compared (journal units differ per path, see README).
    Per transfer the value is compared in raw units, signed as "into the master bank":
      master = sum of its ref rows (LedgerType sign);
      slave  = TO_MASTER: REQUESTED - RETURNED, 0 when REQUESTED has no follow-up row (a non-success reply writes
               none: nothing applied); FROM_MASTER: -DELIVERED (ASYNC_BANK: the master reported the debit as
               SUCCESS; DROP: the items were dropped in the slave world).
    Transfers whose REQUESTED row has ledger_type NULL (AsyncBank NO_LOG calls, e.g. StockMarket settlement) are
    skipped when the master has no row for them: by design it writes none.
    Classes: ok (equal; also a DROP with only FAILED rows: passed on to the next slave), parked (open admin case),
    duplicate (both sides, more value than moved), lost (value left one side and did not arrive), created from
    nothing (slave got value, master has no row), known gap (see _unlinked_drop).
    'data gap' replaces lost / created from nothing when the side without rows cannot show the transfer (save not
    loaded, no diagnostics DB, DB started after the transfer, master ledger without ref); 'after snapshot' when the
    transfer is newer than the oldest loaded save (level.dat LastPlayed). Returns (rows, warnings)."""
    warns, seen = [], {}
    for w in worlds:
        u = w.identity.get('server_uid')
        if u and u in seen: warns.append('%s and %s share server_uid %s (copied world?)' % (seen[u], w.data, u))
        if u: seen.setdefault(u, w.data)
    masters = [w for w in worlds if w.role() != 'SLAVE']
    slaves = [w for w in worlds if w.role() == 'SLAVE']
    m = masters[0] if masters else None
    if len(masters) > 1: warns.append('%d master/standalone saves loaded; using %s' % (len(masters), m.data))
    if m is None: warns.append('no master save loaded: the master side of every transfer is unknown')
    mu = m.identity.get('server_uid') if m else None
    for s in slaves:
        if mu and s.identity.get('master_uid') not in (None, mu):
            warns.append('slave %s belongs to master %s, not to the loaded one' % (s.label(), s.identity.get('master_uid')))
        if s.bsd is None: warns.append('slave %s: no diagnostics data (no %s)' % (s.label(), os.path.basename(DIAG_BS)))
    warns += ['%s: no last-save info (no level.dat LastPlayed)' % w.label() for w in worlds if w.last_played is None]
    known = [w.last_played for w in worlds if w.last_played]
    oldest = min(known) if known else None

    led, parked, noref = collections.defaultdict(list), {}, True
    if m and m.bs:
        noref = 'ref' not in m.columns(m.bs, 'TransactionLog')
        for r in m.ledger():
            if _is_uuid(r.get('ref')): led[r['ref']].append(r)
        fc = m.columns(m.bs, 'SuspicionFlag')
        if 'ref' in fc:
            for ref, t, ack in m.rows(m.bs, 'SELECT ref, ts, %s FROM SuspicionFlag WHERE flag_type = ?'
                                      % ('acknowledged' if 'acknowledged' in fc else '0'), (TRANSFER_PARKED,)):
                if ref: parked[ref] = (t or 0, ack)
    bank_save = m.meta_ms(m.bsd, 'bank_last_save_ms') if m else None
    if m and bank_save is None: warns.append('%s: no bank-save info (no bank_last_save_ms)' % m.label())
    jr, owner = collections.defaultdict(list), collections.defaultdict(list)   # a DROP can pass through several slaves
    for s in slaves:
        for r in s.journal:
            jr[r['transfer_id']].append(r)
            if s not in owner[r['transfer_id']]: owner[r['transfer_id']].append(s)
    nolog = {t for t, J in jr.items() if any(r.get('step') == 'REQUESTED' and 'ledger_type' in r and r['ledger_type'] is None
                                             and r.get('path') != 'DROP' for r in J)}

    def gap(t, slave_missing):
        """Why the side without rows cannot show a transfer at time t, or None."""
        if not slave_missing:
            return 'no master save loaded' if m is None else 'master has no banksystem.db' if m.bs is None \
                else 'master ledger has no ref column' if noref else None
        if not slaves: return 'no slave save loaded'
        ks = m.identity.get('known_slaves') if m else None
        missing = sorted(set(ks if isinstance(ks, dict) else ()) - {s.identity.get('slave_id') for s in slaves})
        if missing: return 'slave save not loaded: ' + ', '.join(missing)
        for s in slaves:   # master rows do not name the slave, so every loaded slave must be able to show it
            if s.bsd is None: return 'slave %s has no diagnostics data' % s.label()
            c = s.diag_start()
            if c is None or c > t: return 'slave %s diagnostics DB started %s, after the transfer' % (s.label(), ts(c) if c else '(unknown)')
        return None

    out = []
    for tid in set(jr) | set(led) | set(parked):
        J, L = jr.get(tid, []), led.get(tid, [])
        if tid in nolog and not L and tid not in parked: continue
        d = next((r['direction'] for r in J if r.get('direction')), None)
        req = next((r for r in J if r.get('step') == 'REQUESTED'), None)
        requested = _raw_sum(req.get('items_json')) if req else None
        part = lambda r: (_raw_sum(r.get('items_json')) if r.get('items_json') else requested) or 0   # null = all
        followed = any(r.get('step') != 'REQUESTED' for r in J)
        sv = (((requested or 0) - sum(part(r) for r in J if r.get('step') == 'RETURNED') if followed else 0) if d == 'TO_MASTER'
              else -sum(part(r) for r in J if r.get('step') == 'DELIVERED') if d == 'FROM_MASTER' else 0)
        mv = sum(_into_bank(r, d) for r in L)
        t = min([r.get('ts') or 0 for r in J + L] or [parked.get(tid, (0,))[0]])
        notes = []
        if tid in parked:
            cls = 'parked'
            if parked[tid][1]: notes.append('acknowledged')
        elif _unlinked_drop(J, L, mv, sv): cls = 'known gap'; notes.append(UNLINKED_DROP)
        elif mv == sv:
            cls = 'ok'
            if J and not L and all(r.get('path') == 'DROP' and r.get('step') == 'FAILED' for r in J):
                notes.append('DROP leg passed on to the next slave (player not here)')
            elif req and not followed: notes.append('not applied (REQUESTED without a follow-up row)')
        elif not J: cls = 'lost'
        elif not L: cls = 'created from nothing' if mv > sv else 'lost'
        else: cls = 'duplicate' if mv > sv else 'lost'
        if cls in ('lost', 'created from nothing'):
            g = gap(t, not J) if not (J and L) else None
            if g: cls = 'data gap'; notes.append(g)
            elif oldest is not None and t > oldest: cls = 'after snapshot'; notes.append('newer than the oldest loaded save %s' % ts(oldest))
        if bank_save is not None and any((r.get('ts') or 0) > bank_save for r in L):
            notes.append('master ledger rows may have been rolled back (after the bank save %s)' % ts(bank_save))
        nm = m or owner[tid][0]
        acct = next((r.get('account_number') for r in J + L if r.get('account_number') is not None), '')
        pu = next((r.get('player_uuid') for r in J if r.get('player_uuid')), None) or next((r.get('actor_uuid') for r in L if r.get('actor_uuid')), None)
        out.append({'transfer_id': tid, 'class': cls, 'time': ts(t), 'time_ms': t,
                    'path': next((r['path'] for r in J if r.get('path')), ''), 'direction': d or '',
                    'slave': ' '.join(s.label() for s in owner.get(tid, ())), 'account': acct, 'player': nm.pname(pu) if pu else '',
                    'slave_steps': ' '.join(r.get('step') or '?' for r in J),
                    'master_rows': ' '.join('%s %s' % (LEDGER_TYPES.get(r['code'], ('code%s' % r['code'],))[0], qty(r.get('amount') or 0)) for r in L),
                    'into_bank_master': mv / SCALE, 'into_bank_slave': sv / SCALE, 'note': '; '.join(notes)})
    return sorted(out, key=lambda r: (r['time_ms'], r['transfer_id'])), warns


def write_csv(path, rows, header=None):
    """rows: list of dicts (header = keys of first row) or list of sequences with header."""
    with open(path, 'w', newline='', encoding='utf-8') as fh:
        if rows and isinstance(rows[0], dict):
            cw = csv.DictWriter(fh, fieldnames=header or list(rows[0])); cw.writeheader(); cw.writerows(rows)
        else:
            cw = csv.writer(fh)
            if header: cw.writerow(header)
            cw.writerows(rows)


def fill_dicts(w, fills):
    return [{'id': f.id, 'time': ts(f.time), 'time_ms': f.time, 'player': w.pname(f.uuid), 'uuid': f.uuid, 'account': f.account,
             'item': w.iname(f.item), 'item_id': f.item, 'side': 'buy' if f.amount > 0 else 'sell',
             'type': {0: 'market', 1: 'limit'}.get(f.type, f.type), 'qty': f.amount / SCALE, 'price': f.price / 100,
             'money': money_of(f.amount, f.price) / 100, 'order_id': f.order_id,
             'intermarket': bool(f.g1 or f.g2), 'origin': w.origin_of(f)} for f in fills]


def pnl_dicts(w, st):
    return [{'player': w.pname(u), 'uuid': u, 'item': w.iname(i), 'item_id': i, 'orders': s['n'],
             'bought': s['bq'] / SCALE, 'buy_cost': s['bc'] / 100, 'avg_buy': (s['bc'] / s['bq']) if s['bq'] else 0,
             'sold': s['sq'] / SCALE, 'sell_proceeds': s['sp'] / 100, 'avg_sell': (s['sp'] / s['sq']) if s['sq'] else 0,
             'cash_pnl': s['cash'] / 100, 'realized_pnl': round(s['real'] / 100, 2), 'unmatched_sold': s['unmatched'] / SCALE,
             'open_position': s['pos'] / SCALE}
            for (u, i), s in sorted(st.items(), key=lambda kv: -kv[1]['real'])]


# ----------------------------------------------------------------------------------- report
def analyze(w, t0=None, t1=None, pump_ratio=2.0, pump_window=30, max_rate=5, pnl_hour=1e6, price_ratio=10.0,
            spike_ratio=5.0, recon_threshold=1000.0, spikes=True):
    fills = w.fills()
    sel = filter_fills(fills, t0=t0, t1=t1)
    st, hourly = pnl(sel)
    flags = (flag_pump(w, sel, pump_ratio, pump_window) + flag_rate(w, sel, max_rate) +
             flag_pnl_hour(w, hourly, pnl_hour) + flag_prices(w, sel, price_ratio) +
             (flag_spikes(w, spike_ratio) if spikes else []))
    lm = ledger_order_mismatch(w)
    if lm: flags += lm
    rec, mode = reconcile(w, fills, int(recon_threshold * SCALE))
    for r in rec:
        flags.append({'flag': 'unexplained_balance', 'player': r['owner'], 'uuid': '', 'item': r['item'], 'time': r['to'],
                      'value': r['residual'] / SCALE, 'detail': 'acct %s %s..%s bank %s explained %s %s' % (
                          r['account'], r['from'], r['to'], qty(r['bank_delta']), qty(r['explained']), r['note'])})
    return {'fills': sel, 'all_fills': fills, 'pnl': st, 'flags': flags, 'recon': rec, 'recon_mode': mode,
            'virtual': virtual_split(w), 'suspicion': suspicion_flags(w), 'audit_logs': audit_logs(w)}


def report_md(w, res, top=25, title='StockMarket/BankSystem audit'):
    L = ['# %s' % title, '', 'Input: `%s`  ' % w.data, 'Generated: %s UTC. Money in $, quantities in whole items.' % ts(dt.datetime.now(dt.timezone.utc).timestamp() * 1000), '']
    L += ['## Data', '', '| | |', '|---|---|']
    fl = res['all_fills']
    L += ['| %s | %s |' % kv for kv in w.schema_summary().items()]
    L += ['| OrderHistory rows | %d (%d with time=0: test leftovers, excluded from flags) |' % (len(fl), sum(1 for f in fl if f.time <= 0)),
          '| Players (named) | %d |' % len(w.names), '| Items | %d |' % len(w.items),
          '| Ledger rows | %d |' % len(w.ledger()), '| Reconciliation mode | %s |' % res['recon_mode'], '']
    if w.warnings or w.lost:
        lost = collections.defaultdict(list)
        for q, c, a, b in w.lost: lost[(q, c)].append((a, b))
        L += ['Warnings:', ''] + ['- %s' % x for x in w.warnings]
        for (q, c), v in lost.items():
            lo, hi = min(x[0] for x in v), max(x[1] for x in v)
            L.append('- unreadable (corrupt pages): %d sub-ranges of `%s`, %s %s..%s' % (
                len(v), q, c, *((ts(lo), ts(hi)) if c == 'time' else (lo, hi))))
        L.append('')
    L += ['## Top realized P&L (player, item)', '',
          '| Player | UUID | Item | Orders | Bought | Avg buy | Sold | Avg sell | Cash P&L | Realized P&L | Unmatched sold |',
          '|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|']
    for d in pnl_dicts(w, res['pnl'])[:top]:
        L.append('| %s | %s | %s | %d | %s | %s | %s | %s | %s | %s | %s |' % (
            d['player'], d['uuid'][:8], d['item'], d['orders'], format(d['bought'], ',.2f'), usd(d['avg_buy'] * 100),
            format(d['sold'], ',.2f'), usd(d['avg_sell'] * 100), usd(d['cash_pnl'] * 100), usd(d['realized_pnl'] * 100),
            format(d['unmatched_sold'], ',.2f')))
    L.append('')
    by = collections.defaultdict(list)
    for f in res['flags']: by[f['flag']].append(f)
    L += ['## Flags', '', '| Flag | Count |', '|---|---:|'] + ['| %s | %d |' % (k, len(v)) for k, v in by.items()] + ['']
    for k, v in by.items():
        L += ['### %s' % k, '', '| Player | Item | Time (UTC) | Value | Detail |', '|---|---|---|---:|---|']
        L += ['| %s | %s | %s | %s | %s |' % (f['player'], f['item'], f['time'], f['value'], f['detail']) for f in v[:top]]
        if len(v) > top: L.append('| ... | | | | %d more (see CSV) |' % (len(v) - top))
        L.append('')
    if res['virtual'] is not None:
        L += ['## Money vs virtual liquidity (ledger virtual_qty)', '', '| Account | Owner | From virtual book | Player-to-player |', '|---|---|---:|---:|']
        for a, (v, p) in sorted(res['virtual'].items(), key=lambda kv: -kv[1][0])[:top]:
            o = w.accounts.get(a, {}).get('owner')
            L.append('| %s | %s | %s | %s |' % (a, w.pname(o) if o else '', usd(v), usd(p)))
        L.append('')
    big = sorted(((sum(b + l for i, (b, l) in a['banks'].items() if i == w.money_id), n, a) for n, a in w.accounts.items()), key=lambda x: -x[0])
    L += ['## Largest bank money balances (final state)', '', '| Account | Name | Owner | Money |', '|---|---|---|---:|']
    L += ['| %s | %s | %s | %s |' % (n, a['name'], w.pname(a['owner']) if a['owner'] else '', usd(m)) for m, n, a in big[:10]] + ['']
    if w.notes:
        L += ['## Banknotes held in playerdata', '', '| Player | Value |', '|---|---:|']
        L += ['| %s | %s |' % (w.pname(u), usd(c)) for u, c in sorted(w.notes.items(), key=lambda kv: -kv[1])] + ['']
    if res['suspicion']:
        cols, rows = res['suspicion']
        L += ['## SuspicionFlag (latest %d)' % len(rows), '', '| ' + ' | '.join(cols) + ' |', '|' + '---|' * len(cols)]
        L += ['| ' + ' | '.join(str(x) for x in r) + ' |' for r in rows] + ['']
    if res['audit_logs']:
        L += ['## BankSystem audit logs', ''] + ['- `%s`' % p for p in res['audit_logs']] + ['']
    return '\n'.join(L)
