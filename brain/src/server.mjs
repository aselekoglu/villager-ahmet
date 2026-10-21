import http from 'node:http';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { loadEnv } from './load-env.mjs';
import { MemoryStore } from './store.mjs';
import { chat, decide } from './engine.mjs';
import { configuredJevModel } from './models.mjs';
import { metrics, MetricsTracker } from './metrics.mjs';
import { renderDashboardHtml } from './dashboard.mjs';

loadEnv();

function safeTokenCompare(provided, expected) {
  if (!provided || !expected) return false;
  const p = Buffer.from(String(provided));
  const e = Buffer.from(String(expected));
  if (p.length !== e.length) return false;
  return crypto.timingSafeEqual(p, e);
}

let defaultStore = null;
function getDefaultStore() {
  if (!defaultStore) {
    defaultStore = new MemoryStore(resolve(process.env.MEMORY_PATH ?? './data/memory.json'));
  }
  return defaultStore;
}

export function createBrainApp({
  store,
  token = process.env.BRIDGE_TOKEN,
  metricsTracker = metrics,
  host = process.env.BRAIN_HOST ?? '127.0.0.1'
} = {}) {
  const activeMetrics = metricsTracker || metrics;
  const activeToken = token;

  const sendJson = (res, status, value) => {
    res.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store'
    });
    res.end(JSON.stringify(value));
  };

  return async (req, res) => {
    const start = performance.now();
    const reqUrl = new URL(req.url, `http://${req.headers.host || host}`);
    const pathname = reqUrl.pathname;
    const bearerToken = req.headers.authorization?.startsWith('Bearer ')
      ? req.headers.authorization.slice(7).trim()
      : (req.headers.authorization ? req.headers.authorization.trim() : null);
    const queryToken = reqUrl.searchParams.get('token');
    const isAuthed = Boolean(activeToken && (safeTokenCompare(bearerToken, activeToken) || safeTokenCompare(queryToken, activeToken)));

    // Public /health endpoint
    if (req.method === 'GET' && pathname === '/health') {
      const healthData = {
        ok: true,
        service: 'villager-ahmet-brain',
        decisionMode: process.env.AHMET_DECISION_MODE ?? 'jev',
        chatMode: process.env.AHMET_CHAT_MODE ?? 'llm',
        configuredJevModel: configuredJevModel()
      };
      sendJson(res, 200, healthData);
      activeMetrics.recordCall({
        path: pathname,
        status: 200,
        durationMs: performance.now() - start,
        source: 'internal',
        model: null,
        fallback: false
      });
      return;
    }

    // Authenticated access check for all remaining endpoints
    if (!isAuthed) {
      sendJson(res, 401, { error: 'Unauthorized or missing BRIDGE_TOKEN' });
      activeMetrics.recordCall({
        path: pathname,
        status: 401,
        durationMs: performance.now() - start,
        source: 'auth',
        error: 'Unauthorized'
      });
      return;
    }

    // Authenticated GET /dashboard (HTML)
    if (req.method === 'GET' && pathname === '/dashboard') {
      const health = {
        ok: true,
        service: 'villager-ahmet-brain',
        decisionMode: process.env.AHMET_DECISION_MODE ?? 'jev',
        chatMode: process.env.AHMET_CHAT_MODE ?? 'llm',
        configuredJevModel: configuredJevModel()
      };
      const html = renderDashboardHtml({ health });
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-store',
        'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'"
      });
      res.end(html);
      activeMetrics.recordCall({
        path: pathname,
        status: 200,
        durationMs: performance.now() - start,
        source: 'dashboard',
        model: null,
        fallback: false
      });
      return;
    }

    // Authenticated GET /api/dashboard/summary (JSON)
    if (req.method === 'GET' && pathname === '/api/dashboard/summary') {
      const health = {
        ok: true,
        service: 'villager-ahmet-brain',
        decisionMode: process.env.AHMET_DECISION_MODE ?? 'jev',
        chatMode: process.env.AHMET_CHAT_MODE ?? 'llm',
        configuredJevModel: configuredJevModel()
      };
      const summary = activeMetrics.getSummary({ health });
      sendJson(res, 200, summary);
      activeMetrics.recordCall({
        path: pathname,
        status: 200,
        durationMs: performance.now() - start,
        source: 'dashboard',
        model: null,
        fallback: false
      });
      return;
    }

    // POST /api/chat, /api/decide, /api/event
    if (req.method !== 'POST' || !['/api/chat', '/api/decide', '/api/event'].includes(pathname)) {
      sendJson(res, 404, { error: 'Not found' });
      activeMetrics.recordCall({
        path: pathname,
        status: 404,
        durationMs: performance.now() - start,
        source: 'http',
        error: 'Not found'
      });
      return;
    }

    const currentStore = store || getDefaultStore();
    try {
      let raw = '';
      for await (const part of req) {
        raw += part;
        if (raw.length > 16000) throw new Error('Body too large');
      }
      const body = JSON.parse(raw);
      if (pathname === '/api/chat') {
        const result = await chat(body, { store: currentStore });
        sendJson(res, 200, result);
        activeMetrics.recordCall({
          path: pathname,
          status: 200,
          durationMs: performance.now() - start,
          source: result.taskSource || 'chat',
          model: process.env.AHMET_CHAT_MODE ?? 'gpt-6-luna',
          fallback: result.taskSource === 'heuristic'
        });
      } else if (pathname === '/api/decide') {
        const result = await decide(body);
        sendJson(res, 200, result);
        activeMetrics.recordCall({
          path: pathname,
          status: 200,
          durationMs: performance.now() - start,
          source: result.source || 'decision',
          model: configuredJevModel(),
          fallback: result.source === 'fallback'
        });
      } else {
        // /api/event
        if (!['task_started', 'task_cancelled', 'task_delivered'].includes(body.type)) {
          throw new Error('Invalid event type');
        }
        const actor = String(body.actor ?? '').slice(0, 80);
        const count = Number(body.count ?? 0);
        await currentStore.recordEvent({ event: body.type, actor, count });
        activeMetrics.recordEvent({
          type: body.type,
          actor,
          count,
          details: body.details ? String(body.details).slice(0, 100) : ''
        });
        sendJson(res, 200, { ok: true });
        activeMetrics.recordCall({
          path: pathname,
          status: 200,
          durationMs: performance.now() - start,
          source: 'event',
          fallback: false
        });
      }
    } catch (e) {
      sendJson(res, 400, { error: String(e.message).slice(0, 300) });
      activeMetrics.recordCall({
        path: pathname,
        status: 400,
        durationMs: performance.now() - start,
        source: 'error',
        error: e.message
      });
    }
  };
}

export function createBrainServer(options = {}) {
  return http.createServer(createBrainApp(options));
}

export default createBrainServer;

const isMain = Boolean(process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url));
if (isMain) {
  const port = Number(process.env.BRAIN_PORT ?? 3197);
  const host = process.env.BRAIN_HOST ?? '127.0.0.1';
  const token = process.env.BRIDGE_TOKEN;
  const store = getDefaultStore();
  await store.load();
  const server = createBrainServer({ store, token, host });
  server.listen(port, host, () => console.log(`Ahmet brain listening on http://${host}:${port}`));
}
