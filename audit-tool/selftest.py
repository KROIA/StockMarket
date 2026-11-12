"""Self-check: builds tiny synthetic worlds (new + legacy format, plus a corrupted DB) and asserts the parsers,
reconciliation and anomaly flags. Run: python -I audit-tool/selftest.py  (exit code 0 = pass)."""
import json, os, sqlite3, sys, tempfile, shutil

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core, nbt

U1 = '11111111-2222-3333-4444-555555555555'   # trader
U2 = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'   # other player
T = 1700000000000                              # 2023-11-14 22:13:20 UTC (arbitrary)
MONEY, DIA = 6, 500                            # DIA = an arbitrary test item


def uuid_ints(u):
    b = bytes.fromhex(u.replace('-', ''))
    return [int.from_bytes(b[i:i + 4], 'big', signed=True) for i in range(0, 16, 4)]


def uuid_longs(u):
    b = bytes.fromhex(u.replace('-', ''))
    return int.from_bytes(b[:8], 'big', signed=True), int.from_bytes(b[8:], 'big', signed=True)


def build(root, new_format):
    bsd, smd = os.path.join(root, 'BankSystem'), os.path.join(root, 'StockMarket')
    for d in ('Bank_data/users', 'Bank_data/bankAccounts', 'Database'):
        os.makedirs(os.path.join(bsd, d))
    os.makedirs(os.path.join(smd, 'Database'))
    w = lambda p, o: open(p, 'wb').write(nbt.dumps(o))
    w(os.path.join(bsd, 'ItemIDs.nbt'), {'itemIDs': [{'itemID': {'id': MONEY}, 'itemStack': {'id': 'banksystem:money', 'count': 1}},
                                                     {'itemID': {'id': DIA}, 'itemStack': {'id': 'minecraft:stone', 'count': 1}}],
                                         'itemIDAliases': [{'from': 900, 'to': DIA}], 'formatVersion': 2})
    w(os.path.join(bsd, 'Meta_data.nbt'), {'itemIDShortNameDigest': [{'s': 7, 'n': 'banksystem:money5'}]})
    w(os.path.join(bsd, 'Bank_data/users/chunk_0.nbt'), {'data': [{'userUUID': uuid_ints(U1), 'userName': 'Trader'},
                                                                  {'userUUID': uuid_ints(U2), 'userName': 'Other'}]})
    w(os.path.join(bsd, 'Bank_data/bankAccounts/chunk_0.nbt'), {'data': [
        {'accountNumber': 1, 'accountName': 'Trader acct', 'personalBankOwnerDataUUID': uuid_ints(U1),
         'banks': [{'itemID': {'id': MONEY}, 'balance': 10**9, 'lockedBalance': 0}]}]})

    sm = sqlite3.connect(os.path.join(smd, 'Database', 'stockdata.db'))
    sm.execute('CREATE TABLE OrderHistory (id INTEGER PRIMARY KEY AUTOINCREMENT, itemid INTEGER, accountid INTEGER, userid_one INTEGER,'
               ' userid_two INTEGER, type INTEGER, amount INTEGER, price INTEGER, time DATETIME,'
               ' intermarket_group_one INTEGER DEFAULT 0, intermarket_group_two INTEGER DEFAULT 0%s)' % (', order_id INTEGER' if new_format else ''))
    sm.execute('CREATE TABLE MarketPrice (id INTEGER PRIMARY KEY AUTOINCREMENT, marketid INTEGER, open INTEGER, low INTEGER, high INTEGER, time INTEGER, traded_volume REAL)')
    sm.execute('CREATE INDEX idx_mp ON MarketPrice (marketid, time)')
    a1, a2 = uuid_longs(U1), uuid_longs(U2)
    orders = [  # (uuid longs, acct, amount raw, price cents, time)
        (a2, 2, 500, 1000, T + 1000),          # other player buys 5 @ $10
        (a1, 1, 1000, 1000, T + 10000),         # trader buys 10 @ $10
        (a1, 1, -1000, 3000, T + 15000),        # trader sells 10 @ $30, 5 s later, nothing in between -> pump
    ] + [(a1, 1, 100, 1000, T + 20000 + k * 100) for k in range(7)] \
      + [(a1, 1, -700, 100000, T + 60000)]     # 7 orders in 0.7 s -> rate; sell @ $1000 40 s later -> absurd price, no pump
    for n, (u, acct, amt, px, t) in enumerate(orders, 1):
        sm.execute('INSERT INTO OrderHistory (itemid,accountid,userid_one,userid_two,type,amount,price,time%s) VALUES (?,?,?,?,0,?,?,?%s)'
                   % ((',order_id', ',?') if new_format else ('', '')), (DIA, acct, *u, amt, px, t, *([n] if new_format else [])))
    sm.executemany('INSERT INTO MarketPrice (marketid,open,low,high,time,traded_volume) VALUES (?,?,?,?,?,?)',
                   [(DIA, 1000, 990, 1010, T - 86400000 + k * 60000, 100) for k in range(3000)] +
                   [(DIA, 1000, 1000, 9000, T + 60000, 100)])   # one 9x spike candle
    sm.commit(); sm.close()

    bs = sqlite3.connect(os.path.join(bsd, 'Database', 'banksystem.db'))
    bs.execute('CREATE TABLE BalanceHistory (id INTEGER PRIMARY KEY AUTOINCREMENT, account_number INTEGER, item_id INTEGER, balance INTEGER, locked_balance INTEGER, time INTEGER)')
    extra = ', type_code INTEGER NOT NULL DEFAULT 0, ref TEXT, money_amount INTEGER, price INTEGER, currency_item INTEGER, virtual_qty INTEGER' if new_format else ''
    bs.execute('CREATE TABLE TransactionLog (id INTEGER PRIMARY KEY AUTOINCREMENT, account_number INTEGER, actor_uuid TEXT, kind TEXT, item_id INTEGER,'
               ' amount INTEGER, other_account INTEGER, company_id INTEGER, ts INTEGER, note TEXT, source_kind TEXT DEFAULT \'UNKNOWN\', tag TEXT%s)' % extra)
    # money snapshots: start 1000000 cents; after buy -10000 (cost $100) +30000 (sell $300) = 1020000;
    # then an unexplained +5,000,000 cents jump; then a legacy/v2 transfer out of 1000 cents
    snaps = [(T, 1000000), (T + 16000, 1020000), (T + 17000, 6020000), (T + 18000, 6019000), (T + 18500, 6019000 + 32767 * 0)]
    bs.executemany('INSERT INTO BalanceHistory (account_number,item_id,balance,locked_balance,time) VALUES (1,?,?,0,?)',
                   [(MONEY, b, t) for t, b in snaps] + [(core.WEALTH_ITEM_ID, 10**15, T + 17000)])
    if new_format:
        rows = [(1, U1, 'TRADE', DIA, 1000, T + 10000, 4, 'sm:order:2', 10000, 1000, MONEY, 1000, 'CLOSE'),
                (1, U1, 'TRADE', DIA, 1000, T + 15000, 5, 'sm:order:3', 30000, 3000, MONEY, 1000, 'CLOSE'),
                (1, U1, 'TRADE', DIA, 600, T + 60000, 5, 'sm:order:11', 700000, 100000, MONEY, 0, 'CLOSE'),  # 600 != 700 -> mismatch
                (1, U1, 'TRANSFER', MONEY, -1000, T + 17500, 3, None, None, None, None, None, None)]
        bs.executemany('INSERT INTO TransactionLog (account_number,actor_uuid,kind,item_id,amount,ts,type_code,ref,money_amount,price,currency_item,virtual_qty,tag)'
                       ' VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)', rows)
    else:
        bs.execute("INSERT INTO TransactionLog (account_number,actor_uuid,kind,item_id,amount,ts) VALUES (1,?,'TRANSFER_OUT',?,1000,?)", (U1, MONEY, T + 17500))
    bs.commit(); bs.close()


def check(new_format):
    root = tempfile.mkdtemp(prefix='audit_selftest_')
    try:
        build(root, new_format)
        with core.World(root) as w:
            assert w.names[U1] == 'Trader' and w.pname(U2) == 'Other'
            assert w.iname(DIA) == 'stone#500' and 'alias' in w.iname(900) and w.iname(7) == 'banksystem:money5#7'
            assert w.iname(32767).startswith('wealth (synthetic)')
            assert w.find_player('trader') == U1 and w.accounts_of(U1) == [1]
            st, hourly = core.pnl(core.filter_fills(w.fills(), U1, DIA, T, T + 16000))
            s = st[(U1, DIA)]
            assert s['cash'] == 20000 and abs(s['real'] - 20000) < 1e-6, s    # +$200
            res = core.analyze(w, pnl_hour=100, recon_threshold=1)
            kinds = {f['flag'] for f in res['flags']}
            for k in ('pump_round_trip', 'order_rate', 'pnl_per_hour', 'absurd_price', 'candle_spike', 'unexplained_balance'):
                assert k in kinds, (k, kinds)
            pump = [f for f in res['flags'] if f['flag'] == 'pump_round_trip']
            assert len(pump) == 1 and pump[0]['player'] == 'Trader' and pump[0]['value'] == 3.0, pump
            rec = res['recon']
            assert len(rec) == 1 and rec[0]['residual'] == 5000000 and rec[0]['item_id'] == MONEY, rec   # only the injected jump
            if new_format:
                assert 'ledger trade rows' in res['recon_mode']
                mm = [f for f in res['flags'] if f['flag'] == 'ledger_mismatch']
                assert len(mm) == 1 and 'order 11' in mm[0]['detail'], mm
                assert len([f for f in res['flags'] if f['flag'] == 'ledger_missing']) == 8   # orders 1, 4-10 have no rows
                v = res['virtual'][1]
                assert abs(v[0] - (-10000 + 30000)) < 1e-6 and abs(v[1] - 700000) < 1e-6, v   # order 11 fully p2p
            else:
                assert 'OrderHistory' in res['recon_mode'] and res['virtual'] is None
            md = core.report_md(w, res)
            assert 'Trader' in md and 'pump_round_trip' in md
    finally:
        shutil.rmtree(root, ignore_errors=True)


def check_payout_signs():
    """PAYOUT / DIVIDEND: new rows (balance_after set) are signed per leg, legacy rows (balance_after NULL) unsigned on
    both legs, so they stay unknown and only label the interval. SHARE_TRADE stays unknown either way."""
    root = tempfile.mkdtemp(prefix='audit_selftest_')
    try:
        build(root, True)
        bs = sqlite3.connect(os.path.join(root, 'BankSystem', 'Database', 'banksystem.db'))
        bs.execute('ALTER TABLE TransactionLog ADD COLUMN balance_after INTEGER')
        co, ho = 10, 11   # company account, holder account
        bs.executemany('INSERT INTO TransactionLog (account_number,kind,item_id,amount,other_account,ts,type_code,ref,balance_after)'
                       ' VALUES (?,?,?,?,?,?,?,?,?)', [
            (co, 'PAYOUT', MONEY, -500, ho, T + 30000, 20, 'run-p', 9500), (ho, 'PAYOUT', MONEY, 500, co, T + 30000, 20, 'run-p', 500),
            (co, 'DIVIDEND', MONEY, -300, ho, T + 31000, 21, 'run-d', 9200), (ho, 'DIVIDEND', MONEY, 300, co, T + 31000, 21, 'run-d', 800),
            (co, 'PAYOUT', MONEY, 200, ho, T + 40000, 20, None, None), (ho, 'PAYOUT', MONEY, 200, co, T + 40000, 20, None, None),
            (co, 'DIVIDEND', MONEY, 100, ho, T + 41000, 21, None, None), (ho, 'DIVIDEND', MONEY, 100, co, T + 41000, 21, None, None)])
        bs.executemany('INSERT INTO BalanceHistory (account_number,item_id,balance,locked_balance,time) VALUES (?,?,?,0,?)',
                       [(co, MONEY, 10000, T + 29000), (co, MONEY, 9200, T + 32000), (co, MONEY, 8900, T + 42000),
                        (ho, MONEY, 0, T + 29000), (ho, MONEY, 800, T + 32000), (ho, MONEY, 1100, T + 42000)])
        bs.commit(); bs.close()
        with core.World(root) as w:
            rec = {r['account']: r for r in core.reconcile(w, threshold_raw=0)[0] if r['account'] in (co, ho)}
            assert set(rec) == {co, ho}, rec   # new signed legs explain T+29000..T+32000 exactly
            for a, res in ((co, -300), (ho, 300)):
                r = rec[a]
                assert r['from'] == core.ts(T + 32000) and r['residual'] == res, r
                assert r['note'] == 'unsigned ledger rows: DIVIDEND,PAYOUT', r
        assert core._into_bank({'code': 20, 'amount': -500, 'balance_after': 9500}, 'TO_MASTER') == -500
        assert core._into_bank({'code': 21, 'amount': 100, 'balance_after': None}, 'FROM_MASTER') == -100   # legacy: journal direction
        for amt, after in ((-200, 1000), (200, 1200), (200, None)):   # new withdrawal, deposit, legacy row
            assert core.ledger_sign({'code': 24, 'amount': amt, 'balance_after': after}) is None
    finally:
        shutil.rmtree(root, ignore_errors=True)


def check_nbt():
    data = nbt.dumps({'a': [1, 2], 's': 'x', 'c': {'l': 2**40}})
    assert nbt.loads(data) == {'a': [1, 2], 's': 'x', 'c': {'l': 2**40}}
    deep = b'\x0a\x00\x00' + b'\x0a\x00\x01k' * 600 + b'\x00' * 601      # 600 nested compounds
    for bad in (deep, b'\x0a\x00\x00\x09\x00\x01k\x0a\x7f\xff\xff\xff', b'\x0a\x00\x00\x08\x00\x01s\xff\xff', b'\x0a\x00'):
        try:
            nbt.loads(bad); raise AssertionError('accepted bad NBT %r' % bad[:12])
        except nbt.NBTError:
            pass


def check_corrupt():
    root = tempfile.mkdtemp(prefix='audit_selftest_')
    try:
        build(root, True)
        p = os.path.join(root, 'StockMarket', 'Database', 'stockdata.db')
        c = sqlite3.connect(p)
        ps = c.execute('PRAGMA page_size').fetchone()[0]; n = c.execute('PRAGMA page_count').fetchone()[0]; c.close()
        orig = open(p, 'rb').read()
        for pg in range(n // 2, n):    # smash the first page (from the middle on) that breaks a full MarketPrice scan
            with open(p, 'wb') as f:
                f.write(orig[:ps * pg] + b'\xde\xad' * (ps // 2) + orig[ps * (pg + 1):])
            try:
                sqlite3.connect(p).execute('SELECT open FROM MarketPrice').fetchall()
            except sqlite3.DatabaseError:
                break
        else:
            raise AssertionError('could not corrupt the fixture')
        with core.World(root) as w:
            got = w.rows(w.sm, 'SELECT open FROM MarketPrice WHERE 1', (), w.idr(w.sm, 'MarketPrice'))
            assert 0 < len(got) < 3001 and w.lost, (len(got), w.lost[:2])
            assert len(w.fills()) == 11
    finally:
        shutil.rmtree(root, ignore_errors=True)


# ----------------------------------------------------------------------------- diagnostics + multi-save join
MASTER_UID, SLAVE_UID = '00000000-0000-4000-8000-0000000000aa', '00000000-0000-4000-8000-0000000000bb'
CREATED, BANK_SAVE, SLAVE_SAVE, MASTER_SAVE = T + 2500, T + 40000, T + 50000, T + 100000   # oldest save = SLAVE_SAVE
# CREATED is after the slave's first journal row (T + 2001): the writer thread opens the file after the first rows are queued
GROUP = '12345678-1234-4234-8234-123456789abc'      # inter-market group of the extra fill
GOLD, GOLD_LATE = 501, 502                         # account 1 slots bound to an external currency: at T, at T + 30000


def tid(n):
    return '00000000-0000-4000-8000-%012d' % n


# transfer -> expected class (None = skipped). Slave rows: (step, direction, path, items_json, ts);
# master rows: (type_code, amount, ts[, item_id]) on account 1, item MONEY unless given
TRANSFERS = {
    1: ('ok', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":1000}', T + 44000), ('CONFIRMED', None, None, None, T + 44001)],
        [(1, 1000, T + 45000)]),                                   # also newer than bank_last_save_ms -> rolled back note
    2: ('ok', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"7":500}', T + 2001)], [(2, 500, T + 2000)]),
    3: ('ok', [('REQUESTED', 'TO_MASTER', 'CONVERTER_DEPOSIT', '{"6":1000}', T + 3000), ('CONFIRMED', None, None, '{"6":700}', T + 3002),
               ('RETURNED', None, None, '{"6":300}', T + 3003)], [(13, 700, T + 3001)]),   # partial
    4: ('ok', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":100}', T + 4000), ('RETURNED', None, None, None, T + 4001)], []),  # rejected
    5: ('lost', [], [(2, 400, T + 5000)]),                         # DROP debit, the slave never got it
    6: ('duplicate', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":200}', T + 6000), ('RETURNED', None, None, None, T + 6002)],
        [(1, 200, T + 6001)]),                                     # APPLIED + RETURNED
    7: ('duplicate', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"7":300}', T + 7001)], [(2, 300, T + 7000), (6, 300, T + 7002)]),  # REFUNDED after DELIVERED
    8: ('created from nothing', [('REQUESTED', 'FROM_MASTER', 'ASYNC_BANK', '{"6":900}', T + 8000),
                                 ('DELIVERED', None, None, None, T + 8001)], []),   # withdraw delivered, master never debited
    9: ('parked', [], [(2, 100, T + 9000)]),                       # + TRANSFER_PARKED flag
    10: ('after snapshot', [], [(2, 50, T + 60000)]),              # newer than the oldest save (slave level.dat)
    11: ('data gap', [], [(2, 60, T + 50)]),                       # before the slave's diagnostics DB existed
    12: ('lost', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":80}', T + 12000), ('TIMED_OUT', None, None, None, T + 12001)], []),
    13: ('lost', [('FAILED', 'FROM_MASTER', 'DROP', '{"6":70}', T + 13001)], [(2, 70, T + 13000)]),   # FAILED, never refunded
    14: ('known gap', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"6":900}', T + 14000)], []),   # user-deletion payout, ref=null
    15: (None, [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":500}', T + 15000), ('CONFIRMED', None, None, None, T + 15001)], []),  # NO_LOG
    16: ('lost', [], [(2, 40, T + 2200)]),                        # after the slave's first row, before its created_ms: no gap
    17: ('ok', [('FAILED', 'FROM_MASTER', 'DROP', '{"6":90}', T + 17000)], []),   # leg passed on to the next slave
    18: ('ok', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"6":250}', T + 18001)], [(25, 250, T + 18000)]),   # ACCOUNT_DELETE_PAYOUT = outflow
    19: ('ok', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":120}', T + 19000)], []),   # non-success reply: nothing applied
    20: ('duplicate', [('REQUESTED', 'TO_MASTER', 'ASYNC_BANK', '{"6":130}', T + 20000)], [(1, 130, T + 20001)]),  # applied, slave saw no success
    21: ('known gap', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"6":30000}', T + 21001)], [(25, 300, T + 21000)]),  # pre-#136 100x payout
    22: ('known gap', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"501":500}', T + 22001)], [(25, 405, T + 22000, GOLD)]),  # 5 coins: 81 vs 100
    23: ('duplicate', [('DELIVERED', 'FROM_MASTER', 'DROP', '{"502":500}', T + 23001)], [(2, 405, T + 23000, GOLD_LATE)]),  # bound later
}
NO_LOG = {15}                                                      # REQUESTED with ledger_type NULL


def diag_db(path, sql, meta, rows=()):
    c = sqlite3.connect(path)
    c.execute('CREATE TABLE Meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
    c.executemany('INSERT INTO Meta VALUES (?,?)', [(k, str(v)) for k, v in meta.items()])
    for s in sql: c.execute(s)
    for q, r in rows: c.execute(q, r)
    c.commit(); c.close()


JOURNAL_SQL = ('CREATE TABLE TransferJournal (id INTEGER PRIMARY KEY AUTOINCREMENT, transfer_id TEXT NOT NULL, ts INTEGER NOT NULL, '
               'direction TEXT, path TEXT, step TEXT NOT NULL, account_number INTEGER, player_uuid TEXT, items_json TEXT, '
               'ledger_type INTEGER, context TEXT, detail TEXT)')
NOTEFLOW_OLD = 'CREATE TABLE NoteFlow (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, source TEXT NOT NULL, player_uuid TEXT, item_id INTEGER NOT NULL, amount INTEGER NOT NULL, context TEXT)'


def build_universe(root):
    """Master world (the regular new-format fixture + diagnostics) and one slave world under root/master, root/slave."""
    mw, sw = os.path.join(root, 'master'), os.path.join(root, 'slave')
    md, sd = os.path.join(mw, 'data'), os.path.join(sw, 'data')
    build(md, True)
    for d, uid, role, slave, save in ((md, MASTER_UID, 'MASTER', None, MASTER_SAVE), (sd, SLAVE_UID, 'SLAVE', 'slave_a', SLAVE_SAVE)):
        os.makedirs(os.path.join(d, 'ModUtilities'), exist_ok=True)
        os.makedirs(os.path.join(d, 'BankSystem', 'Database'), exist_ok=True)
        ident = {'server_uid': uid, 'role': role, 'slave_id': slave, 'master_uid': MASTER_UID if slave else None,
                 'first_start_ms': T, 'known_slaves': {} if slave else {'slave_a': {'uid': SLAVE_UID, 'last_seen_ms': T}}}
        with open(os.path.join(d, 'ModUtilities', 'server_identity.json'), 'w', encoding='utf-8') as f: json.dump(ident, f)
        with open(os.path.join(os.path.dirname(d), 'level.dat'), 'wb') as f: f.write(nbt.dumps({'Data': {'LastPlayed': save}}))
    # master: ledger rows with ref = transfer_id, a TRANSFER_PARKED flag, NoteFlow with villager columns, OrderOrigin
    bs = sqlite3.connect(os.path.join(md, 'BankSystem', 'Database', 'banksystem.db'))
    for n, (_, _, led) in TRANSFERS.items():
        bs.executemany('INSERT INTO TransactionLog (account_number,actor_uuid,kind,item_id,amount,ts,type_code,ref) VALUES (1,?,?,?,?,?,?,?)',
                       [(U1, 'X', (x + (MONEY,))[3], x[1], x[2], x[0], tid(n)) for x in led])
    bs.execute('CREATE TABLE SuspicionFlag (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, account_number INTEGER NOT NULL, '
               'flag_type INTEGER NOT NULL, severity INTEGER NOT NULL DEFAULT 0, amount INTEGER NOT NULL DEFAULT 0, ref TEXT, details TEXT, '
               'acknowledged INTEGER NOT NULL DEFAULT 0)')
    bs.execute('INSERT INTO SuspicionFlag (ts,account_number,flag_type,amount,ref) VALUES (?,1,6,100,?)', (T + 9001, tid(9)))
    bs.commit(); bs.close()
    with open(os.path.join(md, 'BankSystem', 'BankAccountBindings.nbt'), 'wb') as f:
        f.write(nbt.dumps({'version': 1, 'rows': [{'accountId': 1, 'itemShort': GOLD, 'ref': {}, 'boundAt': T},
                                                  {'accountId': 1, 'itemShort': GOLD_LATE, 'ref': {}, 'boundAt': T + 30000}]}))
    diag_db(os.path.join(md, 'BankSystem', 'Database', 'banksystem_diagnostics.db'),
            [JOURNAL_SQL, NOTEFLOW_OLD] + ['ALTER TABLE NoteFlow ADD COLUMN ' + c for c in ('villager_uuid TEXT', 'dimension TEXT', 'x INTEGER', 'y INTEGER', 'z INTEGER')],
            {'schema_version': 1, 'created_ms': T - 1000, 'bank_last_save_ms': BANK_SAVE},
            [('INSERT INTO NoteFlow (ts,source,player_uuid,item_id,amount,villager_uuid,dimension,x,y,z) VALUES (?,?,?,?,?,?,?,?,?,?)',
              (T + 500, 'stockmarket:villager_pay', U1, MONEY, 3, U2, 'minecraft:overworld', 1, 64, -2))])
    diag_db(os.path.join(md, 'StockMarket', 'Database', 'stockmarket_diagnostics.db'),
            [JOURNAL_SQL, NOTEFLOW_OLD, 'CREATE TABLE OrderOrigin (ref TEXT PRIMARY KEY, origin_server_uid TEXT, origin_name TEXT NOT NULL)'],
            {'schema_version': 1, 'created_ms': T - 1000},
            [('INSERT INTO OrderOrigin VALUES (?,?,?)', r) for r in
             (('sm:order:2', SLAVE_UID, 'slave_a'), ('sm:order:3', MASTER_UID, ''), ('sm:im:' + GROUP, SLAVE_UID, 'slave_a'))])
    sm = sqlite3.connect(os.path.join(md, 'StockMarket', 'Database', 'stockdata.db'))
    g1, g2 = uuid_longs(GROUP)
    sm.execute('INSERT INTO OrderHistory (itemid,accountid,userid_one,userid_two,type,amount,price,time,intermarket_group_one,intermarket_group_two)'
               ' VALUES (?,1,?,?,0,100,1000,?,?,?)', (DIA, *uuid_longs(U1), T + 70000, g1, g2))
    sm.commit(); sm.close()
    # slave: journal only (no banksystem.db), old NoteFlow without villager columns
    rows = [('INSERT INTO TransferJournal (transfer_id,ts,direction,path,step,account_number,player_uuid,items_json,ledger_type) VALUES (?,?,?,?,?,?,?,?,?)',
             (tid(n), t, d, p, step, 1 if step == 'REQUESTED' else None, U1 if d else None, items,   # DROP rows: no account / ledger_type
              None if n in NO_LOG or step != 'REQUESTED' else 13 if p == 'CONVERTER_DEPOSIT' else 1 if d == 'TO_MASTER' else 2))
            for n, (_, jr, _) in TRANSFERS.items() for step, d, p, items, t in jr]
    diag_db(os.path.join(sd, 'BankSystem', 'Database', 'banksystem_diagnostics.db'), [JOURNAL_SQL, NOTEFLOW_OLD],
            {'schema_version': 1, 'created_ms': CREATED}, rows)
    return mw, sw


def check_diagnostics():
    root = tempfile.mkdtemp(prefix='audit_selftest_')
    try:
        mw, sw = build_universe(root)
        with core.World(mw) as m, core.World(sw, copy=False) as s:
            # step 1 readers
            assert m.role() == 'MASTER' and s.role() == 'SLAVE' and s.label() == 'slave_a', (m.role(), s.role())
            assert not s.warnings, s.warnings      # a slave has no main DBs; that is expected, not a warning
            assert m.last_played == MASTER_SAVE and s.last_played == SLAVE_SAVE
            assert m.meta_ms(m.bsd, 'bank_last_save_ms') == BANK_SAVE and m.meta_ms(m.smd, 'bank_last_save_ms') is None
            nf = m.noteflow
            assert len(nf) == 1 and nf[0]['villager_uuid'] == U2 and nf[0]['y'] == 64 and nf[0]['amount'] == 3, nf
            assert s.noteflow == [] and 'villager_uuid' not in s.columns(s.bsd, 'NoteFlow')
            assert len(s.journal) == sum(len(j) for _, j, _ in TRANSFERS.values()) and m.journal == []
            assert m.origins['sm:order:2'] == (SLAVE_UID, 'slave_a') and s.origins == {}
            tr = {d['order_id']: d['origin'] for d in core.fill_dicts(m, m.fills()) if not d['intermarket']}
            assert tr[2] == 'slave_a' and tr[3] == 'master' and tr[1] == '', tr
            assert [d['origin'] for d in core.fill_dicts(m, m.fills()) if d['intermarket']] == ['slave_a']
            rb = m.rolled_back()
            assert {r['ref'] for r in rb} == {tid(1), tid(10), 'sm:order:11'}, rb   # rows after bank_last_save_ms
            st = m.diag_status()
            assert st['banksystem_diagnostics.db'] == 'Meta 3, TransferJournal 0, NoteFlow 1', st
            assert st['stockmarket_diagnostics.db'] == 'Meta 2, OrderOrigin 3', st
            assert 'OrderOrigin missing (no diagnostics data)' in s.diag_status()['stockmarket_diagnostics.db'] or \
                   s.diag_status()['stockmarket_diagnostics.db'] == 'no diagnostics data (file missing)'
            assert 'MASTER' in core.report_md(m, core.analyze(m, spikes=False))
            # step 2 join
            rows, warns = core.join([m, s])
            got = {r['transfer_id']: r for r in rows}
            for n, (cls, _, _) in TRANSFERS.items():
                assert (got[tid(n)]['class'] if tid(n) in got else None) == cls, (n, cls, got.get(tid(n)))
            assert len(rows) == sum(1 for c, _, _ in TRANSFERS.values() if c) and not warns, (len(rows), warns)
            assert 'passed on' in got[tid(17)]['note'] and 'not applied' in got[tid(19)]['note']
            assert got[tid(22)]['note'] == core.BOUND_RATIO and m.bound_slots == {(1, GOLD): T, (1, GOLD_LATE): T + 30000}
            assert 'rolled back' in got[tid(1)]['note'] and got[tid(1)]['slave'] == 'slave_a' and got[tid(1)]['player'] == 'Trader'
            assert 'started' in got[tid(11)]['note'] and 'oldest' in got[tid(10)]['note']
            assert got[tid(3)]['into_bank_master'] == got[tid(3)]['into_bank_slave'] == 7.0
            # without the slave save: slave-side gaps, no false 'lost'; without the master: master-side gaps
            alone = {r['transfer_id']: r['class'] for r in core.join([m])[0]}
            assert alone[tid(5)] == 'data gap' and alone[tid(9)] == 'parked'
            rows, warns = core.join([s])
            assert {r['class'] for r in rows} <= {'ok', 'data gap', 'known gap'} and any('no master' in x for x in warns), (rows, warns)
            assert core.UNLINKED_DROP in got[tid(14)]['note']
            assert [r['class'] for r in rows if r['transfer_id'] == tid(8)] == ['data gap']
            rows, warns = core.join([m, s, s])
            assert any('share server_uid' in x for x in warns), warns
    finally:
        shutil.rmtree(root, ignore_errors=True)


def check_no_diagnostics():
    """Older world: no diagnostics DBs, no identity, no level.dat -> clear notes, empty readers, no crash."""
    root = tempfile.mkdtemp(prefix='audit_selftest_')
    try:
        build(root, True)
        with core.World(root) as w:
            assert w.journal == [] and w.noteflow == [] and w.origins == {} and w.meta(w.bsd) == {}
            assert w.bsd is None and not any('diagnostics' in x for x in w.warnings), w.warnings
            st = w.diag_status()
            assert set(st.values()) == {'no diagnostics data (file missing)'}, st
            ss = w.schema_summary()
            assert ss['Last save (level.dat LastPlayed)'] == 'no last-save info' and w.rolled_back() is None
            assert ss['Ledger rows after the last bank save'] == 'no bank-save info'
            assert all(d['origin'] == '' for d in core.fill_dicts(w, w.fills()))
            assert core.join([w]) == ([], ['master: no last-save info (no level.dat LastPlayed)', 'master: no bank-save info (no bank_last_save_ms)'])
        # diagnostics file present but without the tables (Meta only)
        diag_db(os.path.join(root, 'BankSystem', 'Database', 'banksystem_diagnostics.db'), [], {'created_ms': T})
        with core.World(root) as w:
            assert w.journal == [] and w.noteflow == [] and w.meta_ms(w.bsd, 'created_ms') == T
            assert 'TransferJournal missing (no diagnostics data)' in w.diag_status()['banksystem_diagnostics.db']
    finally:
        shutil.rmtree(root, ignore_errors=True)


def main():
    check_nbt(); print('nbt parser: ok')
    check(True); print('new format (type_code/order_id/virtual_qty): ok')
    check(False); print('legacy 2.1.2 format: ok')
    check_payout_signs(); print('PAYOUT/DIVIDEND signed and legacy legs, SHARE_TRADE unknown sign: ok')
    check_corrupt(); print('corrupt db salvage: ok')
    check_no_diagnostics(); print('no diagnostics data (missing DB / tables): ok')
    check_diagnostics(); print('diagnostics readers + multi-save join (%d transfer classes): ok' % len({c for c, _, _ in TRANSFERS.values() if c}))
    print('SELFTEST PASSED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
