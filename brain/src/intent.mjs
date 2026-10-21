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
const RESOURCES = [
  ['minecraft:raw_iron', ['ham demir', 'raw iron', 'demir cevheri', 'iron ore']],
  ['minecraft:raw_gold', ['ham altın', 'ham altin', 'raw gold', 'altın cevheri', 'altin cevheri', 'gold ore']],
  ['minecraft:cobblestone', ['cobblestone', 'kırık taş', 'kırik taş', 'taş', 'tas', 'stone']],
  ['minecraft:red_sand', ['kızıl kum', 'kizil kum', 'red sand']],
  ['minecraft:coal', ['kömür', 'komur', 'coal']],
  ['minecraft:sand', ['kum', 'sand']],
  ['minecraft:dirt', ['toprak', 'dirt', 'grass block', 'çimenli blok', 'cimenli blok']],
];
const CRAFT_RECIPES = [
  ['minecraft:oak_planks', ['meşe tahtası', 'mese tahtasi', 'meşe tahta', 'mese tahta', 'oak planks', 'oak plank', 'oak_planks']],
  ['minecraft:stick', ['çubuk', 'cubuk', 'stick', 'sticks', 'minecraft:stick']],
];
export const CROPS = [
  ['minecraft:wheat', ['buğday', 'bugday', 'wheat']],
  ['minecraft:carrot', ['havuç', 'havuc', 'carrot', 'carrots']],
  ['minecraft:potato', ['patates', 'potato', 'potatoes']],
  ['any', ['ekin', 'ekinler', 'mahsul', 'tarla', 'crop', 'crops']],
];
export const UNSUPPORTED_CROPS = [
  ['beetroot', ['pancar', 'beetroot', 'beetroots']],
  ['nether_wart', ['nether wart', 'nether siğili', 'nether sigili']],
  ['melon', ['karpuz', 'melon']],
  ['pumpkin', ['balkabağı', 'balkabagi', 'kabak', 'pumpkin']],
  ['cocoa', ['kakao', 'cocoa']],
  ['sweet_berries', ['tatlı böğürtlen', 'tatli bogurtlen', 'sweet berry', 'sweet berries']],
];

export function isFarmCommand(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  const crop = /(buğday|bugday|wheat|havuç|havuc|carrot|carrots|patates|potato|potatoes|ekin|ekinler|mahsul|tarla|crop|crops)/i.test(s);
  const action = /(topla|getir|hasat|biç|bic|biriktir|gather|collect|bring|harvest|farm)/i.test(s);
  return Boolean(crop && action);
}

export function isCraftCommand(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  const craftAction = /(üret|uret|yap|işle|isle|çevir|cevir|dönüştür|donustur|craft|make)/i.test(s);
  const matchedRecipe = CRAFT_RECIPES.some(([, names]) => names.some(name => s.includes(name)));
  return Boolean(craftAction && matchedRecipe);
}

export function isWoodCommand(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  const wood = /(odun|kütük|kutuk|log|wood|meşe|mese|huş|ladin|spruce|birch|oak|crimson|warped|mangrov|mangrove|acacia|akasya|cherry|kiraz|jungle|pale oak|dark oak|bamboo)/i.test(s);
  const action = /(topla|getir|kes|biriktir|bul|ara|gather|collect|bring|get|chop|harvest|find|search)/i.test(s);
  return wood && action;
}

export function parseTask(text) {
  const s = String(text ?? '').toLocaleLowerCase('tr-TR');
  if (isCraftCommand(s)) {
    const craftRecipe = CRAFT_RECIPES.find(([, names]) => names.some(name => s.includes(name)));
    const quantity = s.match(/\b(\d{1,3})\b/);
    const amount = quantity ? Number(quantity[1]) : 4;
    if (amount < 4 || amount > 64 || amount % 4 !== 0) {
      return {error: "Üretim miktarı 4 ile 64 arasında ve 4'ün katı olmalı (ör. 4, 8, 12, 16)."};
    }
    return {type: 'craft_item', recipe: craftRecipe[0], amount};
  }
  const action = /(topla|getir|kes|biriktir|bul|ara|biç|bic|hasat|gather|collect|bring|get|chop|harvest|find|search|farm)/i.test(s);
  if (!action) return null;
  const unsupportedCrop = UNSUPPORTED_CROPS.find(([, names]) => names.some(name => s.includes(name)));
  if (unsupportedCrop) {
    return {error: 'Şimdilik sadece onaylı tarladaki olgun buğday (wheat), havuç (carrot) ve patates (potato) ekinlerini hasat edebilirim.'};
  }
  const quantity = s.match(/\b(\d{1,3})\b/);
  const amount = quantity ? Number(quantity[1]) : 8;
  if (amount < 1 || amount > 64) return {error:'Bir seferde 1–64 kaynak isteyebilirsin.'};
  if (isFarmCommand(s)) {
    const matched = CROPS.find(([, names]) => names.some(name => s.includes(name)));
    return {type: 'harvest_crop', crop: matched ? matched[0] : 'any', amount};
  }
  if (isWoodCommand(s)) {
    const matched = MATERIALS.find(([, names]) => names.some(name => s.includes(name)));
    return {type:'collect_wood',material:matched?.[0]??'any',amount};
  }
  const resource=RESOURCES.find(([,names])=>names.some(name=>s.includes(name)));
  return resource?{type:'collect_item',itemId:resource[0],amount}:null;
}

export function isCancel(text) { return /(iptal|vazgeç|vazgec|cancel|stop)/i.test(String(text??'')); }

export function isReturnHome(text) { return /(eve dön|eve don|eve git|evine dön|evine git|go home|return home)/i.test(String(text??'')); }

export function validateWoodTask(task) {
  const materials = new Set(['any', ...MATERIALS.map(([id]) => id)]);
  return task && typeof task === 'object' && task.type === 'collect_wood'
    && materials.has(task.material) && Number.isInteger(task.amount) && task.amount >= 1 && task.amount <= 64
    ? {type:'collect_wood',material:task.material,amount:task.amount}
    : null;
}

export function validateCraftTask(task) {
  const recipes = new Set(['minecraft:oak_planks', 'minecraft:stick']);
  return task && typeof task === 'object' && task.type === 'craft_item'
    && recipes.has(task.recipe) && Number.isInteger(task.amount)
    && task.amount >= 4 && task.amount <= 64 && task.amount % 4 === 0
    ? {type: 'craft_item', recipe: task.recipe, amount: task.amount}
    : null;
}

export function validateFarmTask(task) {
  if (!task || typeof task !== 'object' || task.type !== 'harvest_crop') return null;
  if (!Number.isInteger(task.amount) || task.amount < 1 || task.amount > 64) return null;
  const canonical = {
    'any': 'any',
    'crop:ANY': 'any',
    'minecraft:wheat': 'minecraft:wheat',
    'wheat': 'minecraft:wheat',
    'crop:WHEAT': 'minecraft:wheat',
    'minecraft:carrot': 'minecraft:carrot',
    'carrot': 'minecraft:carrot',
    'carrots': 'minecraft:carrot',
    'crop:CARROT': 'minecraft:carrot',
    'minecraft:potato': 'minecraft:potato',
    'potato': 'minecraft:potato',
    'potatoes': 'minecraft:potato',
    'crop:POTATO': 'minecraft:potato',
  };
  const crop = canonical[task.crop];
  if (!crop) return null;
  return {type: 'harvest_crop', crop, amount: task.amount};
}

export function validateTask(task) {
  const wood = validateWoodTask(task);
  if (wood) return wood;
  const craft = validateCraftTask(task);
  if (craft) return craft;
  const farm = validateFarmTask(task);
  if (farm) return farm;
  return task && typeof task === 'object' && task.type === 'collect_item'
    && typeof task.itemId === 'string' && /^minecraft:[a-z0-9_]{1,64}$/.test(task.itemId)
    && Number.isInteger(task.amount) && task.amount >= 1 && task.amount <= 64
    ? {type: 'collect_item', itemId: task.itemId, amount: task.amount} : null;
}
