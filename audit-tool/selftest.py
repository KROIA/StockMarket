"""Self-check: builds tiny synthetic worlds (new + legacy format, plus a corrupted DB) and asserts the parsers,
reconciliation and anomaly flags. Run: python -I audit-tool/selftest.py  (exit code 0 = pass)."""
import os, sqlite3, sys, tempfile, shutil

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


def main():
    check_nbt(); print('nbt parser: ok')
    check(True); print('new format (type_code/order_id/virtual_qty): ok')
    check(False); print('legacy 2.1.2 format: ok')
    check_corrupt(); print('corrupt db salvage: ok')
    print('SELFTEST PASSED')
    return 0


if __name__ == '__main__':
    sys.exit(main())
