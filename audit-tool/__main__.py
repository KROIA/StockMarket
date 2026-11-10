"""CLI of the offline StockMarket + BankSystem audit tool. Run: python -I audit-tool <command> <world_copy> ..."""
import argparse, collections, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # the tool's own dir only, never the world dir
import core


def thresholds(p):
    g = p.add_argument_group('anomaly thresholds')
    g.add_argument('--pump-ratio', type=float, default=2.0, help='sell price / preceding buy price (default 2)')
    g.add_argument('--pump-window', type=float, default=30, help='max seconds between buy and sell (default 30)')
    g.add_argument('--max-rate', type=int, default=5, help='orders per second per player (default 5)')
    g.add_argument('--pnl-hour', type=float, default=1e6, help='realized $ per player per hour (default 1,000,000)')
    g.add_argument('--price-ratio', type=float, default=10, help='fill price vs typical price, both ways (default 10)')
    g.add_argument('--spike-ratio', type=float, default=5, help='1-min candle high / low (default 5)')
    g.add_argument('--recon-threshold', type=float, default=1000, help='unexplained balance change, $ or items (default 1000)')
    g.add_argument('--no-spikes', action='store_true', help='skip the full MarketPrice scan')


def main(argv=None):
    ap = argparse.ArgumentParser(prog='audit', description='Offline audit of StockMarket + BankSystem world copies (read-only).')
    sub = ap.add_subparsers(dest='cmd', required=True)

    def cmd(name, help_, player=False, item=False, csv=True):
        p = sub.add_parser(name, help=help_)
        p.add_argument('world', help='world copy (world dir, world/data, or a dir holding BankSystem/ and StockMarket/)')
        p.add_argument('--no-copy', action='store_true', help='open the DBs in place with mode=ro instead of copying to a temp dir')
        p.add_argument('--from', dest='t0', help='UTC start, YYYY-MM-DD[ HH:MM]')
        p.add_argument('--to', dest='t1', help='UTC end (exclusive)')
        if player: p.add_argument('--player', help='name or UUID prefix')
        if item: p.add_argument('--item', type=int, help='ItemID / market id')
        if csv: p.add_argument('--csv', help='write the result as CSV to this file')
        return p

    p = cmd('report', 'one-command markdown report (+ optional CSV dir)', csv=False)
    p.add_argument('--out', default='report.md'); p.add_argument('--csv-dir'); p.add_argument('--top', type=int, default=25)
    thresholds(p)
    p = cmd('names', 'list players (uuid, name, accounts, banknotes) and items', csv=True)
    p.add_argument('--items', action='store_true', help='list items instead of players')
    cmd('trades', 'per-player trade history', player=True, item=True)
    cmd('pnl', 'realized P&L per player and item', player=True, item=True)
    p = cmd('timeline', 'balance timeline (BalanceHistory) of a player or account', player=True, item=True)
    p.add_argument('--account', type=int); p.add_argument('--min-delta', type=float, default=0, help='hide changes below this ($/items)')
    cmd('candles', '1-min candles of a market with the player\'s fills', player=True, item=True)
    p = cmd('flags', 'anomaly flags only'); thresholds(p)
    p = cmd('diag', 'diagnostics DB rows of one world (Meta + identity, TransferJournal, NoteFlow, OrderOrigin)')
    p.add_argument('--table', choices=('meta', 'journal', 'noteflow', 'origin'), default='meta')
    p = sub.add_parser('join', help='join master + slave saves by transfer_id and classify every cross-server transfer')
    p.add_argument('worlds', nargs='+', help='world copies: the master and its slaves')
    p.add_argument('--no-copy', action='store_true', help='open the DBs in place with mode=ro instead of copying to a temp dir')
    p.add_argument('--csv', help='write the result as CSV to this file')
    sub.add_parser('gui', help='open the Tkinter GUI').add_argument('world', nargs='?', help='world dir; several separated by ";"')
    sub.add_parser('selftest', help='run the built-in self-check')

    a = ap.parse_args(argv)
    if a.cmd == 'gui':
        import gui; return gui.main(a.world)
    if a.cmd == 'selftest':
        import selftest; return selftest.main()
    if a.cmd == 'join':
        ws = []
        try:
            for p in a.worlds: ws.append(core.World(p, copy=not a.no_copy))
            rows, warns = core.join(ws)
            emit(rows, None, a.csv)
            counts = collections.Counter(r['class'] for r in rows)
            sys.stdout.flush()
            print('summary: %s' % (', '.join('%s %d' % kv for kv in sorted(counts.items())) or 'no transfers'), file=sys.stderr)
            for x in warns + ['%s: %s' % (w.label(), x) for w in ws for x in w.warnings]: print('warning:', x, file=sys.stderr)
        finally:
            for w in ws: w.close()
        return 0

    with core.World(a.world, copy=not a.no_copy) as w:
        t0, t1 = core.parse_time(a.t0), core.parse_time(a.t1)
        u = w.find_player(a.player) if getattr(a, 'player', None) else None
        item = getattr(a, 'item', None)
        rows = header = None

        if a.cmd == 'report':
            res = core.analyze(w, t0, t1, a.pump_ratio, a.pump_window, a.max_rate, a.pnl_hour, a.price_ratio,
                               a.spike_ratio, a.recon_threshold, not a.no_spikes)
            open(a.out, 'w', encoding='utf-8').write(core.report_md(w, res, a.top))
            print('wrote', a.out)
            if a.csv_dir:
                os.makedirs(a.csv_dir, exist_ok=True)
                for name, r in (('fills', core.fill_dicts(w, res['fills'])), ('pnl', core.pnl_dicts(w, res['pnl'])),
                                ('flags', res['flags']), ('recon', res['recon'])):
                    if r: core.write_csv(os.path.join(a.csv_dir, name + '.csv'), r)
                print('wrote CSVs to', a.csv_dir)
            for d in core.pnl_dicts(w, res['pnl'])[:5]:
                print('  %-14s %-8s %-18s realized %s cash %s' % (d['player'], d['uuid'][:8], d['item'],
                                                                 core.usd(d['realized_pnl'] * 100), core.usd(d['cash_pnl'] * 100)))
            return 0

        if a.cmd == 'names':
            if a.items:
                rows = [{'item_id': i, 'name': n} for i, n in sorted(w.items.items())]
            else:
                us = set(w.names) | {f.uuid for f in w.fills()}
                rows = [{'uuid': x, 'name': w.names.get(x, ''), 'accounts': ' '.join(map(str, w.accounts_of(x))),
                         'banknotes': w.notes.get(x, 0) / 100} for x in sorted(us, key=lambda x: w.pname(x).lower())]
        elif a.cmd == 'trades':
            rows = core.fill_dicts(w, core.filter_fills(w.fills(), u, item, t0, t1))
        elif a.cmd == 'pnl':
            rows = core.pnl_dicts(w, core.pnl(core.filter_fills(w.fills(), u, item, t0, t1))[0])
        elif a.cmd == 'timeline':
            accts = [a.account] if a.account is not None else w.accounts_of(u) if u else None
            if not accts: ap.error('timeline needs --player or --account')
            rows, prev = [], {}
            for ac in accts:
                for acct, i, b, l, t in w.balance_history(ac, item):
                    d = (b + l) - prev.get((acct, i), 0); prev[(acct, i)] = b + l
                    if (t0 is None or t >= t0) and (t1 is None or t < t1) and abs(d) >= a.min_delta * core.SCALE:
                        rows.append({'time': core.ts(t), 'account': acct, 'item': w.iname(i), 'delta': d / core.SCALE,
                                     'balance': b / core.SCALE, 'locked': l / core.SCALE})
            rows.sort(key=lambda r: r['time'])
        elif a.cmd == 'candles':
            if item is None or t0 is None or t1 is None: ap.error('candles needs --item, --from and --to')
            rows = [{'time': core.ts(t), 'open': o / 100, 'low': lo / 100, 'high': h / 100, 'volume': v / core.SCALE}
                    for t, o, lo, h, v in w.candles(item, t0, t1)]
            if u:
                for f in core.filter_fills(w.fills(), u, item, t0, t1):
                    rows.append({'time': core.ts(f.time), 'open': '', 'low': '', 'high': '', 'volume': '',
                                 'fill': '%s %s @ %s' % ('BUY' if f.amount > 0 else 'SELL', core.qty(abs(f.amount)), core.usd(f.price))})
                rows.sort(key=lambda r: r['time'])
                header = ['time', 'open', 'low', 'high', 'volume', 'fill']
        elif a.cmd == 'flags':
            res = core.analyze(w, t0, t1, a.pump_ratio, a.pump_window, a.max_rate, a.pnl_hour, a.price_ratio,
                               a.spike_ratio, a.recon_threshold, not a.no_spikes)
            rows = res['flags']
        elif a.cmd == 'diag':
            if a.table == 'meta':
                rows = [{'source': os.path.basename(rel), 'key': k, 'value': v}
                        for rel, db in ((core.DIAG_BS, w.bsd), (core.DIAG_SM, w.smd)) for k, v in sorted(w.meta(db).items())]
                rows += [{'source': 'server_identity.json', 'key': k, 'value': v} for k, v in w.identity.items()]
                rows += [{'source': 'level.dat', 'key': 'LastPlayed', 'value': w.last_played or 'no last-save info'}]
                rows += [{'source': n, 'key': 'tables', 'value': s} for n, s in w.diag_status().items()]
            elif a.table == 'origin':
                rows = [{'ref': r, 'origin_server_uid': u, 'origin_name': n} for r, (u, n) in w.origins.items()]
            else:
                rows = [dict(r, time=core.ts(r.get('ts'))) for r in (w.journal if a.table == 'journal' else w.noteflow)
                        if (t0 is None or (r.get('ts') or 0) >= t0) and (t1 is None or (r.get('ts') or 0) < t1)]
            if not rows:
                print('no diagnostics data: %s' % '; '.join('%s: %s' % kv for kv in w.diag_status().items()), file=sys.stderr)

        emit(rows, header, a.csv)
        for x in w.warnings: print('warning:', x, file=sys.stderr)
    return 0


def emit(rows, header, csv_path):
    """Print rows as TSV, or write them as CSV when csv_path is set."""
    if csv_path:
        core.write_csv(csv_path, rows, header); print('wrote %d rows to %s' % (len(rows), csv_path))
    else:
        keys = header or (list(rows[0]) if rows else [])
        print('\t'.join(keys))
        for r in rows: print('\t'.join(str(r.get(k, '')) for k in keys))


if __name__ == '__main__':
    sys.exit(main())
