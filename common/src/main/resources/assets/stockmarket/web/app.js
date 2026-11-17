// StockMarket read-only web page: polls the JSON API of the master server (no build step, no framework).
'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const LC = window.LightweightCharts;
  const GAME_HOUR_MS = 50000;
  const WALL_LABELS = { 60000: '1m', 300000: '5m', 900000: '15m', 3600000: '1h', 14400000: '4h', 86400000: '1D' };

  const state = { markets: [], sel: null, tf: null, tfs: [], gameTime: false, candleStart: null, lastBar: null, historyAt: 0 };

  // ---- formatting
  const fmtPrice = n => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: Math.abs(n) < 1 ? 4 : 2 });
  const fmtVol = n => n.toLocaleString(undefined, { maximumFractionDigits: n < 10 ? 2 : 0 });
  const fmtTime = ms => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  // an untranslated key such as "item.mod.thing" -> show the item id instead
  const displayName = m => /^[a-z0-9_]+(\.[a-z0-9_]+)+$/.test(m.name) ? m.itemId : m.name;
  function tfLabel(tf) {
    if (!state.gameTime) return WALL_LABELS[tf] || (tf / 60000) + 'm';
    const h = tf / GAME_HOUR_MS;
    return h >= 168 ? (h / 168) + 'W' : h >= 24 ? (h / 24) + 'D' : h + 'h';
  }
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  async function api(path) {
    const r = await fetch(path, { cache: 'no-store' });
    if (!r.ok) throw new Error(path + ': ' + r.status);
    return r.json();
  }
  function setLive(ok) {
    $('live').className = 'live ' + (ok ? 'on' : 'off');
    $('liveText').textContent = ok ? 'live' : 'offline';
  }

  // ---- chart (Lightweight Charts v5 API)
  const chart = LC.createChart($('chart'), {
    autoSize: true,
    layout: { background: { type: 'solid', color: '#0d1424' }, textColor: '#7d8aa3', attributionLogo: true },
    grid: { vertLines: { color: '#152035' }, horzLines: { color: '#152035' } },
    rightPriceScale: { borderColor: '#1c2840' },
    timeScale: {
      borderColor: '#1c2840', timeVisible: true, secondsVisible: false,
      tickMarkFormatter: (t, type) => {
        const d = new Date(t * 1000);
        return type < 3 ? d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
          : d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
      },
    },
    localization: { timeFormatter: t => new Date(t * 1000).toLocaleString() },
  });
  const candleSeries = chart.addSeries(LC.CandlestickSeries, {
    upColor: '#26a69a', downColor: '#ef5350', borderVisible: false, wickUpColor: '#26a69a', wickDownColor: '#ef5350',
  });
  const volumeSeries = chart.addSeries(LC.HistogramSeries, {
    priceScaleId: '', priceFormat: { type: 'volume' }, lastValueVisible: false, priceLineVisible: false,
  });
  volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

  // ---- markets
  function renderMarkets() {
    const q = $('search').value.trim().toLowerCase();
    const ul = $('markets');
    ul.replaceChildren();
    for (const m of state.markets) {
      const name = displayName(m);
      if (q && !name.toLowerCase().includes(q) && !m.itemId.toLowerCase().includes(q)) continue;
      const li = el('li', m.id === state.sel ? 'sel' : '');
      li.title = m.itemId;
      li.append(el('span', 'dot ' + m.state), el('span', 'name', name), el('span', 'price', fmtPrice(m.price)));
      li.onclick = () => select(m.id);
      ul.append(li);
    }
  }
  function renderHead() {
    const m = state.markets.find(x => x.id === state.sel);
    $('mName').textContent = m ? displayName(m) : 'No market selected';
    $('mPrice').textContent = m ? fmtPrice(m.price) : '';
    $('mState').textContent = m ? m.state : '';
    $('mId').textContent = m ? m.itemId : '';
    document.title = m ? displayName(m) + ' ' + fmtPrice(m.price) + ' - StockMarket' : 'StockMarket';
  }
  function renderTimeframes() {
    const box = $('tfs');
    box.replaceChildren();
    for (const tf of state.tfs) {
      const b = el('button', tf === state.tf ? 'sel' : '', tfLabel(tf));
      b.onclick = () => { state.tf = tf; renderTimeframes(); loadHistory(true); };
      box.append(b);
    }
  }

  async function pollMarkets() {
    try {
      const d = await api('api/markets');
      setLive(true);
      $('currency').textContent = 'Currency: ' + d.currency.name;
      const tfsChanged = d.gameTime !== state.gameTime || d.timeframes.join() !== state.tfs.join();
      state.gameTime = d.gameTime;
      state.tfs = d.timeframes;
      if (tfsChanged || !state.tfs.includes(state.tf)) {
        state.tf = state.tfs.includes(state.tf) ? state.tf : state.tfs[Math.min(1, state.tfs.length - 1)];
        renderTimeframes();
      }
      state.markets = d.markets.sort((a, b) => displayName(a).localeCompare(displayName(b)));
      if (state.sel === null || !state.markets.some(m => m.id === state.sel)) {
        if (state.markets.length) select(state.markets[0].id);
      }
      renderMarkets();
      renderHead();
      const m = state.markets.find(x => x.id === state.sel);
      if (m) {
        if (m.candleStart !== state.candleStart || Date.now() - state.historyAt > 30000) {
          loadHistory(false);
        } else if (state.lastBar) {
          // move the newest candle with the live price
          const b = state.lastBar;
          b.c = m.price; b.h = Math.max(b.h, m.price); b.l = Math.min(b.l, m.price);
          candleSeries.update({ time: b.t, open: b.o, high: b.h, low: b.l, close: b.c });
        }
      }
    } catch (e) {
      setLive(false);
    }
  }

  function select(id) {
    if (state.sel === id) return;
    state.sel = id;
    state.candleStart = null;
    state.lastBar = null;
    candleSeries.setData([]);
    volumeSeries.setData([]);
    renderMarkets();
    renderHead();
    loadHistory(true);
    pollBook();
    pollTrades();
  }

  async function loadHistory(fit) {
    const id = state.sel, tf = state.tf;
    if (id === null || !tf) return;
    state.historyAt = Date.now();
    const m0 = state.markets.find(x => x.id === id);
    state.candleStart = m0 ? m0.candleStart : null; // set before the fetch, so the next poll doesn't reload again
    try {
      const d = await api(`api/history?market=${id}&tf=${tf}&max=500`);
      if (id !== state.sel || tf !== state.tf) return; // selection changed meanwhile
      candleSeries.setData(d.candles.map(c => ({ time: c.t, open: c.o, high: c.h, low: c.l, close: c.c })));
      volumeSeries.setData(d.candles.map(c => ({
        time: c.t, value: c.v, color: c.c >= c.o ? 'rgba(38,166,154,.45)' : 'rgba(239,83,80,.45)',
      })));
      state.lastBar = d.candles.length ? { ...d.candles[d.candles.length - 1] } : null;
      if (fit) chart.timeScale().fitContent();
    } catch (e) { /* next poll retries */ }
  }

  // ---- order book, open orders, trades
  async function pollBook() {
    const id = state.sel;
    if (id === null) return;
    try {
      const d = await api(`api/book?market=${id}`);
      if (id !== state.sel) return;
      const book = $('book');
      book.replaceChildren();
      const asks = d.levels.filter(l => l.v < 0).sort((a, b) => b.p - a.p);
      const bids = d.levels.filter(l => l.v > 0).sort((a, b) => b.p - a.p);
      const max = Math.max(1e-9, ...d.levels.map(l => Math.abs(l.v)));
      const row = (l, cls) => {
        const r = el('div', 'lvl ' + cls);
        const bar = el('span', 'bar');
        bar.style.width = (Math.abs(l.v) / max * 100).toFixed(1) + '%';
        r.append(bar, el('span', '', fmtPrice(l.p)), el('span', '', fmtVol(Math.abs(l.v))));
        return r;
      };
      asks.forEach(l => book.append(row(l, 'ask')));
      book.append(el('div', 'mid', fmtPrice(d.price)));
      bids.forEach(l => book.append(row(l, 'bid')));
      if (!asks.length && !bids.length) book.append(el('div', 'empty', 'No volume near the price'));

      const tbody = $('orders');
      tbody.replaceChildren();
      for (const o of d.orders) {
        const tr = el('tr');
        tr.append(el('td', '', fmtPrice(o.p)), el('td', 'buy', o.buy ? fmtVol(o.buy) : ''),
          el('td', 'sell', o.sell ? fmtVol(o.sell) : ''), el('td', '', String(o.count)));
        tbody.append(tr);
      }
      if (!d.orders.length) {
        const tr = el('tr'); const td = el('td', 'empty', 'No open player orders'); td.colSpan = 4; tr.append(td); tbody.append(tr);
      }
    } catch (e) { /* next poll retries */ }
  }

  async function pollTrades() {
    const id = state.sel;
    if (id === null) return;
    try {
      const d = await api(`api/trades?market=${id}&limit=50`);
      if (id !== state.sel) return;
      const tbody = $('trades');
      tbody.replaceChildren();
      for (const t of d.trades) {
        const tr = el('tr');
        tr.title = t.type + ' order';
        tr.append(el('td', '', fmtTime(t.t)), el('td', t.side, t.side), el('td', '', fmtVol(t.amount)), el('td', '', fmtPrice(t.price)));
        tbody.append(tr);
      }
      if (!d.trades.length) {
        const tr = el('tr'); const td = el('td', 'empty', 'No trades in the last 24 h'); td.colSpan = 4; tr.append(td); tbody.append(tr);
      }
    } catch (e) { /* next poll retries */ }
  }

  function tickClock() { $('clock').textContent = new Date().toLocaleTimeString(); }

  $('search').addEventListener('input', renderMarkets);
  tickClock();
  pollMarkets();
  setInterval(tickClock, 1000);
  setInterval(pollMarkets, 2000);
  setInterval(pollBook, 2000);
  setInterval(pollTrades, 5000);
})();
