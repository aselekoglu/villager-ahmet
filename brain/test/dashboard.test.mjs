import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable, Writable } from 'node:stream';
import { MetricsTracker, metrics } from '../src/metrics.mjs';
import { renderDashboardHtml } from '../src/dashboard.mjs';
import { createBrainApp, createBrainServer } from '../src/server.mjs';
import { MemoryStore } from '../src/store.mjs';

function createMockReq({ method = 'GET', url = '/', headers = {}, body = null }) {
  const req = new Readable({
    read() {
      if (body !== null && body !== undefined) {
        this.push(typeof body === 'string' ? body : JSON.stringify(body));
      }
      this.push(null);
    }
  });
  req.method = method;
  req.url = url;
  req.headers = Object.fromEntries(
    Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v])
  );
  return req;
}

function createMockRes() {
  const res = new Writable();
  res.statusCode = 200;
  res.headers = {};
  res._chunks = [];
  res.writeHead = function(statusCode, headers) {
    this.statusCode = statusCode;
    if (headers) {
      for (const [k, v] of Object.entries(headers)) {
        this.headers[k.toLowerCase()] = v;
      }
    }
    return this;
  };
  res.setHeader = function(k, v) {
    this.headers[k.toLowerCase()] = v;
  };
  res.getHeader = function(k) {
    return this.headers[k.toLowerCase()];
  };
  res._write = function(chunk, encoding, callback) {
    this._chunks.push(Buffer.from(chunk));
    callback();
  };
  return res;
}

async function request(app, options) {
  const req = createMockReq(options);
  const res = createMockRes();
  const finished = new Promise(resolve => res.on('finish', resolve));
  await app(req, res);
  await finished;
  const text = Buffer.concat(res._chunks).toString('utf8');
  return {
    status: res.statusCode,
    headers: {
      get(name) { return res.headers[name.toLowerCase()] || null; }
    },
    text: async () => text,
    json: async () => JSON.parse(text)
  };
}

async function withAppContext(fn, { token = 'secret-test-token', store, metricsTracker } = {}) {
  const d = await mkdtemp(join(tmpdir(), 'ahmet-dashboard-test-'));
  const s = store || new MemoryStore(join(d, 'memory.json'));
  await s.load();
  const tracker = metricsTracker || new MetricsTracker();
  const app = createBrainApp({ store: s, token, metricsTracker: tracker, host: '127.0.0.1' });
  try {
    await fn({
      app,
      tracker,
      token,
      store: s,
      req: (opt) => request(app, opt)
    });
  } finally {
    await rm(d, { recursive: true, force: true });
  }
}

test('1. GET /dashboard without token or with invalid token returns 401', async () => {
  await withAppContext(async ({ req }) => {
    const unauthed = await req({ method: 'GET', url: '/dashboard' });
    assert.equal(unauthed.status, 401);
    const unauthedJson = await unauthed.json();
    assert.match(unauthedJson.error, /Unauthorized/);

    const wrongBearer = await req({
      method: 'GET',
      url: '/dashboard',
      headers: { Authorization: 'Bearer wrong-token' }
    });
    assert.equal(wrongBearer.status, 401);

    const wrongQuery = await req({ method: 'GET', url: '/dashboard?token=wrong-token' });
    assert.equal(wrongQuery.status, 401);
  });
});

test('2. GET /dashboard with valid Bearer token returns 200, HTML, and strict CSP header', async () => {
  await withAppContext(async ({ req, token }) => {
    const res = await req({
      method: 'GET',
      url: '/dashboard',
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-type') || '', /text\/html/);

    const csp = res.headers.get('content-security-policy');
    assert.ok(csp, 'CSP header should be present');
    assert.match(csp, /default-src 'none'/);
    assert.match(csp, /style-src 'unsafe-inline'/);
    assert.match(csp, /script-src 'unsafe-inline'/);
    assert.match(csp, /connect-src 'self'/);

    const html = await res.text();
    assert.match(html, /Villager Ahmet/);
    assert.match(html, /System Online/);
    assert.match(html, /1\. System & Engine Health/);
    assert.match(html, /2\. Telemetry & Requests/);
    assert.match(html, /3\. Provider Accounting & Transparency/);
    assert.match(html, /4\. Task Event Audit Trail/);
    assert.match(html, /5\. Recent API Invocations/);
    assert.match(html, /UNKNOWN/);
    // Never leak token in generated HTML
    assert.ok(!html.includes(token), 'HTML must not leak secret token');
  });
});

test('3. GET /dashboard with ?token= query parameter returns 200', async () => {
  await withAppContext(async ({ req, token }) => {
    const res = await req({
      method: 'GET',
      url: `/dashboard?token=${encodeURIComponent(token)}`
    });
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-type') || '', /text\/html/);
    const html = await res.text();
    assert.match(html, /Villager Ahmet/);
  });
});

test('4. GET /api/dashboard/summary without token returns 401', async () => {
  await withAppContext(async ({ req }) => {
    const unauthed = await req({ method: 'GET', url: '/api/dashboard/summary' });
    assert.equal(unauthed.status, 401);
    const json = await unauthed.json();
    assert.match(json.error, /Unauthorized/);
  });
});

test('5. GET /api/dashboard/summary with valid token returns 200 and schema with accounting transparency', async () => {
  await withAppContext(async ({ req, token }) => {
    const res = await req({
      method: 'GET',
      url: '/api/dashboard/summary',
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-type') || '', /application\/json/);
    const summary = await res.json();

    assert.equal(summary.ok, true);
    assert.equal(summary.service, 'villager-ahmet-brain');
    assert.ok(typeof summary.uptimeSeconds === 'number');
    assert.ok(typeof summary.serverTime === 'string');

    // Accounting transparency
    assert.equal(summary.accounting.cost, 'UNKNOWN');
    assert.match(summary.accounting.costNote, /Upstream model providers/);
    assert.equal(summary.accounting.tokens, 'UNKNOWN');
    assert.match(summary.accounting.tokensNote, /Token counts/);

    // Structure checks
    assert.ok(summary.requests && typeof summary.requests.total === 'number');
    assert.ok(summary.latencies && typeof summary.latencies.count === 'number');
    assert.ok(Array.isArray(summary.recentCalls));
    assert.ok(Array.isArray(summary.recentEvents));

    // Never leak token in JSON
    const jsonStr = JSON.stringify(summary);
    assert.ok(!jsonStr.includes(token), 'Summary JSON must not leak secret token');
  });
});

test('6. MetricsTracker circular buffers cap at 100 for events and 100 for calls', () => {
  const tracker = new MetricsTracker();

  // 150 events
  for (let i = 0; i < 150; i++) {
    tracker.recordEvent({ type: 'task_started', actor: `player_${i}`, count: i, details: `job ${i}` });
  }
  assert.equal(tracker.eventAuditLog.length, 100);
  assert.equal(tracker.eventAuditLog[0].actor, 'player_50');
  assert.equal(tracker.eventAuditLog[99].actor, 'player_149');

  // 150 calls
  for (let i = 0; i < 150; i++) {
    tracker.recordCall({ path: `/api/test/${i}`, status: 200, durationMs: i, source: 'test' });
  }
  assert.equal(tracker.recentCalls.length, 100);
  assert.equal(tracker.recentCalls[0].path, '/api/test/50');
  assert.equal(tracker.recentCalls[99].path, '/api/test/149');

  const summary = tracker.getSummary();
  assert.equal(summary.eventAuditCount, 100);
  assert.equal(summary.recentCallsCount, 100);
});

test('7. MetricsTracker computes latency stats, fallbacks, byPath, and modelsUsed', () => {
  const tracker = new MetricsTracker();

  // Initial state
  const initial = tracker.getSummary();
  assert.equal(initial.latencies.minMs, null);
  assert.equal(initial.latencies.maxMs, null);
  assert.equal(initial.latencies.avgMs, null);
  assert.equal(initial.latencies.lastMs, null);
  assert.equal(initial.requests.total, 0);

  // Record 3 calls: 10ms, 30ms, 20ms
  tracker.recordCall({ path: '/api/chat', status: 200, durationMs: 10, source: 'chat', model: 'gpt-6-luna', fallback: false });
  tracker.recordCall({ path: '/api/decide', status: 200, durationMs: 30, source: 'decision', model: 'jev-preview', fallback: true });
  tracker.recordCall({ path: '/api/chat', status: 200, durationMs: 20, source: 'chat', model: 'gpt-6-luna', fallback: false });

  const summary = tracker.getSummary();
  assert.equal(summary.requests.total, 3);
  assert.equal(summary.requests.byPath['/api/chat'], 2);
  assert.equal(summary.requests.byPath['/api/decide'], 1);
  assert.equal(summary.requests.fallbacks, 1);
  assert.equal(summary.requests.fallbackRate, 0.3333);

  assert.equal(summary.latencies.count, 3);
  assert.equal(summary.latencies.minMs, 10);
  assert.equal(summary.latencies.maxMs, 30);
  assert.equal(summary.latencies.avgMs, 20);
  assert.equal(summary.latencies.lastMs, 20);

  assert.equal(summary.models['gpt-6-luna'].calls, 2);
  assert.equal(summary.models['gpt-6-luna'].fallbacks, 0);
  assert.equal(summary.models['jev-preview'].calls, 1);
  assert.equal(summary.models['jev-preview'].fallbacks, 1);
});

test('8. Server endpoints record telemetry in metrics tracker', async () => {
  await withAppContext(async ({ req, token, tracker }) => {
    // 1. GET /health (public)
    const h = await req({ method: 'GET', url: '/health' });
    assert.equal(h.status, 200);

    // 2. POST /api/event (task_started)
    const ev = await req({
      method: 'POST',
      url: '/api/event',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: { type: 'task_started', actor: 'Ata', count: 5, details: '5 logs' }
    });
    assert.equal(ev.status, 200);

    // 3. POST /api/chat
    const ch = await req({
      method: 'POST',
      url: '/api/chat',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: { actorUuid: 'ata-1', actorName: 'Ata', text: 'selam' }
    });
    assert.equal(ch.status, 200);

    // 4. POST /api/decide
    const dec = await req({
      method: 'POST',
      url: '/api/decide',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: { actions: [{ id: 'wait', description: 'Wait' }] }
    });
    assert.equal(dec.status, 200);

    // 5. GET /dashboard
    const dash = await req({
      method: 'GET',
      url: '/dashboard',
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(dash.status, 200);

    // 6. GET /api/dashboard/summary
    const sum = await req({
      method: 'GET',
      url: '/api/dashboard/summary',
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(sum.status, 200);

    // 7. Not found
    const nf = await req({
      method: 'POST',
      url: '/api/unknown',
      headers: { Authorization: `Bearer ${token}` }
    });
    assert.equal(nf.status, 404);

    const metricsSummary = tracker.getSummary();
    assert.equal(metricsSummary.requests.total, 7);
    assert.equal(metricsSummary.requests.byPath['/health'], 1);
    assert.equal(metricsSummary.requests.byPath['/api/event'], 1);
    assert.equal(metricsSummary.requests.byPath['/api/chat'], 1);
    assert.equal(metricsSummary.requests.byPath['/api/decide'], 1);
    assert.equal(metricsSummary.requests.byPath['/dashboard'], 1);
    assert.equal(metricsSummary.requests.byPath['/api/dashboard/summary'], 1);
    assert.equal(metricsSummary.requests.byPath['/api/unknown'], 1);
    assert.equal(metricsSummary.requests.byStatus['200'], 6);
    assert.equal(metricsSummary.requests.byStatus['404'], 1);

    assert.equal(metricsSummary.eventAuditCount, 1);
    assert.equal(metricsSummary.recentEvents[0].actor, 'Ata');
    assert.equal(metricsSummary.recentEvents[0].type, 'task_started');
    assert.equal(metricsSummary.recentEvents[0].count, 5);
  });
});

test('9. Security redaction: token secrets are never logged in path or summary', async () => {
  const secretKey = 'super-secret-token-do-not-leak';
  await withAppContext(async ({ req, tracker }) => {
    await req({ method: 'GET', url: `/dashboard?token=${secretKey}` });
    await req({ method: 'GET', url: `/api/dashboard/summary?token=${secretKey}` });

    const summary = tracker.getSummary();
    const serialized = JSON.stringify(summary);

    assert.ok(!serialized.includes(secretKey), 'Serialized summary must never contain token secret from query param');
    assert.ok(summary.requests.byPath['/dashboard'] !== undefined, 'Path should be /dashboard without query string');
    assert.ok(summary.requests.byPath[`/dashboard?token=${secretKey}`] === undefined);

    for (const call of summary.recentCalls) {
      assert.ok(!call.path.includes(secretKey), `Call path "${call.path}" must not leak secret`);
    }
  }, { token: secretKey });
});

test('10. renderDashboardHtml produces offline-safe HTML with escaped values and no remote links', () => {
  const html = renderDashboardHtml({
    health: {
      decisionMode: '<script>alert("xss-decide")</script>',
      chatMode: '<b>llm</b>',
      configuredJevModel: 'jev-preview" onload="bad()'
    }
  });

  // Verify XSS prevention via HTML escaping
  assert.ok(!html.includes('<script>alert('), 'Must escape script tags in health fields');
  assert.ok(html.includes('&lt;script&gt;alert(&quot;xss-decide&quot;)&lt;/script&gt;'));
  assert.ok(html.includes('&lt;b&gt;llm&lt;/b&gt;'));

  // Verify no remote stylesheets, remote scripts, or external font CDN
  assert.ok(!html.includes('href="http'), 'Must not link remote stylesheets');
  assert.ok(!html.includes('src="http'), 'Must not link remote scripts');
  assert.ok(!html.includes('fonts.googleapis.com'), 'Must not load Google fonts');

  // Verify badges and controls
  assert.match(html, /badge-online/);
  assert.match(html, /toggleRefreshBtn/);
  assert.match(html, /manualRefreshBtn/);
  assert.match(html, /modelsTable/);
  assert.match(html, /eventsTable/);
  assert.match(html, /callsTable/);
});

test('11. MetricsTracker reset cleans all state', () => {
  const tracker = new MetricsTracker();
  tracker.recordCall({ path: '/api/chat', status: 200, durationMs: 15, source: 'chat' });
  tracker.recordEvent({ type: 'task_started', actor: 'Ata', count: 1 });
  assert.equal(tracker.requests.total, 1);
  assert.equal(tracker.eventAuditLog.length, 1);

  tracker.reset();
  const summary = tracker.getSummary();
  assert.equal(summary.requests.total, 0);
  assert.equal(summary.eventAuditCount, 0);
  assert.equal(summary.recentCallsCount, 0);
  assert.equal(summary.latencies.count, 0);
  assert.equal(summary.latencies.minMs, null);
});

test('12. createBrainServer creates an http.Server instance', () => {
  const server = createBrainServer();
  assert.ok(server);
  assert.ok(typeof server.listen === 'function');
});
