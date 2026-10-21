function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderDashboardHtml({ health = {} } = {}) {
  const safeDecision = escapeHtml(health.decisionMode || 'jev');
  const safeChat = escapeHtml(health.chatMode || 'llm');
  const safeJev = escapeHtml(health.configuredJevModel || 'jev-preview');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Villager Ahmet — Observability Dashboard</title>
  <style>
    :root {
      --bg: #0f172a;
      --card-bg: #1e293b;
      --card-border: #334155;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --accent: #38bdf8;
      --green: #22c55e;
      --yellow: #eab308;
      --red: #ef4444;
      --font-sans: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: var(--font-sans);
      line-height: 1.5;
      padding: 1.5rem;
    }
    header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 2rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid var(--card-border);
    }
    .title-group { display: flex; align-items: center; gap: 0.75rem; }
    h1 { font-size: 1.5rem; font-weight: 700; }
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 0.2rem 0.6rem;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .badge-online { background: rgba(34, 197, 94, 0.2); color: var(--green); border: 1px solid var(--green); }
    .badge-warn { background: rgba(234, 179, 8, 0.2); color: var(--yellow); border: 1px solid var(--yellow); }
    .controls { display: flex; align-items: center; gap: 1rem; }
    button {
      background: var(--card-bg);
      color: var(--text);
      border: 1px solid var(--card-border);
      padding: 0.4rem 0.8rem;
      border-radius: 0.375rem;
      cursor: pointer;
      font-size: 0.875rem;
    }
    button:hover { background: #334155; }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2rem;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 0.5rem;
      padding: 1.25rem;
    }
    .card h2 {
      font-size: 1rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 1rem;
      border-bottom: 1px solid var(--card-border);
      padding-bottom: 0.5rem;
    }
    .stat-row {
      display: flex;
      justify-content: space-between;
      padding: 0.4rem 0;
      font-size: 0.9rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    }
    .stat-label { color: var(--text-muted); }
    .stat-val { font-family: var(--font-mono); font-weight: 500; }
    .note {
      font-size: 0.8rem;
      color: var(--text-muted);
      background: rgba(0, 0, 0, 0.2);
      border-left: 3px solid var(--yellow);
      padding: 0.5rem 0.75rem;
      margin-top: 0.75rem;
      border-radius: 0.25rem;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.85rem;
      margin-top: 0.5rem;
    }
    th, td {
      text-align: left;
      padding: 0.5rem;
      border-bottom: 1px solid var(--card-border);
    }
    th { color: var(--text-muted); font-weight: 600; }
    td { font-family: var(--font-mono); }
    .full-width { grid-column: 1 / -1; }
  </style>
</head>
<body>
  <header>
    <div class="title-group">
      <h1>Villager Ahmet</h1>
      <span class="badge badge-online">System Online</span>
    </div>
    <div class="controls">
      <span id="refreshStatus" style="font-size: 0.85rem; color: var(--text-muted);">Auto-refresh: On (5s)</span>
      <button id="toggleRefreshBtn" type="button">Pause</button>
      <button id="manualRefreshBtn" type="button">Refresh Now</button>
    </div>
  </header>

  <div class="grid">
    <!-- Card 1: System & Engine Health -->
    <div class="card">
      <h2>1. System & Engine Health</h2>
      <div class="stat-row">
        <span class="stat-label">Service</span>
        <span class="stat-val">villager-ahmet-brain</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Decision Mode</span>
        <span class="stat-val" id="valDecision">${safeDecision}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Chat Mode</span>
        <span class="stat-val" id="valChat">${safeChat}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Configured JEV Model</span>
        <span class="stat-val" id="valJev">${safeJev}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Server Time</span>
        <span class="stat-val" id="valServerTime">-</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Uptime</span>
        <span class="stat-val" id="valUptime">-</span>
      </div>
    </div>

    <!-- Card 2: Telemetry & Requests -->
    <div class="card">
      <h2>2. Telemetry & Requests</h2>
      <div class="stat-row">
        <span class="stat-label">Total Requests</span>
        <span class="stat-val" id="valTotalReq">0</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Fallbacks Dispatched</span>
        <span class="stat-val" id="valFallbacks">0</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Fallback Rate</span>
        <span class="stat-val" id="valFallbackRate">0%</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Latency (Last / Min / Avg / Max)</span>
        <span class="stat-val" id="valLatencies">- / - / - / -</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Requests by Path</span>
        <span class="stat-val" id="valByPath">-</span>
      </div>
    </div>

    <!-- Card 3: Provider Accounting & Transparency -->
    <div class="card">
      <h2>3. Provider Accounting & Transparency</h2>
      <div class="stat-row">
        <span class="stat-label">Monetary Cost</span>
        <span class="stat-val"><span class="badge badge-warn">UNKNOWN</span></span>
      </div>
      <div class="note">
        Upstream model providers (TypeSafe JEV / OpenAI) do not return billing or monetary cost in responses. Never estimated or fabricated.
      </div>
      <div class="stat-row" style="margin-top: 0.5rem;">
        <span class="stat-label">Token Accounting</span>
        <span class="stat-val"><span class="badge badge-warn">UNKNOWN</span></span>
      </div>
      <div class="note" style="border-left-color: var(--accent);">
        Token counts are reported only if provided by provider responses; otherwise UNKNOWN.
      </div>
      <h3 style="font-size: 0.85rem; margin-top: 1rem; color: var(--text-muted);">Models Telemetry</h3>
      <table id="modelsTable">
        <thead><tr><th>Model</th><th>Calls</th><th>Fallbacks</th><th>Last Seen</th></tr></thead>
        <tbody><tr><td colspan="4" style="color: var(--text-muted); text-align: center;">No model calls recorded</td></tr></tbody>
      </table>
    </div>

    <!-- Card 4: Task Audit Trail -->
    <div class="card full-width">
      <h2>4. Task Event Audit Trail (Last 50)</h2>
      <table id="eventsTable">
        <thead><tr><th>Timestamp</th><th>Event Type</th><th>Actor</th><th>Count</th><th>Details</th></tr></thead>
        <tbody><tr><td colspan="5" style="color: var(--text-muted); text-align: center;">No events recorded yet</td></tr></tbody>
      </table>
    </div>

    <!-- Card 5: Recent Telemetry Calls -->
    <div class="card full-width">
      <h2>5. Recent API Invocations (Last 20)</h2>
      <table id="callsTable">
        <thead><tr><th>Timestamp</th><th>Path</th><th>Status</th><th>Duration</th><th>Source</th><th>Model</th><th>Fallback</th></tr></thead>
        <tbody><tr><td colspan="7" style="color: var(--text-muted); text-align: center;">No calls recorded yet</td></tr></tbody>
      </table>
    </div>
  </div>

  <script>
    (function() {
      let refreshTimer = null;
      let autoRefresh = true;

      function getAuthToken() {
        const urlParams = new URLSearchParams(window.location.search);
        let token = urlParams.get('token');
        if (!token) {
          try {
            token = sessionStorage.getItem('ahmetBridgeToken');
          } catch (e) {}
        }
        if (!token) {
          token = prompt('Enter BRIDGE_TOKEN to view live dashboard metrics:');
          if (token) {
            try {
              sessionStorage.setItem('ahmetBridgeToken', token);
            } catch (e) {}
          }
        }
        return token;
      }

      async function fetchSummary() {
        const token = getAuthToken();
        if (!token) return;

        try {
          const res = await fetch('/api/dashboard/summary?token=' + encodeURIComponent(token), {
            headers: { 'Accept': 'application/json' }
          });
          if (!res.ok) {
            document.getElementById('refreshStatus').textContent = 'Error: ' + res.status + ' Unauthorized';
            return;
          }
          const data = await res.json();
          updateUI(data);
        } catch (err) {
          document.getElementById('refreshStatus').textContent = 'Connection error';
        }
      }

      function updateUI(data) {
        if (!data) return;
        if (data.serverTime) document.getElementById('valServerTime').textContent = data.serverTime;
        if (data.uptimeSeconds !== undefined) {
          const sec = data.uptimeSeconds;
          const h = Math.floor(sec / 3600);
          const m = Math.floor((sec % 3600) / 60);
          const s = sec % 60;
          document.getElementById('valUptime').textContent = h + 'h ' + m + 'm ' + s + 's (' + sec + 's)';
        }

        if (data.health) {
          if (data.health.decisionMode) document.getElementById('valDecision').textContent = data.health.decisionMode;
          if (data.health.chatMode) document.getElementById('valChat').textContent = data.health.chatMode;
          if (data.health.configuredJevModel) document.getElementById('valJev').textContent = data.health.configuredJevModel;
        }

        if (data.requests) {
          document.getElementById('valTotalReq').textContent = data.requests.total;
          document.getElementById('valFallbacks').textContent = data.requests.fallbacks;
          document.getElementById('valFallbackRate').textContent = (data.requests.fallbackRate * 100).toFixed(2) + '%';
          const paths = Object.entries(data.requests.byPath || {}).map(([p, c]) => p + ': ' + c).join(', ');
          document.getElementById('valByPath').textContent = paths || 'none';
        }

        if (data.latencies) {
          const l = data.latencies;
          const fmt = (v) => v !== null && v !== undefined ? v + 'ms' : '-';
          document.getElementById('valLatencies').textContent = fmt(l.lastMs) + ' / ' + fmt(l.minMs) + ' / ' + fmt(l.avgMs) + ' / ' + fmt(l.maxMs);
        }

        // Models table
        const modelsBody = document.querySelector('#modelsTable tbody');
        if (modelsBody && data.models) {
          const entries = Object.entries(data.models);
          if (entries.length > 0) {
            modelsBody.innerHTML = '';
            for (const [name, stats] of entries) {
              const tr = document.createElement('tr');
              tr.innerHTML = '<td>' + escapeText(name) + '</td><td>' + stats.calls + '</td><td>' + stats.fallbacks + '</td><td>' + escapeText(stats.lastSeen || '-') + '</td>';
              modelsBody.appendChild(tr);
            }
          }
        }

        // Events table
        const eventsBody = document.querySelector('#eventsTable tbody');
        if (eventsBody && data.recentEvents) {
          if (data.recentEvents.length > 0) {
            eventsBody.innerHTML = '';
            for (const ev of data.recentEvents.slice().reverse()) {
              const tr = document.createElement('tr');
              tr.innerHTML = '<td>' + escapeText(ev.timestamp) + '</td><td>' + escapeText(ev.type) + '</td><td>' + escapeText(ev.actor) + '</td><td>' + ev.count + '</td><td>' + escapeText(ev.details || '') + '</td>';
              eventsBody.appendChild(tr);
            }
          }
        }

        // Calls table
        const callsBody = document.querySelector('#callsTable tbody');
        if (callsBody && data.recentCalls) {
          if (data.recentCalls.length > 0) {
            callsBody.innerHTML = '';
            for (const c of data.recentCalls.slice().reverse()) {
              const tr = document.createElement('tr');
              tr.innerHTML = '<td>' + escapeText(c.timestamp) + '</td><td>' + escapeText(c.path) + '</td><td>' + c.status + '</td><td>' + c.durationMs + 'ms</td><td>' + escapeText(c.source) + '</td><td>' + escapeText(c.model || '-') + '</td><td>' + (c.fallback ? 'Yes' : 'No') + '</td>';
              callsBody.appendChild(tr);
            }
          }
        }

        document.getElementById('refreshStatus').textContent = 'Last updated: ' + new Date().toLocaleTimeString();
      }

      function escapeText(text) {
        const div = document.createElement('div');
        div.textContent = text ?? '';
        return div.innerHTML;
      }

      function startAutoRefresh() {
        if (refreshTimer) clearInterval(refreshTimer);
        refreshTimer = setInterval(fetchSummary, 5000);
      }

      document.getElementById('toggleRefreshBtn').addEventListener('click', function() {
        autoRefresh = !autoRefresh;
        if (autoRefresh) {
          this.textContent = 'Pause';
          startAutoRefresh();
          fetchSummary();
        } else {
          this.textContent = 'Resume';
          if (refreshTimer) clearInterval(refreshTimer);
          document.getElementById('refreshStatus').textContent = 'Auto-refresh: Paused';
        }
      });

      document.getElementById('manualRefreshBtn').addEventListener('click', fetchSummary);

      fetchSummary();
      startAutoRefresh();
    })();
  </script>
</body>
</html>`;
}
