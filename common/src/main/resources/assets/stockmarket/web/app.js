// StockMarket read-only web page: polls the JSON API of the master server (no build step, no framework).
'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const LC = window.LightweightCharts;
  const GAME_HOUR_MS = 50000;
  const WALL_LABELS = { 60000: '1m', 300000: '5m', 900000: '15m', 3600000: '1h', 14400000: '4h', 86400000: '1D' };

  const state = { markets: [], sel: null, tf: null, tfs: [], gameTime: false, candleStart: null, lastBar: null, historyAt: 0,
    mode: 'market', have: null, want: null, pairStarts: '', pairAt: 0, pairLast: null, pairOptions: '', startPair: false };

  // ---- formatting
  const fmtPrice = n => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: Math.abs(n) < 1 ? 4 : 2 });
  const fmtVol = n => n.toLocaleString(undefined, { maximumFractionDigits: n < 10 ? 2 : 0 });
  const fmtRate = n => n.toLocaleString(undefined, { maximumFractionDigits: n < 1 ? 6 : 4 });
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
    // candles use the top 5-75 %, the volume histogram the bottom 20 %: they never overlap, at any height
    rightPriceScale: { borderColor: '#1c2840', scaleMargins: { top: 0.05, bottom: 0.25 } },
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
  // pair view: price ratio as a line on the same chart, visible only in pair mode
  const pairSeries = chart.addSeries(LC.LineSeries, { color: '#2ec4b6', lineWidth: 2, visible: false,
    priceFormat: { type: 'price', precision: 4, minMove: 0.0001 } });

  /**
   * Pair rate history like the in-game cross-rate chart (CrossRateMarket): candles of both markets matched by
   * timestamp within half a timeframe, rate = want / have = how many "have" items one "want" item costs.
   * Buckets missing in either market or with a price of 0 are skipped. Pure: candles in, line points out.
   * @param have candles of the "I have" market ({t seconds, c close}, oldest first)
   * @param want candles of the "I want" market, same format
   * @param tfMs timeframe in milliseconds
   */
  function ratioSeries(have, want, tfMs) {
    const tol = tfMs / 2000; // candle times are in seconds
    const out = [];
    let i = 0, j = 0;
    while (i < have.length && j < want.length) {
      const d = have[i].t - want[j].t;
      if (Math.abs(d) <= tol) {
        if (have[i].c > 0 && want[j].c > 0) out.push({ time: have[i].t, value: want[j].c / have[i].c });
        i++; j++;
      } else if (d > 0) j++;
      else i++;
    }
    return out;
  }

  // ---- item icons: the PNG from the server, else a coloured badge with initials
  const missingIcons = new Set(); // 404s are not cached by the browser: ask once per page load
  function icon(itemId, name) {
    const box = el('span', 'icon');
    const badge = () => {
      let h = 0;
      for (const ch of itemId) h = (h * 31 + ch.charCodeAt(0)) | 0;
      box.style.background = `hsl(${Math.abs(h) % 360} 45% 35%)`;
      box.textContent = name.replace(/[^A-Za-z0-9 ]/g, '').split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase() || '?';
    };
    if (missingIcons.has(itemId)) { badge(); return box; }
    const img = new Image();
    img.alt = '';
    img.onerror = () => { missingIcons.add(itemId); img.remove(); badge(); };
    img.src = 'api/icon?item=' + encodeURIComponent(itemId);
    box.append(img);
    return box;
  }

  // ---- markets, grouped like the in-game trade screen: items and company shares
  const GROUPS = [['money', 'Items'], ['shares', 'Company shares']];
  const collapsed = new Set();
  function renderMarkets() {
    const q = $('search').value.trim().toLowerCase();
    const box = $('markets');
    box.replaceChildren();
    for (const [cat, title] of GROUPS) {
      const list = state.markets.filter(m => m.category === cat && (!q || displayName(m).toLowerCase().includes(q) || m.itemId.toLowerCase().includes(q)));
      if (!list.length) continue;
      const group = el('details');
      group.open = !collapsed.has(cat);
      group.ontoggle = () => { if (group.open) collapsed.delete(cat); else collapsed.add(cat); };
      group.append(el('summary', '', `${title} (${list.length})`));
      const ul = el('ul');
      for (const m of list) {
        const li = el('li', m.id === state.sel ? 'sel' : '');
        li.title = m.itemId;
        li.append(el('span', 'dot ' + m.state), icon(m.itemId, displayName(m)), el('span', 'name', displayName(m)), el('span', 'price', fmtPrice(m.price)));
        li.onclick = () => { setMode('market'); select(m.id); };
        ul.append(li);
      }
      group.append(ul);
      box.append(group);
    }
  }
  function renderHead() {
    if (state.mode === 'pair') return renderPair();
    const m = state.markets.find(x => x.id === state.sel);
    $('mName').textContent = m ? displayName(m) : 'No market selected';
    const key = m ? m.itemId : '';
    if ($('mIcon').dataset.item !== key) { // keep the image while the market stays the same
      $('mIcon').dataset.item = key;
      $('mIcon').replaceChildren(...(m ? [icon(m.itemId, displayName(m))] : []));
    }
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
      b.onclick = () => { state.tf = tf; renderTimeframes(); if (state.mode === 'pair') loadPair(true); else loadHistory(true); };
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
      renderPairOptions(false);
      renderHead();
      const m = state.markets.find(x => x.id === state.sel);
      if (state.mode === 'pair') {
        refreshPair();
      } else if (m) {
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

  // ---- pair view (derived from the two items' markets, not a market of its own)
  const byId = id => state.markets.find(m => m.id === id);
  function setMode(mode) {
    if (state.mode === mode) return;
    state.mode = mode;
    $('modeMarket').classList.toggle('sel', mode === 'market');
    $('modePair').classList.toggle('sel', mode === 'pair');
    $('pairBar').hidden = mode !== 'pair';
    $('side').classList.toggle('dim', mode === 'pair');
    candleSeries.applyOptions({ visible: mode === 'market' });
    volumeSeries.applyOptions({ visible: mode === 'market' });
    pairSeries.applyOptions({ visible: mode === 'pair' });
    $('mIcon').dataset.item = '';
    if (mode === 'pair') {
      if (!byId(state.have) || !byId(state.want) || state.have === state.want) {
        state.have = state.markets[0] ? state.markets[0].id : null;
        state.want = state.markets[1] ? state.markets[1].id : null;
      }
      renderPairOptions(true);
      loadPair(true);
    } else {
      saveHash();
      loadHistory(true);
    }
    renderHead();
  }
  /** Remembers the pair as #pair=<have id>,<want id>, so a reload or shared link opens the same pair. */
  function saveHash() {
    try {
      history.replaceState(null, '', state.mode === 'pair' ? `#pair=${state.have},${state.want}` : location.pathname + location.search);
    } catch (e) { /* the hash is a convenience */ }
  }
  function renderPairOptions(force) {
    const key = state.markets.map(m => m.id + displayName(m)).join('|');
    if (!force && key === state.pairOptions) return; // don't rebuild an open dropdown every poll
    state.pairOptions = key;
    for (const [sel, cur] of [[$('pairHave'), state.have], [$('pairWant'), state.want]]) {
      sel.replaceChildren(...state.markets.map(m => {
        const o = el('option', '', displayName(m) + (m.category === 'shares' ? ' (share)' : ''));
        o.value = m.id;
        return o;
      }));
      sel.value = cur;
    }
  }
  function setPairIcon(box, m) {
    const key = m ? m.itemId : '';
    if (box.dataset.item === key) return; // keep the image while the item stays the same
    box.dataset.item = key;
    box.replaceChildren(...(m ? [icon(m.itemId, displayName(m))] : []));
  }
  function renderPair() {
    const h = byId(state.have), w = byId(state.want);
    setPairIcon($('pairHaveIcon'), h);
    setPairIcon($('pairWantIcon'), w);
    setPairIcon($('mIcon'), w);
    const ok = h && w && h.price > 0 && w.price > 0;
    const rate = ok ? w.price / h.price : 0; // same as the in-game pair selector: want / have, no fee or spread
    $('mName').textContent = h && w ? `${displayName(w)} / ${displayName(h)}` : 'Pick two markets';
    $('mPrice').textContent = ok ? fmtRate(rate) : '';
    $('mState').textContent = '';
    $('mId').textContent = '';
    $('pairRates').textContent = ok
      ? `1 ${displayName(w)} = ${fmtRate(rate)} ${displayName(h)}  \u00b7  1 ${displayName(h)} = ${fmtRate(1 / rate)} ${displayName(w)}`
      : 'Rate unknown';
    document.title = ok ? `${displayName(w)}/${displayName(h)} ${fmtRate(rate)} - StockMarket` : 'StockMarket';
  }
  /** Called by the markets poll: reload both histories on a new candle or every 30 s, else move the newest point. */
  function refreshPair() {
    const h = byId(state.have), w = byId(state.want);
    if (!h || !w) return;
    if (h.candleStart + ',' + w.candleStart !== state.pairStarts || Date.now() - state.pairAt > 30000) {
      loadPair(false);
    } else if (state.pairLast && h.price > 0 && w.price > 0) {
      state.pairLast.value = w.price / h.price;
      pairSeries.update(state.pairLast);
    }
  }
  async function loadPair(fit) {
    const have = state.have, want = state.want, tf = state.tf;
    if (have === null || want === null || !tf) return;
    const h = byId(have), w = byId(want);
    state.pairAt = Date.now();
    state.pairStarts = h && w ? h.candleStart + ',' + w.candleStart : ''; // set before the fetch, like loadHistory
    saveHash();
    try {
      const [hd, wd] = await Promise.all([
        api(`api/history?market=${have}&tf=${tf}&max=500`), api(`api/history?market=${want}&tf=${tf}&max=500`)]);
      if (have !== state.have || want !== state.want || tf !== state.tf || state.mode !== 'pair') return;
      const points = ratioSeries(hd.candles, wd.candles, tf);
      pairSeries.setData(points);
      state.pairLast = points.length ? { ...points[points.length - 1] } : null;
      if (fit) chart.timeScale().fitContent();
    } catch (e) { /* next poll retries */ }
  }
  function pairChanged() {
    state.have = Number($('pairHave').value);
    state.want = Number($('pairWant').value);
    state.pairLast = null;
    pairSeries.setData([]);
    renderHead();
    loadPair(true);
  }
  $('pairHave').onchange = pairChanged;
  $('pairWant').onchange = pairChanged;
  $('pairSwap').onclick = () => {
    [state.have, state.want] = [state.want, state.have];
    renderPairOptions(true);
    pairChanged();
  };
  $('modeMarket').onclick = () => setMode('market');
  $('modePair').onclick = () => setMode('pair');

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
  try { // #pair=<have id>,<want id> opens the pair view
    const m = /^#pair=(-?\d+),(-?\d+)$/.exec(location.hash);
    if (m) { state.have = Number(m[1]); state.want = Number(m[2]); state.startPair = true; }
  } catch (e) { /* ignore a broken hash */ }
  pollMarkets().then(() => { if (state.startPair) { state.startPair = false; setMode('pair'); } });
  setInterval(tickClock, 1000);
  setInterval(pollMarkets, 2000);
  setInterval(pollBook, 2000);
  setInterval(pollTrades, 5000);
})();
