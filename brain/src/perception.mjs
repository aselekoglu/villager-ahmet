const PHASES = new Set(['WAKE', 'WORK', 'WANDER', 'SLEEP', 'UNKNOWN']);
const MATERIAL = /^[A-Z][A-Z0-9_]{0,63}$/;
const MAX_AGE_MS = 20_000;
const MAX_FUTURE_MS = 1_000;
const MAX_JSON_LENGTH = 16_384;

const isSafeInt = value => Number.isSafeInteger(value);
const emptySections = () => ({
  blocks: { sampledCells: 0, types: [] },
  inventory: { enabled: false, available: false, items: [] },
  players: { enabled: false, available: false, bands: [] },
});

function unavailable(now, expectedGeneration) {
  const capturedAt = isSafeInt(now) && now >= 0 ? now : 0;
  const generation = isSafeInt(expectedGeneration) && expectedGeneration >= 0 ? expectedGeneration : 0;
  return {
    version: 1, available: false, capturedAt, generation, phase: 'UNKNOWN', ...emptySections(),
  };
}

function cleanCounts(entries, maxEntries, maxEach, maxTotal) {
  if (!Array.isArray(entries)) return null;
  const totals = new Map();
  let sum = 0;
  for (const item of entries.slice(0, maxEntries)) {
    if (!item || typeof item !== 'object' || typeof item.material !== 'string' || !MATERIAL.test(item.material) ||
        !isSafeInt(item.count) || item.count < 0 || item.count > maxEach) return null;
    if (item.count === 0) continue;
    sum += item.count;
    if (!Number.isSafeInteger(sum) || sum > maxTotal) return null;
    totals.set(item.material, (totals.get(item.material) ?? 0) + item.count);
  }
  return [...totals.entries()]
    .sort(([nameA, countA], [nameB, countB]) => countB - countA || nameA.localeCompare(nameB))
    .slice(0, 8)
    .map(([material, count]) => ({ material, count }));
}

/** Validate and copy an advisory snapshot; invalid, stale, or mismatched input fails closed. */
export function sanitizePerception(raw, { now = Date.now(), expectedGeneration } = {}) {
  const fail = () => unavailable(now, expectedGeneration);
  if (!raw || typeof raw !== 'object' || Array.isArray(raw) ||
      !isSafeInt(now) || now < 0 || !isSafeInt(expectedGeneration) || expectedGeneration < 0) return fail();
  if (raw.version !== 1 || raw.available !== true || !isSafeInt(raw.capturedAt) || raw.capturedAt < 0 ||
      raw.capturedAt < now - MAX_AGE_MS || raw.capturedAt > now + MAX_FUTURE_MS ||
      raw.generation !== expectedGeneration || !PHASES.has(raw.phase)) return fail();

  const blocks = raw.blocks;
  const inventory = raw.inventory;
  const players = raw.players;
  if (!blocks || typeof blocks !== 'object' || !isSafeInt(blocks.sampledCells) ||
      blocks.sampledCells < 0 || blocks.sampledCells > 64 ||
      !inventory || typeof inventory !== 'object' || typeof inventory.enabled !== 'boolean' ||
      typeof inventory.available !== 'boolean' ||
      !players || typeof players !== 'object' || typeof players.enabled !== 'boolean' ||
      typeof players.available !== 'boolean') return fail();

  const blockTypes = cleanCounts(blocks.types, 8, 64, blocks.sampledCells);
  const inventoryItems = cleanCounts(inventory.items, 8, 3456, 3456);
  if (blockTypes === null || inventoryItems === null || !Array.isArray(players.bands)) return fail();
  const bands = players.bands.slice(0, 8).filter(band => band === 'near' || band === 'nearby');
  if (players.bands.slice(0, 8).some(band => band !== 'near' && band !== 'nearby')) return fail();
  if ((!inventory.enabled && inventory.available) || (!players.enabled && players.available)) return fail();
  if ((!inventory.enabled || !inventory.available) && inventoryItems.length !== 0) return fail();
  if ((!players.enabled || !players.available) && bands.length !== 0) return fail();
  if (blockTypes.reduce((total, item) => total + item.count, 0) > blocks.sampledCells) return fail();

  const clean = {
    version: 1,
    available: true,
    capturedAt: raw.capturedAt,
    generation: expectedGeneration,
    phase: raw.phase,
    blocks: { sampledCells: blocks.sampledCells, types: blockTypes },
    inventory: { enabled: inventory.enabled, available: inventory.available, items: inventoryItems },
    players: { enabled: players.enabled, available: players.available, bands },
  };
  if (JSON.stringify(clean).length > MAX_JSON_LENGTH) return fail();
  return clean;
}
