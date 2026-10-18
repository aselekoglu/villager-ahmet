import {mkdir, readFile, rename, writeFile} from 'node:fs/promises';
import {dirname} from 'node:path';

const SESSION_IDLE_MS = 30 * 60_000;
const MAX_ACTORS = 200;
const MAX_EVENTS = 500;
const MAX_HISTORY = 20;
const MAX_SUMMARY = 1000;
const VERIFIED_EVENT_TYPES = new Set(['task_started', 'task_cancelled', 'task_delivered']);
const objectMap = () => Object.create(null);
const boundedText = (value, limit) => String(value ?? '').slice(0, limit);
const validActor = value => typeof value === 'string' && value.length > 0 && value.length <= 80;
const safeTask = value => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const type = boundedText(value.type, 30);
  const amount = Number(value.amount);
  if (!Number.isInteger(amount) || amount < 1 || amount > 64) return null;
  if (type === 'collect_wood' && typeof value.material === 'string')
    return {type, material: boundedText(value.material, 20), amount};
  if (type === 'collect_item' && typeof value.itemId === 'string' && /^minecraft:[a-z0-9_]{1,80}$/.test(value.itemId))
    return {type, itemId: value.itemId, amount};
  return null;
};
const validHistoryRow = row => row && typeof row === 'object' && !Array.isArray(row)
  && typeof row.text === 'string' && typeof row.reply === 'string'
  ? {at: typeof row.at === 'string' ? row.at.slice(0, 40) : '', text: row.text.slice(0, 500), reply: row.reply.slice(0, 500), task: safeTask(row.task)}
  : null;
const normalizedEventTime = value => {
  if (typeof value !== 'string' || !value) return '';
  const millis = Date.parse(value);
  return Number.isFinite(millis) ? new Date(millis).toISOString() : '';
};
const validAuditEvent = (event, {allowLegacyPlayerChat = false} = {}) => {
  if (!event || typeof event !== 'object' || Array.isArray(event)) return null;
  const hasActor = Object.prototype.hasOwnProperty.call(event, 'actor');
  const actor = validActor(event.actor) ? event.actor : (!hasActor && allowLegacyPlayerChat && event.event === 'chat' ? event.player : null);
  if (!validActor(actor) || typeof event.event !== 'string' || !event.event) return null;
  const row = {at: normalizedEventTime(event.at), actor, event: event.event.slice(0, 80)};
  if (Number.isInteger(event.count) && event.count >= 0) row.count = event.count;
  if (event.task !== undefined) row.task = safeTask(event.task);
  return row;
};
const validTimestamp = value => Number.isSafeInteger(value) && Math.abs(value) <= 8.64e15 && Number.isFinite(new Date(value).getTime());
const compact = (existing, line) => {
  const joined = existing ? `${existing}\n${line}` : line;
  return joined.length > MAX_SUMMARY ? joined.slice(-MAX_SUMMARY) : joined;
};

export class MemoryStore {
  constructor(path, {clock = () => Date.now()} = {}) {
    this.path = path;
    this.clock = clock;
    this.data = {version: 2, conversations: objectMap(), events: [], auditEvents: [], profiles: objectMap(), sessions: objectMap(), previousSessions: objectMap()};
    this.queue = Promise.resolve();
  }
  async load() {
    try {
      const value = JSON.parse(await readFile(this.path, 'utf8'));
      if (!value || typeof value !== 'object' || Array.isArray(value) || ![1, 2].includes(value.version)) return;
      const conversations = objectMap();
      if (value.conversations && typeof value.conversations === 'object' && !Array.isArray(value.conversations)) {
        for (const actor of Object.keys(value.conversations).slice(0, MAX_ACTORS)) {
          if (!validActor(actor) || !Array.isArray(value.conversations[actor])) continue;
          conversations[actor] = value.conversations[actor].map(validHistoryRow).filter(Boolean).slice(-MAX_HISTORY);
        }
      }
      const incomingEvents = [];
      for (const raw of (Array.isArray(value.events) ? value.events : [])) {
        const row = validAuditEvent(raw, {allowLegacyPlayerChat: value.version === 1});
        if (row) incomingEvents.push({row, verified: VERIFIED_EVENT_TYPES.has(row.event)});
      }
      if (value.version === 2 && Array.isArray(value.auditEvents)) {
        for (const raw of value.auditEvents) {
          const row = validAuditEvent(raw);
          if (row) incomingEvents.push({row, verified: false});
        }
      }
      incomingEvents.sort((a, b) => {
        const aTime = a.row.at ? Date.parse(a.row.at) : Number.NEGATIVE_INFINITY;
        const bTime = b.row.at ? Date.parse(b.row.at) : Number.NEGATIVE_INFINITY;
        return aTime - bTime;
      });
      const events = [];
      const auditEvents = [];
      for (const entry of incomingEvents.slice(-MAX_EVENTS)) (entry.verified ? events : auditEvents).push(entry.row);
      const profiles = objectMap();
      const sessions = objectMap();
      const previousSessions = objectMap();
      if (value.version === 2) {
        if (value.profiles && typeof value.profiles === 'object' && !Array.isArray(value.profiles)) {
          for (const actor of Object.keys(value.profiles).slice(0, MAX_ACTORS)) {
            const p = value.profiles[actor];
            if (!validActor(actor) || !p || typeof p !== 'object' || Array.isArray(p)) continue;
            profiles[actor] = {
              displayName: boundedText(p.displayName, 40),
              firstSeen: typeof p.firstSeen === 'string' ? p.firstSeen.slice(0, 40) : '',
              lastSeen: typeof p.lastSeen === 'string' ? p.lastSeen.slice(0, 40) : '',
              interactionCount: Number.isInteger(p.interactionCount) && p.interactionCount >= 0 ? p.interactionCount : 0
            };
          }
        }
        if (value.sessions && typeof value.sessions === 'object' && !Array.isArray(value.sessions)) {
          for (const actor of Object.keys(value.sessions).slice(0, MAX_ACTORS)) {
            const s = value.sessions[actor];
            if (!validActor(actor) || !s || typeof s !== 'object' || Array.isArray(s)) continue;
            if (!validTimestamp(s.startedAt) || !validTimestamp(s.lastSeen) || s.startedAt > s.lastSeen) continue;
            sessions[actor] = {startedAt: s.startedAt, lastSeen: s.lastSeen,
              turnCount: Number.isInteger(s.turnCount) && s.turnCount >= 0 ? s.turnCount : 0, summary: boundedText(s.summary, MAX_SUMMARY)};
          }
        }
        if (value.previousSessions && typeof value.previousSessions === 'object' && !Array.isArray(value.previousSessions)) {
          for (const actor of Object.keys(value.previousSessions).slice(0, MAX_ACTORS)) {
            if (!validActor(actor) || !Array.isArray(value.previousSessions[actor])) continue;
            previousSessions[actor] = value.previousSessions[actor].filter(s => s && typeof s === 'object' && typeof s.summary === 'string')
              .slice(-5).map(s => ({startedAt: typeof s.startedAt === 'string' ? s.startedAt.slice(0, 40) : '', endedAt: typeof s.endedAt === 'string' ? s.endedAt.slice(0, 40) : '', summary: s.summary.slice(-MAX_SUMMARY)}));
          }
        }
      }
      this.data = {version: 2, conversations, events, auditEvents, profiles, sessions, previousSessions};
    } catch (e) { if (e.code !== 'ENOENT') throw e; }
  }
  async record(player, text, reply, task, displayName = '') {
    const key = boundedText(player, 80);
    if (!validActor(key) || (!this.data.profiles[key] && Object.keys(this.data.profiles).length >= MAX_ACTORS)) return;
    const now = this.clock();
    const at = new Date(now).toISOString();
    const row = {at, text: boundedText(text, 500), reply: boundedText(reply, 500), task: safeTask(task)};
    if (!Array.isArray(this.data.conversations[key])) this.data.conversations[key] = [];
    this.data.conversations[key].push(row);
    this.data.conversations[key] = this.data.conversations[key].slice(-MAX_HISTORY);
    const profile = this.data.profiles[key] ??= {displayName: '', firstSeen: at, lastSeen: at, interactionCount: 0};
    if (displayName) profile.displayName = boundedText(displayName, 40);
    if (!profile.firstSeen) profile.firstSeen = at;
    profile.lastSeen = at;
    profile.interactionCount = Math.min(1_000_000, profile.interactionCount + 1);
    this.rolloverSession(key, now);
    const session = this.data.sessions[key];
    session.lastSeen = now;
    session.turnCount++;
    const taskText = row.task ? `; request ${row.task.type} ${row.task.amount}` : '';
    session.summary = compact(session.summary, `Player: ${row.text.slice(0, 160)}${taskText}`);
    this.persistQueued();
    await this.queue;
  }
  async recordEvent(event) {
    if (!event || !VERIFIED_EVENT_TYPES.has(event.event)) return;
    const clean = validAuditEvent({...event, at: new Date(this.clock()).toISOString()});
    if (!clean) return;
    this.data.events.push(clean);
    this.trimEventLogs();
    const session = this.data.sessions[clean.actor];
    if (session) session.summary = compact(session.summary, `Verified server event: ${clean.event} (${clean.count})`);
    this.persistQueued();
    await this.queue;
  }
  trimEventLogs() {
    const entries = [...this.data.events.map(row => ({row, verified: true})), ...this.data.auditEvents.map(row => ({row, verified: false}))]
      .sort((a, b) => a.row.at.localeCompare(b.row.at)).slice(-MAX_EVENTS);
    this.data.events = entries.filter(entry => entry.verified).map(entry => entry.row);
    this.data.auditEvents = entries.filter(entry => !entry.verified).map(entry => entry.row);
  }
  rolloverSession(key, now) {
    const previous = this.data.sessions[key];
    if (previous && now - previous.lastSeen < SESSION_IDLE_MS) return false;
    if (previous) {
      const old = this.data.previousSessions[key] ??= [];
      old.push({startedAt: new Date(previous.startedAt).toISOString(), endedAt: new Date(previous.lastSeen).toISOString(), summary: previous.summary.slice(-MAX_SUMMARY)});
      this.data.previousSessions[key] = old.slice(-5);
    }
    this.data.sessions[key] = {startedAt: now, lastSeen: now, turnCount: 0, summary: ''};
    return Boolean(previous);
  }
  persistQueued() {
    this.queue = this.queue.catch(() => {}).then(() => this.persist());
  }
  async persist() {
    await mkdir(dirname(this.path), {recursive: true});
    const tmp = `${this.path}.${process.pid}.tmp`;
    await writeFile(tmp, JSON.stringify(this.data, null, 2), {mode: 0o600});
    await rename(tmp, this.path);
  }
  history(player) { return this.data.conversations[String(player)]?.slice(-6) ?? []; }
  profile(player) {
    const p = this.data.profiles[String(player)];
    if (!p) return null;
    const relationship = p.interactionCount >= 10 ? 'established' : p.interactionCount >= 2 ? 'familiar' : 'new';
    return {...p, relationship};
  }
  verifiedEvents(player) {
    return this.data.events.filter(e => e.actor === String(player) && VERIFIED_EVENT_TYPES.has(e.event)).slice(-10).map(e => ({at: e.at, event: e.event, count: e.count}));
  }
  context(player) {
    const key = String(player);
    const session = this.data.sessions[key];
    if (session && this.rolloverSession(key, this.clock())) this.persistQueued();
    const currentSession = this.data.sessions[key];
    const profile = this.profile(key);
    const recentSessions = (this.data.previousSessions[key] ?? []).slice(-5).map(s => ({startedAt: s.startedAt, endedAt: s.endedAt, summary: s.summary.slice(-MAX_SUMMARY)}));
    return {
      profile: profile ? {...profile} : {displayName: '', firstSeen: '', lastSeen: '', interactionCount: 0, relationship: 'new'},
      currentSession: currentSession ? {lastSeen: new Date(currentSession.lastSeen).toISOString(), turnCount: currentSession.turnCount, summary: currentSession.summary.slice(-MAX_SUMMARY)} : {lastSeen: '', turnCount: 0, summary: ''},
      recentSessions,
      verifiedEvents: this.verifiedEvents(key)
    };
  }
}
