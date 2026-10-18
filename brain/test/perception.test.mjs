import test from 'node:test';
import assert from 'node:assert/strict';
import { sanitizePerception } from '../src/perception.mjs';

const snapshot = overrides => ({
  version: 1, available: true, capturedAt: 20_000, generation: 3, phase: 'WANDER',
  blocks: { sampledCells: 3, types: [{ material: 'STONE', count: 2 }, { material: 'DIRT', count: 1 }] },
  inventory: { enabled: false, available: false, items: [] },
  players: { enabled: false, available: false, bands: [] },
  ...overrides,
});

test('sanitizer keeps only bounded schema data and drops identifiers, coordinates, and unknown fields', () => {
  const raw = snapshot({
    x: 12, world: 'private', playerUuid: 'secret', command: 'delete',
    players: { enabled: true, available: true, bands: ['near', 'nearby'], names: ['Alice'] },
    blocks: { sampledCells: 3, types: [{ material: 'STONE', count: 2 }, { material: 'DIRT', count: 1 }] },
  });
  const clean = sanitizePerception(raw, { now: 20_000, expectedGeneration: 3 });
  assert.deepEqual(Object.keys(clean), ['version', 'available', 'capturedAt', 'generation', 'phase', 'blocks', 'inventory', 'players']);
  assert.deepEqual(clean.blocks.types, [{ material: 'STONE', count: 2 }, { material: 'DIRT', count: 1 }]);
  assert.deepEqual(clean.players.bands, ['near', 'nearby']);
  assert.equal(JSON.stringify(clean).includes('secret'), false);
  assert.equal(JSON.stringify(clean).includes('Alice'), false);
  assert.equal(JSON.stringify(clean).includes('delete'), false);
});

test('stale, future, and generation-mismatched snapshots fail closed', () => {
  for (const [raw, expected, now] of [
    [snapshot({ capturedAt: 0 }), 3, 20_001],
    [snapshot({ capturedAt: 21_001 }), 3, 20_000],
    [snapshot({ generation: 4 }), 3, 20_000],
  ]) {
    const clean = sanitizePerception(raw, { now, expectedGeneration: expected });
    assert.equal(clean.available, false);
    assert.deepEqual(clean.blocks.types, []);
    assert.deepEqual(clean.inventory.items, []);
    assert.deepEqual(clean.players.bands, []);
  }
});

test('malformed required fields return minimal unavailable data', () => {
  for (const raw of [null, {}, snapshot({ phase: 'TELEPORT' }), snapshot({ generation: -1 }),
    snapshot({ blocks: { sampledCells: 65, types: [] } }),
    snapshot({ blocks: { sampledCells: 1, types: [{ material: 'not_an_enum', count: 1 }] } }),
    snapshot({ players: { enabled: true, available: true, bands: ['far'] } }),
    snapshot({ inventory: { enabled: 'yes', available: true, items: [] } })]) {
    assert.equal(sanitizePerception(raw, { now: 20_000, expectedGeneration: 3 }).available, false);
  }
});

test('non-string materials, including coercible and throwing values, fail closed', () => {
  const throwingMaterial = { toString() { throw new Error('must not coerce material'); } };
  for (const material of [['STONE'], { value: 'STONE' }, throwingMaterial]) {
    const raw = snapshot({ blocks: { sampledCells: 1, types: [{ material, count: 1 }] } });
    let clean;
    assert.doesNotThrow(() => { clean = sanitizePerception(raw, { now: 20_000, expectedGeneration: 3 }); });
    assert.equal(clean.available, false);
    assert.deepEqual(clean.blocks.types, []);
    assert.equal(Array.isArray(clean.blocks.types[0]?.material), false);
  }
});

test('disabled inventory or player sections cannot claim to be available', () => {
  for (const overrides of [
    { inventory: { enabled: false, available: true, items: [] } },
    { players: { enabled: false, available: true, bands: [] } },
  ]) {
    const clean = sanitizePerception(snapshot(overrides), { now: 20_000, expectedGeneration: 3 });
    assert.equal(clean.available, false);
    assert.equal(clean.inventory.available, false);
    assert.equal(clean.players.available, false);
  }
});

test('snapshot age boundaries, count caps, enums, and JSON size are bounded', () => {
  const types = Array.from({ length: 8 }, (_, i) => ({ material: 'STONE', count: i + 1 }));
  const bands = ['near', 'nearby', 'near', 'nearby', 'near', 'nearby', 'near', 'nearby', 'near'];
  const clean = sanitizePerception(snapshot({
    capturedAt: 19_999,
    blocks: { sampledCells: 64, types },
    inventory: { enabled: true, available: true, items: types },
    players: { enabled: true, available: true, bands },
  }), { now: 20_000, expectedGeneration: 3 });
  assert.equal(clean.available, true);
  assert.equal(clean.blocks.types.length, 1);
  assert.equal(clean.blocks.types[0].count, 36);
  assert.equal(clean.inventory.items[0].count, 36);
  assert.equal(clean.players.bands.length, 8);
  assert.ok(JSON.stringify(clean).length < 16_384);
});
