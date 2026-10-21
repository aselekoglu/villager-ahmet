export class MetricsTracker {
  constructor() {
    this.reset();
  }

  reset() {
    this.startedAt = Date.now();
    this.requests = { total: 0, byPath: {}, byStatus: {}, fallbacks: 0 };
    this.latencies = { count: 0, totalMs: 0, minMs: null, maxMs: null, lastMs: null };
    this.modelsUsed = {};
    this.eventAuditLog = [];
    this.recentCalls = [];
  }

  recordCall({ path, status, durationMs = 0, source, model, fallback, error } = {}) {
    const dur = Math.max(0, Number(durationMs) || 0);
    const p = String(path || 'unknown').slice(0, 50);
    const s = Number(status) || 0;
    const isFallback = Boolean(fallback);

    this.requests.total++;
    this.requests.byPath[p] = (this.requests.byPath[p] || 0) + 1;
    this.requests.byStatus[s] = (this.requests.byStatus[s] || 0) + 1;
    if (isFallback) {
      this.requests.fallbacks++;
    }

    this.latencies.count++;
    this.latencies.totalMs += dur;
    this.latencies.lastMs = Math.round(dur * 10) / 10;
    this.latencies.minMs = this.latencies.minMs === null ? dur : Math.min(this.latencies.minMs, dur);
    this.latencies.maxMs = this.latencies.maxMs === null ? dur : Math.max(this.latencies.maxMs, dur);

    if (model) {
      const m = String(model).slice(0, 50);
      this.modelsUsed[m] = this.modelsUsed[m] || { calls: 0, fallbacks: 0, lastSeen: null };
      this.modelsUsed[m].calls++;
      if (isFallback) {
        this.modelsUsed[m].fallbacks++;
      }
      this.modelsUsed[m].lastSeen = new Date().toISOString();
    }

    while (this.recentCalls.length >= 100) {
      this.recentCalls.shift();
    }

    this.recentCalls.push({
      timestamp: new Date().toISOString(),
      path: p,
      status: s,
      durationMs: Math.round(dur * 10) / 10,
      source: String(source || 'unknown').slice(0, 30),
      model: model ? String(model).slice(0, 50) : null,
      fallback: isFallback,
      ...(error ? { error: String(error).slice(0, 100) } : {})
    });
  }

  recordEvent({ type, actor, count, details } = {}) {
    while (this.eventAuditLog.length >= 100) {
      this.eventAuditLog.shift();
    }

    this.eventAuditLog.push({
      timestamp: new Date().toISOString(),
      type: String(type || '').slice(0, 40),
      actor: String(actor || 'unknown').slice(0, 40),
      count: Number.isInteger(count) ? count : 0,
      details: details ? String(details).slice(0, 100) : ''
    });
  }

  getSummary({ health } = {}) {
    return {
      ok: true,
      service: 'villager-ahmet-brain',
      uptimeSeconds: Math.floor((Date.now() - this.startedAt) / 1000),
      serverTime: new Date().toISOString(),
      health: health || {},
      requests: {
        total: this.requests.total,
        byPath: { ...this.requests.byPath },
        byStatus: { ...this.requests.byStatus },
        fallbacks: this.requests.fallbacks,
        fallbackRate: this.requests.total > 0 ? Number((this.requests.fallbacks / this.requests.total).toFixed(4)) : 0
      },
      latencies: {
        count: this.latencies.count,
        minMs: this.latencies.minMs !== null ? Math.round(this.latencies.minMs * 10) / 10 : null,
        maxMs: this.latencies.maxMs !== null ? Math.round(this.latencies.maxMs * 10) / 10 : null,
        avgMs: this.latencies.count > 0 ? Math.round((this.latencies.totalMs / this.latencies.count) * 10) / 10 : null,
        lastMs: this.latencies.lastMs
      },
      models: { ...this.modelsUsed },
      accounting: {
        cost: "UNKNOWN",
        costNote: "Upstream model providers (TypeSafe JEV / OpenAI) do not return billing or monetary cost in responses. Never estimated or fabricated.",
        tokens: "UNKNOWN",
        tokensNote: "Token counts are reported only if provided by provider responses; otherwise UNKNOWN."
      },
      recentCallsCount: this.recentCalls.length,
      recentCalls: this.recentCalls.slice(-20),
      eventAuditCount: this.eventAuditLog.length,
      recentEvents: this.eventAuditLog.slice(-50)
    };
  }
}

export const metrics = new MetricsTracker();
