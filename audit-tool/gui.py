"""Thin Tkinter GUI over core.py: tables for trades / P&L / timeline / flags, canvas for candles and balances."""
import math, os, sys
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core


class App:
    def __init__(self, root, world=None):
        self.root, self.w, self.ws, self.rows = root, None, [], []
        root.title('StockMarket / BankSystem audit (read-only)')
        root.geometry('1300x800')
        bar = ttk.Frame(root); bar.pack(fill='x', padx=4, pady=4)
        self.path = tk.StringVar(value=world or '')
        ttk.Entry(bar, textvariable=self.path, width=50).pack(side='left')
        ttk.Button(bar, text='Browse', command=self.browse).pack(side='left')
        ttk.Button(bar, text='+ World', command=self.add_world).pack(side='left')
        ttk.Button(bar, text='Load', command=self.load).pack(side='left')
        self.player, self.item, self.t0, self.t1 = (tk.StringVar() for _ in range(4))
        for label, var, wd in (('Player', self.player, 16), ('ItemID', self.item, 7), ('From', self.t0, 16), ('To', self.t1, 16)):
            ttk.Label(bar, text=' ' + label).pack(side='left')
            if var is self.player:
                self.pbox = ttk.Combobox(bar, textvariable=var, width=wd); self.pbox.pack(side='left')
            else:
                ttk.Entry(bar, textvariable=var, width=wd).pack(side='left')
        bar2 = ttk.Frame(root); bar2.pack(fill='x', padx=4)
        for t, c in (('Trades', self.trades), ('P&L', self.pnl), ('Timeline', self.timeline), ('Candles', self.candles),
                     ('Flags', self.flags), ('Transfers', self.transfers), ('Export CSV', self.export)):
            ttk.Button(bar2, text=t, command=c).pack(side='left')
        self.log = tk.BooleanVar(value=True)
        ttk.Checkbutton(bar2, text='log price scale', variable=self.log).pack(side='left')
        self.status = tk.StringVar(value='Load a world copy (world dir, world/data, or a dir with BankSystem/ + StockMarket/). '
                                         'Several worlds (master + slaves) separated by ";" for Transfers.')
        ttk.Label(bar2, textvariable=self.status).pack(side='left', padx=10)
        pane = ttk.PanedWindow(root, orient='vertical'); pane.pack(fill='both', expand=True)
        tf = ttk.Frame(pane); self.tree = ttk.Treeview(tf, show='headings')
        sb = ttk.Scrollbar(tf, command=self.tree.yview); self.tree.configure(yscrollcommand=sb.set)
        sb.pack(side='right', fill='y'); self.tree.pack(fill='both', expand=True)
        self.cv = tk.Canvas(pane, bg='white', height=380)
        pane.add(tf, weight=1); pane.add(self.cv, weight=1)
        if world: self.load()

    # -- plumbing
    def browse(self):
        d = filedialog.askdirectory()
        if d: self.path.set(d)

    def add_world(self):
        d = filedialog.askdirectory()
        if d: self.path.set('%s;%s' % (self.path.get(), d) if self.path.get().strip() else d)

    def load(self):
        self.status.set('Loading (copying databases to a temp dir)...'); self.root.update_idletasks()
        try:
            for w in self.ws: w.close()
            self.ws, self.w = [], None
            for p in [p.strip() for p in self.path.get().split(';') if p.strip()]: self.ws.append(core.World(p))
            # views other than Transfers use the master (or the first world)
            self.w = next((w for w in self.ws if w.role() != 'SLAVE'), self.ws[0] if self.ws else None)
            if not self.w: raise ValueError('no world given')
            names = sorted({self.w.pname(f.uuid) for f in self.w.fills()} | set(self.w.names.values()), key=str.lower)
            self.pbox['values'] = names
            self.status.set('%d orders, %d players, %d items. %s' % (len(self.w.fills()), len(names), len(self.w.items),
                                                                     '; '.join(self.w.warnings)[:200]))
        except Exception as e:   # shown to the user, the GUI keeps running
            messagebox.showerror('Load failed', str(e)); self.status.set('Load failed')

    def args(self):
        if not self.w: raise ValueError('load a world first')
        u = self.w.find_player(self.player.get()) if self.player.get().strip() else None
        item = int(self.item.get()) if self.item.get().strip() else None
        return u, item, core.parse_time(self.t0.get().strip()), core.parse_time(self.t1.get().strip())

    def show(self, rows):
        self.rows = rows
        cols = list(rows[0]) if rows else []
        self.tree.delete(*self.tree.get_children())
        self.tree['columns'] = cols
        for c in cols:
            self.tree.heading(c, text=c); self.tree.column(c, width=110, stretch=True)
        for r in rows[:20000]:
            self.tree.insert('', 'end', values=[r.get(c, '') for c in cols])
        self.status.set('%d rows%s' % (len(rows), ' (first 20000 shown, export for all)' if len(rows) > 20000 else ''))

    def guard(fn):
        def run(self):
            try:
                fn(self)
            except Exception as e:
                messagebox.showerror('Error', str(e))
        return run

    # -- views
    @guard
    def trades(self):
        u, item, t0, t1 = self.args()
        self.show(core.fill_dicts(self.w, core.filter_fills(self.w.fills(), u, item, t0, t1)))

    @guard
    def pnl(self):
        u, item, t0, t1 = self.args()
        self.show(core.pnl_dicts(self.w, core.pnl(core.filter_fills(self.w.fills(), u, item, t0, t1))[0]))

    @guard
    def flags(self):
        u, item, t0, t1 = self.args()
        self.status.set('Analyzing (scans MarketPrice, may take a minute)...'); self.root.update_idletasks()
        fl = core.analyze(self.w, t0, t1)['flags']
        self.show([f for f in fl if u is None or f['uuid'] in ('', u)])

    @guard
    def timeline(self):
        u, item, t0, t1 = self.args()
        if not u: raise ValueError('pick a player')
        rows, series = [], {}
        for acct in self.w.accounts_of(u):
            prev = {}
            for a, i, b, l, t in self.w.balance_history(acct, item):
                d = b + l - prev.get(i, 0); prev[i] = b + l
                if (t0 is None or t >= t0) and (t1 is None or t < t1):
                    rows.append({'time': core.ts(t), 'account': a, 'item': self.w.iname(i), 'delta': d / core.SCALE,
                                 'balance': b / core.SCALE, 'locked': l / core.SCALE})
                    series.setdefault((a, i), []).append((t, (b + l) / core.SCALE))
        rows.sort(key=lambda r: r['time']); self.show(rows)
        # plot money (or the chosen item) per account
        key = [k for k in series if k[1] == (item if item is not None else self.w.money_id)]
        self.plot_lines({'acct %d %s' % k: series[k] for k in key}, log=False)

    @guard
    def candles(self):
        u, item, t0, t1 = self.args()
        if item is None or t0 is None or t1 is None: raise ValueError('candles need ItemID, From and To')
        cs = self.w.candles(item, t0, t1)
        fills = core.filter_fills(self.w.fills(), u, item, t0, t1) if u else []
        self.show([{'time': core.ts(t), 'open': o / 100, 'low': lo / 100, 'high': h / 100, 'volume': v / core.SCALE} for t, o, lo, h, v in cs])
        self.draw_candles(cs, fills)

    @guard
    def transfers(self):
        if not self.ws: raise ValueError('load a world first')
        rows, warns = core.join(self.ws)
        self.show(rows)
        counts = {}
        for r in rows: counts[r['class']] = counts.get(r['class'], 0) + 1
        self.status.set('%d transfers: %s. %s' % (len(rows), ', '.join('%s %d' % kv for kv in sorted(counts.items())),
                                                  '; '.join(warns)[:300]))

    @guard
    def export(self):
        if not self.rows: raise ValueError('nothing to export')
        p = filedialog.asksaveasfilename(defaultextension='.csv', filetypes=[('CSV', '*.csv')])
        if p: core.write_csv(p, self.rows); self.status.set('wrote %s' % p)

    # -- drawing
    def axes(self, tmin, tmax, vmin, vmax, log):
        W, H, m = self.cv.winfo_width() or 1200, self.cv.winfo_height() or 380, 60
        f = (lambda v: math.log10(max(v, 1e-9))) if log else (lambda v: v)
        lo, hi = f(vmin), f(vmax)
        if hi <= lo: hi = lo + 1
        X = lambda t: m + (t - tmin) / max(tmax - tmin, 1) * (W - 2 * m)
        Y = lambda v: H - 25 - (f(v) - lo) / (hi - lo) * (H - 45)
        self.cv.delete('all')
        for k in range(5):
            v = lo + (hi - lo) * k / 4; val = 10 ** v if log else v
            self.cv.create_text(5, Y(val), text=format(val, ',.2f'), anchor='w', font=('TkDefaultFont', 7))
        self.cv.create_text(m, H - 10, text=core.ts(tmin), anchor='w'); self.cv.create_text(W - m, H - 10, text=core.ts(tmax), anchor='e')
        return X, Y

    def draw_candles(self, cs, fills):
        if not cs: self.cv.delete('all'); return
        tmin, tmax = cs[0][0], cs[-1][0] + 60000
        prices = [r[2] / 100 for r in cs] + [r[3] / 100 for r in cs] + [f.price / 100 for f in fills]
        log = self.log.get()
        X, Y = self.axes(tmin, tmax, max(min(prices), 0.01), max(prices), log)
        for k, (t, o, lo, h, v) in enumerate(cs):
            c = cs[k + 1][1] if k + 1 < len(cs) else o     # close = next open
            x = X(t + 30000); col = 'green' if c >= o else 'red'
            self.cv.create_line(x, Y(max(h, 1) / 100), x, Y(max(lo, 1) / 100), fill=col)
            self.cv.create_rectangle(x - 2, Y(max(o, 1) / 100), x + 2, Y(max(c, 1) / 100), outline=col, fill=col)
        for f in fills:   # buys: blue up-triangle, sells: magenta down-triangle
            x, y = X(f.time), Y(max(f.price, 1) / 100)
            pts = (x, y - 7, x - 5, y + 2, x + 5, y + 2) if f.amount > 0 else (x, y + 7, x - 5, y - 2, x + 5, y - 2)
            self.cv.create_polygon(*pts, fill='blue' if f.amount > 0 else 'magenta')

    def plot_lines(self, series, log):
        pts = [p for s in series.values() for p in s]
        if not pts: self.cv.delete('all'); return
        X, Y = self.axes(min(p[0] for p in pts), max(p[0] for p in pts), min(p[1] for p in pts), max(p[1] for p in pts), log)
        colors = ['blue', 'red', 'green', 'orange', 'purple', 'brown']
        for n, (name, s) in enumerate(series.items()):
            col = colors[n % len(colors)]
            if len(s) > 1: self.cv.create_line(*[c for t, v in s for c in (X(t), Y(v))], fill=col)
            self.cv.create_text(70, 10 + 14 * n, text=name, fill=col, anchor='w')


def main(world=None):
    root = tk.Tk()
    App(root, world)
    root.mainloop()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else None))
