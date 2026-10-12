// Deliberately deterministic, bounded MVP intent parser. Freeform language is for conversation, not privileges.
export function parseTask(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  const wood = /(odun|kütük|kutuk|oak|log|wood|meşe|mese)/i.test(s);
  const action = /(topla|getir|kes|biriktir|gather|collect|bring|chop|harvest)/i.test(s);
  if (!wood || !action) return null;
  const quantity = s.match(/\b(\d{1,3})\b/);
  const amount = quantity ? Number(quantity[1]) : 8;
  if (amount < 1 || amount > 64) return {error:'Bir seferde 1–64 meşe odunu isteyebilirsin.'};
  if (/(birch|spruce|jungle|dark oak|acacia|mangrove|cherry|huş|ladin)/i.test(s)) return {error:'İlk sürümde yalnızca normal meşe (oak log) toplayabiliyorum.'};
  return {type:'collect_oak_logs',amount};
}
export function isCancel(text) { return /(iptal|vazgeç|vazgec|cancel|stop)/i.test(String(text??'')); }
