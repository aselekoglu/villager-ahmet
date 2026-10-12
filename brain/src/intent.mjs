const MATERIALS = [
  ['bamboo', ['bamboo']],
  ['pale_oak', ['pale oak', 'soluk meşe']],
  ['dark_oak', ['dark oak', 'kara meşe']],
  ['mangrove', ['mangrove', 'mangrov']],
  ['crimson', ['crimson', 'kızıl']],
  ['warped', ['warped', 'çarpık']],
  ['jungle', ['jungle', 'orman ağacı']],
  ['spruce', ['spruce', 'ladin']],
  ['birch', ['birch', 'huş']],
  ['acacia', ['acacia', 'akasya']],
  ['cherry', ['cherry', 'kiraz']],
  ['oak', ['oak', 'meşe']],
];

export function isWoodCommand(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  const wood = /(odun|kütük|kutuk|log|wood|meşe|mese|huş|ladin|spruce|birch|oak|crimson|warped|mangrov|mangrove|acacia|akasya|cherry|kiraz|jungle|pale oak|dark oak|bamboo)/i.test(s);
  const action = /(topla|getir|kes|biriktir|bul|ara|gather|collect|bring|get|chop|harvest|find|search)/i.test(s);
  return wood && action;
}

export function parseTask(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  if (!isWoodCommand(s)) return null;
  const quantity = s.match(/\b(\d{1,3})\b/);
  const amount = quantity ? Number(quantity[1]) : 8;
  if (amount < 1 || amount > 64) return {error:'Bir seferde 1–64 kütük isteyebilirsin.'};
  const matched = MATERIALS.find(([, names]) => names.some(name => s.includes(name)));
  return {type:'collect_wood',material:matched?.[0]??'any',amount};
}

export function isCancel(text) { return /(iptal|vazgeç|vazgec|cancel|stop)/i.test(String(text??'')); }

export function validateWoodTask(task) {
  const materials = new Set(['any', ...MATERIALS.map(([id]) => id)]);
  return task && typeof task === 'object' && task.type === 'collect_wood'
    && materials.has(task.material) && Number.isInteger(task.amount) && task.amount >= 1 && task.amount <= 64
    ? {type:'collect_wood',material:task.material,amount:task.amount}
    : null;
}
