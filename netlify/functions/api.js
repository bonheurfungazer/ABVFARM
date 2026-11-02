// netlify/functions/api.js
// Endpoint Backend pour ABV Farm
//
// Routes :
//   GET  /api/          -> statut du backend
//   GET  /api/alerts    -> regles d'alertes
//   GET  /api/audit     -> documentation de l'endpoint d'audit (aucune donnee requise)
//   POST /api/audit     -> audit complet des donnees de l'elevage
//     Corps accepte (toutes les donnees de l'application, aucun champ ignore) :
//       { docs: [{ kind: 'rabbit'|'mating'|'batch'|'weight'|'treatment'|'item'|'feed'|'tx'|'reminder'|'cage', ... }] }
//         -> c'est exactement le format du fichier exporte par l'application (Exporter le fichier de sauvegarde)
//       ou { data: { rabbits: [...], matings: [...], ... } }
//       ou { S: { rabbits: [...], ... } }
//     Champs optionnels : { params: { gestation: 31, weaning: 35 }, today: '2026-11-02' }
//     L'API ne stocke rien : elle analyse les donnees recues et renvoie le rapport dans la reponse.
//     Reponse : { success, generatedAt, counts, totals: { errors, warnings }, issues: [...] }
//     Chaque issue : { severity: 'error'|'warning', code, kind, id, label, detail, suggestion }

const KINDMAP = {
  rabbit: 'rabbits', rabbits: 'rabbits',
  mating: 'matings', matings: 'matings',
  weight: 'weights', weights: 'weights',
  treatment: 'treatments', treatments: 'treatments',
  item: 'inventory', inventory: 'inventory',
  feed: 'feed', feeds: 'feed',
  tx: 'tx', transactions: 'tx',
  batch: 'batches', batches: 'batches',
  reminder: 'reminders', reminders: 'reminders',
  cage: 'cages', cages: 'cages'
};

function str(v) { return v === null || v === undefined ? '' : String(v); }
function num(v) { const n = Number(v); return Number.isFinite(n) ? n : 0; }
function isDate(s) { return typeof s === 'string' && /^\d{4}-\d{2}-\d{2}/.test(s); }
function day(s) { return isDate(s) ? s.slice(0, 10) : ''; }
function addDays(dateStr, n) {
  if (!isDate(dateStr)) return '';
  const d = new Date(dateStr.slice(0, 10) + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

function normalizeData(payload) {
  const data = { rabbits: [], matings: [], weights: [], treatments: [], inventory: [], feed: [], tx: [], batches: [], reminders: [], cages: [] };
  const put = (kind, obj) => {
    const key = KINDMAP[kind];
    if (key && obj && typeof obj === 'object') data[key].push(obj);
  };
  if (Array.isArray(payload.docs)) {
    for (const d of payload.docs) put(d.kind, d);
  }
  const src = payload.data || payload.S || payload.export || null;
  if (src && typeof src === 'object') {
    for (const [k, arr] of Object.entries(src)) {
      if (Array.isArray(arr)) for (const d of arr) put(k, d);
    }
  }
  // Un corps qui est directement un export { docs } est deja traite ci-dessus.
  return data;
}

function auditData(payload) {
  const data = normalizeData(payload);
  const params = payload.params || {};
  const gestation = num(params.gestation) || 31;
  const weaning = num(params.weaning) || 35;
  const today = day(payload.today) || new Date().toISOString().slice(0, 10);
  const issues = [];
  const push = (severity, code, kind, id, label, detail, suggestion) => {
    issues.push({ severity, code, kind, id: str(id), label: str(label), detail, suggestion });
  };

  const rabbits = data.rabbits;
  const byId = (arr) => { const m = new Map(); for (const x of arr) if (x && x.id !== undefined) m.set(str(x.id), x); return m; };
  const rabById = byId(rabbits);
  const matById = byId(data.matings);
  const itemById = byId(data.inventory);
  const batchById = byId(data.batches);
  const treatById = byId(data.treatments);
  const cageByName = new Map();
  for (const c of data.cages) if (c && c.name) cageByName.set(str(c.name).trim().toLowerCase(), c);

  const exited = (r) => r && ['Vendu', 'Mort', 'Abattu', 'Donné'].includes(r.status);
  const active = (r) => r && r.status !== 'Vendu' && r.status !== 'Mort' && r.status !== 'Abattu' && r.status !== 'Donné' && r.status !== 'Externe';
  const rname = (id) => { const r = rabById.get(str(id)); return r ? (r.name || str(id)) : str(id); };
  const link = (v) => str(v && (v.rabbitId || v.rabbit || v.id) ? (v.rabbitId || v.rabbit) : '');

  // ---- Lapins ----
  const codes = new Map();
  for (const r of rabbits) {
    const label = r.name || r.id || '(sans nom)';
    if (!r.id) push('error', 'RABBIT_NO_ID', 'rabbit', '', label, 'Lapin sans identifiant : impossible de le lier a ses pesées, soins ou saillies.', 'Supprimer ou corriger cette fiche, puis re-exporter.');
    if (!r.name) push('error', 'RABBIT_NO_NAME', 'rabbit', r.id, label, 'Lapin sans nom/code.', 'Donner un nom ou un code a ce lapin.');
    if (r.name) {
      const k = str(r.name).trim().toLowerCase();
      if (codes.has(k)) push('error', 'RABBIT_DUP_CODE', 'rabbit', r.id, label, `Code/nom en double avec la fiche ${codes.get(k)}.`, 'Renommer l\'une des deux fiches.');
      else codes.set(k, r.id);
    }
    // Correctif : code ABV extrait du nom (ex. ABV-26-049), en double meme si les noms different.
    const _abv = str(r.name).match(/ABV-\d+-\d+/);
    if (_abv) {
      const _k = 'abv:' + _abv[0];
      if (codes.has(_k)) push('error', 'RABBIT_CODE_DUP', 'rabbit', r.id, label, `Code ${_abv[0]} en double avec la fiche ${codes.get(_k)}.`, 'Renommer l\'un des deux lapins avec un code libre.');
      else codes.set(_k, r.id);
    }
    if (day(r.birth) && day(r.birth) > today) push('error', 'RABBIT_BIRTH_FUTURE', 'rabbit', r.id, label, `Date de naissance ${day(r.birth)} dans le futur.`, 'Corriger la date de naissance.');
    if (r.status === 'Vendu' && !(num(r.exitPrice) > 0)) push('error', 'VENDU_NO_PRICE', 'rabbit', r.id, label, 'Animal vendu sans prix de vente : aucune recette ne peut etre creee en finances.', 'Renseigner le prix de vente dans le formulaire de statut.');
    if (exited(r) && !r.exitDate) push('warning', 'RABBIT_EXIT_NO_DATE', 'rabbit', r.id, label, `Animal sorti (${r.status}) sans date de sortie : son age ne peut pas etre fige.`, 'Renseigner la date de sortie.');
    if (r.father && str(r.father) === str(r.id)) push('error', 'RABBIT_PARENT_SELF', 'rabbit', r.id, label, 'Le lapin est indique comme son propre pere.', 'Corriger le pere.');
    if (r.mother && str(r.mother) === str(r.id)) push('error', 'RABBIT_PARENT_SELF', 'rabbit', r.id, label, 'Le lapin est indique comme sa propre mere.', 'Corriger la mere.');
    if (r.father && !rabById.has(str(r.father))) push('error', 'RABBIT_PARENT_ORPHAN', 'rabbit', r.id, label, `Pere ${r.father} introuvable dans les lapins.`, 'Corriger le pere ou recreer sa fiche.');
    if (r.mother && !rabById.has(str(r.mother))) push('error', 'RABBIT_PARENT_ORPHAN', 'rabbit', r.id, label, `Mere ${r.mother} introuvable dans les lapins.`, 'Corriger la mere ou recreer sa fiche.');
    if (r.father) { const f = rabById.get(str(r.father)); if (f && f.sex === 'Female') push('warning', 'RABBIT_PARENT_SEX', 'rabbit', r.id, label, 'Le pere indique est enregistre comme femelle.', 'Verifier le sexe du pere.'); }
    if (r.mother) { const m = rabById.get(str(r.mother)); if (m && m.sex === 'Male') push('warning', 'RABBIT_PARENT_SEX', 'rabbit', r.id, label, 'La mere indiquee est enregistree comme male.', 'Verifier le sexe de la mere.'); }
    if (r.cage && data.cages.length && !cageByName.has(str(r.cage).trim().toLowerCase())) {
      push('warning', 'RABBIT_CAGE_UNDEFINED', 'rabbit', r.id, label, `Cage « ${r.cage} » utilisee mais absente du registre des cages.`, 'Definir cette cage dans le registre, ou corriger la cage du lapin.');
    }
  }

  // ---- Saillies ----
  const openByFemale = new Map();
  for (const m of data.matings) {
    const label = `${rname(m.female)} x ${rname(m.male)} (${day(m.date) || 'sans date'})`;
    if (!m.female || !rabById.has(str(m.female))) push('error', 'MATING_ORPHAN_FEMALE', 'mating', m.id, label, `Femelle ${m.female || '(absente)'} introuvable.`, 'Corriger la femelle ou supprimer la saillie.');
    if (!m.male || !rabById.has(str(m.male))) push('error', 'MATING_ORPHAN_MALE', 'mating', m.id, label, `Male ${m.male || '(absent)'} introuvable.`, 'Corriger le male ou supprimer la saillie.');
    if (m.female && str(m.female) === str(m.male)) push('error', 'MATING_SAME_RABBIT', 'mating', m.id, label, 'La femelle et le male sont le meme lapin.', 'Corriger l\'un des deux reproducteurs.');
    if (['Accouplé', 'Gestation confirmée'].includes(m.status)) {
      if (exited(rabById.get(str(m.female)))) push('error', 'MATING_FEMALE_EXITED_OPEN', 'mating', m.id, label, 'Saillie encore ouverte alors que la femelle est sortie.', 'Cloturer la saillie en « Échoué ».');
      if (exited(rabById.get(str(m.male)))) push('error', 'MATING_MALE_EXITED_OPEN', 'mating', m.id, label, 'Saillie encore ouverte alors que le male est sorti.', 'Cloturer la saillie en « Échoué ».');
      const list = openByFemale.get(str(m.female)) || [];
      list.push(m.id); openByFemale.set(str(m.female), list);
      if (m.due && day(m.due) < today) push('warning', 'MATING_OVERDUE', 'mating', m.id, label, `Mise bas prevue le ${day(m.due)}, deja passee, saillie toujours ouverte.`, 'Enregistrer la mise bas ou cloturer la saillie.');
    }
    if (day(m.date) && day(m.date) > today) push('error', 'MATING_DATE_FUTURE', 'mating', m.id, label, `Date de saillie ${day(m.date)} dans le futur.`, 'Corriger la date.');
    if (day(m.date) && day(m.due) && day(m.due) < day(m.date)) push('error', 'MATING_DUE_BEFORE_DATE', 'mating', m.id, label, `Echeance ${day(m.due)} avant la saillie ${day(m.date)}.`, 'Corriger l\'echeance (environ ' + gestation + ' jours apres la saillie).');
    if (m.status === 'Mise bas' && day(m.birthDate) && day(m.date) && day(m.birthDate) < day(m.date)) push('error', 'MATING_BIRTH_BEFORE_DATE', 'mating', m.id, label, `Mise bas ${day(m.birthDate)} avant la saillie ${day(m.date)}.`, 'Corriger la date de mise bas.');
    if (num(m.alive) < 0 || num(m.dead) < 0) push('error', 'MATING_COUNT_NEGATIVE', 'mating', m.id, label, 'Nombre de lapereaux vivants ou morts-negatif.', 'Corriger les nombres.');
    if (m.status === 'Mise bas' && (!m.birthDate || m.alive === null || m.alive === undefined || m.alive === '')) push('warning', 'MATING_BIRTH_INCOMPLETE', 'mating', m.id, label, 'Mise bas sans date de mise bas ou sans nombre de nes vivants.', 'Renseigner la date et le nombre de nes vivants (la taille est souvent seulement dans les notes).');
    if (m.status === 'Mise bas') {
      const kits = rabbits.filter((r) => str(r.matingId) === str(m.id));
      if (kits.length < num(m.alive)) push('warning', 'KITS_COUNT_MISMATCH', 'mating', m.id, label, `${num(m.alive)} lapereaux vivants declares, mais seulement ${kits.length} fiche(s) individuelle(s) de lapereau(x).`, 'Creer les fiches manquantes depuis la saillie.');
      if (kits.length > num(m.alive)) push('error', 'KITS_COUNT_MISMATCH', 'mating', m.id, label, `${kits.length} fiche(s) de lapereau(x) pour seulement ${num(m.alive)} vivant(s) declare(s).`, 'Verifier les fiches de lapereaux ou le nombre declare.');
    }
    // Consanguinite directe : parent/enfant ou freres et soeurs pleins
    const f = rabById.get(str(m.female)); const mm = rabById.get(str(m.male));
    if (f && mm) {
      if (str(f.father) === str(m.male) || str(f.mother) === str(m.male) || str(mm.father) === str(m.female) || str(mm.mother) === str(m.female)) {
        push('error', 'INBREEDING_DIRECT', 'mating', m.id, label, 'Croisement parent/enfant : consanguinite directe.', 'Ne pas conserver ce couple pour la reproduction.');
      } else if (f.father && f.father === mm.father && f.mother && f.mother === mm.mother) {
        push('warning', 'INBREEDING_SIBLINGS', 'mating', m.id, label, 'Croisement entre frere et soeur (memes pere et mere).', 'Eviter ce couple ou valider avec un plan de selection.');
      }
    }
  }
  for (const [female, ids] of openByFemale) {
    if (ids.length > 1) push('error', 'MATING_DUPLICATE_OPEN_FEMALE', 'mating', ids[0], rname(female), `${ids.length} saillies ouvertes pour la meme femelle.`, 'Ne garder qu\'une seule saillie ouverte par femelle.');
  }

  // ---- Lots ----
  const batchCodes = new Map();
  for (const b of data.batches) {
    const label = b.code || b.id || '(sans code)';
    if (b.code) {
      const k = str(b.code).trim().toLowerCase();
      if (batchCodes.has(k)) push('error', 'BATCH_CODE_DUP', 'batch', b.id, label, `Code de lot en double avec le lot ${batchCodes.get(k)}.`, 'Renommer l\'un des deux lots.');
      else batchCodes.set(k, b.id);
    }
    if (b.mating && !matById.has(str(b.mating))) push('error', 'BATCH_ORPHAN_MATING', 'batch', b.id, label, `Portee ${b.mating} introuvable.`, 'Corriger la portee du lot ou supprimer le lot.');
    if (num(b.current) < 0) push('error', 'BATCH_COUNT_NEGATIVE', 'batch', b.id, label, 'Effectif actuel negatif.', 'Corriger l\'effectif.');
    if (num(b.initial) > 0 && num(b.current) > num(b.initial)) push('error', 'BATCH_CURRENT_GT_INITIAL', 'batch', b.id, label, `Effectif actuel (${num(b.current)}) superieur a l\'effectif initial (${num(b.initial)}).`, 'Corriger l\'effectif initial ou actuel.');
    if (num(b.deaths) < 0) push('error', 'BATCH_COUNT_NEGATIVE', 'batch', b.id, label, 'Nombre de pertes negatif.', 'Corriger les pertes.');
    if (num(b.initial) > 0 && num(b.current) + num(b.deaths) > num(b.initial)) push('error', 'BATCH_COUNT_INCOHERENT', 'batch', b.id, label, `Effectif actuel (${num(b.current)}) + pertes (${num(b.deaths)}) superieur a l\'initial (${num(b.initial)}) : des ventes ou pertes ont ete sur-declarees.`, 'Corriger l\'effectif, les pertes ou l\'initial.');
    if (b.status === 'Vendu' && !data.tx.some((x) => str(x.batchId) === str(b.id) || (x.type === 'in' && (x.label || '').includes(b.code)))) push('warning', 'BATCH_SALE_NO_TX', 'batch', b.id, label, 'Lot vendu sans recette financiere liee.', 'Renseigner la vente avec son prix, ou recreer la recette liee au lot.');
  }

  // ---- Pesees / Soins ----
  for (const w of data.weights) {
    const rid = str(w.rabbitId || w.rabbit || '');
    const label = `${rname(rid)} · ${day(w.date) || 'sans date'}`;
    if (!rid || !rabById.has(rid)) push('error', 'WEIGHT_ORPHAN', 'weight', w.id, label, `Lapin ${rid || '(absent)'} introuvable.`, 'Corriger le lapin ou supprimer la pesee.');
    if (!(num(w.kg) > 0)) push('error', 'WEIGHT_NONPOSITIVE', 'weight', w.id, label, 'Poids nul ou negatif.', 'Corriger le poids.');
    if (day(w.date) && day(w.date) > today) push('error', 'WEIGHT_FUTURE', 'weight', w.id, label, 'Pesee datee dans le futur.', 'Corriger la date.');
    const r = rabById.get(rid);
    if (r && exited(r) && r.exitDate && day(w.date) && day(w.date) > r.exitDate) push('error', 'WEIGHT_AFTER_EXIT', 'weight', w.id, label, `Pesee apres la sortie de l'animal (${r.exitDate}).`, 'Supprimer cette pesee : les donnees d\'un animal sorti sont figees.');
  }
  for (const t of data.treatments) {
    const rid = str(t.rabbitId || t.rabbit || '');
    const label = `${t.product || t.type || 'Soin'} · ${day(t.date) || 'sans date'}`;
    if (rid && !rabById.has(rid)) push('error', 'TREAT_ORPHAN', 'treatment', t.id, label, `Lapin ${rid} introuvable.`, 'Corriger le lapin ou supprimer le soin.');
    const r = rabById.get(rid);
    if (r && exited(r) && r.exitDate && day(t.date) && day(t.date) > r.exitDate) push('error', 'TREAT_AFTER_EXIT', 'treatment', t.id, label, `Soin apres la sortie de l'animal (${r.exitDate}).`, 'Supprimer ce soin : les donnees d\'un animal sorti sont figees.');
    if (r && (r.status === 'Vendu' || r.status === 'Abattu') && r.exitDate && num(t.withdrawal) > 0 && day(t.date)) {
      const end = addDays(day(t.date), num(t.withdrawal));
      if (end >= r.exitDate) push('error', 'TREAT_WITHDRAWAL_SALE', 'treatment', t.id, label, `Vente/abattage le ${r.exitDate} pendant le delai d'attente (jusqu'au ${end}).`, 'Ne pas vendre un animal sous delai d\'attente.');
    }
  }

  // ---- Stocks / Distributions / Finances ----
  for (const it of data.inventory) {
    if (!it.name) push('warning', 'ITEM_NO_NAME', 'item', it.id, '(sans nom)', 'Article sans designation.', 'Nommer l\'article.');
    if (num(it.qty) < 0) push('error', 'ITEM_QTY_NEGATIVE', 'item', it.id, it.name || it.id, 'Quantite negative.', 'Corriger la quantite.');
    if (it.expiry && day(it.expiry) < today && num(it.qty) > 0) push('warning', 'ITEM_EXPIRED', 'item', it.id, it.name || it.id, `Peremption depassee (${day(it.expiry)}) avec du stock restant.`, 'Verifier ou jeter le stock perime.');
  }
  const feedsByDate = new Map();
  for (const f of data.feed) {
    const label = `${day(f.date) || 'sans date'} · ${f.grams ? (num(f.grams) / 1000) + ' kg' : ''}`;
    if (f.item && !itemById.has(str(f.item))) push('error', 'FEED_ORPHAN_ITEM', 'feed', f.id, label, `Article ${f.item} introuvable dans les stocks.`, 'Corriger l\'article ou supprimer la distribution.');
    if (!(num(f.grams) > 0)) push('error', 'FEED_QTY_NONPOSITIVE', 'feed', f.id, label, 'Distribution sans quantite (grammes nuls).', 'Corriger la quantite distribuee.');
    if (day(f.date)) {
      const list = feedsByDate.get(day(f.date)) || [];
      list.push(f.id); feedsByDate.set(day(f.date), list);
    }
  }
  for (const [d, ids] of feedsByDate) {
    if (ids.length > 1) push('warning', 'FEED_DUP_DATE', 'feed', ids[0], d, `${ids.length} distributions enregistrees le meme jour : la regle de l'application est d'une seule distribution par jour.`, 'Ne garder qu\'une seule distribution pour ce jour.');
  }
  for (const x of data.tx) {
    const label = `${x.label || x.cat || 'Operation'} · ${day(x.date) || 'sans date'}`;
    if (!(num(x.amount) > 0)) push('error', 'TX_AMOUNT_NONPOSITIVE', 'tx', x.id, label, 'Montant nul ou negatif.', 'Corriger le montant.');
    if (day(x.date) && day(x.date) > today) push('error', 'TX_DATE_FUTURE', 'tx', x.id, label, 'Operation datee dans le futur.', 'Corriger la date.');
    if (x.rabbitId && !rabById.has(str(x.rabbitId))) push('error', 'TX_ORPHAN_RABBIT', 'tx', x.id, label, `Lapin ${x.rabbitId} introuvable.`, 'Corriger le lapin lie ou supprimer l\'operation.');
    if (x.batchId && !batchById.has(str(x.batchId))) push('error', 'TX_ORPHAN_BATCH', 'tx', x.id, label, `Lot ${x.batchId} introuvable.`, 'Corriger le lot lie ou supprimer l\'operation.');
    if (x.itemId && !itemById.has(str(x.itemId))) push('error', 'TX_ORPHAN_ITEM', 'tx', x.id, label, `Article ${x.itemId} introuvable.`, 'Corriger l\'article lie ou supprimer l\'operation.');
    if (x.type === 'in' && (x.cat || '').toLowerCase().includes('vente') && !x.rabbitId && !x.batchId) {
      push('warning', 'TX_SALE_UNLINKED', 'tx', x.id, label, 'Recette de vente sans lapin ni lot lie : impossible de suivre la marge par lot.', 'Lier la recette a son lapin ou a son lot.');
    }
  }

  // ---- Stock global et ventes sans transaction (controles ajoutes apres l'audit du 2 novembre) ----
  {
    const kgOfItem = (it) => { const q = num(it.qty); if (it.unit === 'sac 25 kg') return q * 25; if (it.unit === 'sac 50 kg') return q * 50; if (it.unit === 'g') return q / 1000; if (it.unit === 'kg') return q; return 0; };
    const bought = data.inventory.filter((i) => i.cat === 'Nourriture').reduce((s, i) => s + kgOfItem(i), 0);
    const used = data.feed.reduce((s, f) => s + num(f.grams), 0) / 1000;
    if (bought - used < 0) push('error', 'STOCK_NEGATIVE', 'inventory', '', 'Stock global', `Stock d'aliment negatif : ${bought.toFixed(1)} kg achetes pour ${used.toFixed(1)} kg distribues.`, 'Verifier les achats manquants, le stock de depart ou les distributions en trop.');
    const unlinked = data.feed.filter((f) => !f.item).length;
    if (unlinked > 0) push('warning', 'FEED_UNLINKED', 'feed', '', `${unlinked} distribution(s) sans article`, `${unlinked} distribution(s) sur ${data.feed.length} sans article lie : le restant par article ne peut pas etre suivi.`, 'Lier les distributions a un article, ou laisser l\'application les repartir en FIFO.');
    for (const r of rabbits) {
      if (r.status === 'Vendu' && !data.tx.some((x) => str(x.rabbitId) === str(r.id))) {
        push('warning', 'TX_MISSING_SALE', 'rabbit', r.id, r.name || r.id, 'Animal vendu sans recette financiere liee.', 'Renseigner le prix de vente pour creer la recette liee.');
      }
    }
  }

  // ---- Rappels ----
  for (const r of data.reminders) {
    const label = r.title || r.id || '(sans titre)';
    if (r.rabbitId && !rabById.has(str(r.rabbitId))) push('error', 'REM_ORPHAN_RABBIT', 'reminder', r.id, label, `Lapin ${r.rabbitId} introuvable.`, 'Corriger le lapin ou supprimer le rappel.');
    if (r.matingId && !matById.has(str(r.matingId))) push('error', 'REM_ORPHAN_MATING', 'reminder', r.id, label, `Saillie ${r.matingId} introuvable.`, 'Corriger la saillie ou supprimer le rappel.');
    if (r.treatmentId && !treatById.has(str(r.treatmentId))) push('error', 'REM_ORPHAN_TREATMENT', 'reminder', r.id, label, `Soin ${r.treatmentId} introuvable.`, 'Corriger le soin ou supprimer le rappel.');
    if (!r.done && r.rabbitId && exited(rabById.get(str(r.rabbitId)))) push('error', 'REM_OPEN_EXITED', 'reminder', r.id, label, 'Rappel encore ouvert pour un animal sorti.', 'Marquer ce rappel comme fait.');
    if (!r.done && day(r.due) && day(r.due) < today) push('warning', 'REM_OVERDUE', 'reminder', r.id, label, `Rappel en retard depuis le ${day(r.due)}.`, 'Traiter ou reporter ce rappel.');
  }

  // ---- Cages ----
  const cageNames = new Map();
  for (const c of data.cages) {
    if (!c.name) { push('warning', 'CAGE_NO_NAME', 'cage', c.id, '(sans nom)', 'Cage sans nom.', 'Nommer la cage.'); continue; }
    const k = str(c.name).trim().toLowerCase();
    if (cageNames.has(k)) push('error', 'CAGE_DUP_NAME', 'cage', c.id, c.name, 'Nom de cage en double.', 'Renommer l\'une des deux cages.');
    else cageNames.set(k, c.id);
  }
  for (const [nameKey, cage] of cageByName) {
    const occ = rabbits.filter((r) => active(r) && str(r.cage || '').trim().toLowerCase() === nameKey).length;
    if (num(cage.capacity) > 0 && occ > num(cage.capacity)) {
      push('error', 'CAGE_OVER_CAPACITY', 'cage', cage.id, cage.name, `${occ} occupant(s) actif(s) pour une capacite de ${num(cage.capacity)}.`, 'Deplacer des lapins vers une autre cage.');
    }
  }

  const counts = {};
  for (const [k, v] of Object.entries(data)) counts[k] = v.length;
  const totals = {
    errors: issues.filter((i) => i.severity === 'error').length,
    warnings: issues.filter((i) => i.severity === 'warning').length
  };
  return { counts, totals, totalIssues: issues.length, issues };
}

exports.handler = async (event, context) => {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Content-Type': 'application/json'
  };
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 200, headers, body: '' };
  }

  const rawPath = (event.path || '') + ' ' + (event.rawUrl || '');
  const query = event.queryStringParameters || {};
  const isAudit = rawPath.includes('audit') || query.audit === '1' || query.audit === 'true';
  const isAlerts = rawPath.includes('alerts');

  try {
    if (event.httpMethod === 'GET') {
      if (isAudit) {
        return {
          statusCode: 200, headers,
          body: JSON.stringify({
            status: 'online',
            service: 'ABV Farm Audit API',
            endpoint: 'POST /api/audit',
            note: 'L\'API ne stocke aucune donnee : elle analyse les donnees envoyees et renvoie le rapport dans la reponse.',
            bodyFormats: [
              '{ docs: [{ kind: "rabbit"|"mating"|"batch"|"weight"|"treatment"|"item"|"feed"|"tx"|"reminder"|"cage", ... }] }  (format du fichier exporte par l\'application)',
              '{ data: { rabbits: [...], matings: [...], batches: [...], weights: [...], treatments: [...], inventory: [...], feed: [...], tx: [...], reminders: [...], cages: [...] } }'
            ],
            optional: '{ params: { gestation: 31, weaning: 35 }, today: "YYYY-MM-DD" }',
            checks: ['liens orphelins (lapin, saillie, lot, article, soin, rappel)', 'codes et noms en double', 'dates futures ou incoherentes', 'animaux sortis avec des donnees apres la sortie', 'saillies ouvertes en double ou en retard', 'consanguinite directe', 'effectifs de lots', 'stocks et distributions en double le meme jour', 'ventes sans lien lapin/lot', 'rappels en retard', 'cages surpeuplees ou non definies']
          })
        };
      }
      if (isAlerts) {
        return {
          statusCode: 200, headers,
          body: JSON.stringify({
            status: 'online',
            service: 'ABV Farm Alert Engine',
            rules: [
              { code: 'PALPATION', window: 'J10-J14', label: 'Palpation de confirmation de gestation' },
              { code: 'NEST_BOX', day: 'J28', label: 'Mise en place de la boîte à nid' },
              { code: 'KINDLE', day: 'J31', label: 'Mise bas attendue' },
              { code: 'WEANING', window: 'J35-J42', label: 'Sevrage des lapereaux' },
              { code: 'FEED_STOCK', threshold: 10, unit: 'kg', label: 'Alerte stock critique' },
              { code: 'TREATMENTS', label: 'Rappels de vaccins et délai d\'attente sanitaire' }
            ],
            offlineCapable: true
          })
        };
      }
      return {
        statusCode: 200, headers,
        body: JSON.stringify({
          status: 'online',
          app: 'ABV Farm Backend API',
          timestamp: new Date().toISOString(),
          route: rawPath.trim() || 'root',
          features: { serverless: true, nodeVersion: process.version, authSupported: true, alertsSupported: true, auditSupported: true }
        })
      };
    }

    if (event.httpMethod === 'POST') {
      const payload = event.body ? JSON.parse(event.body) : {};
      if (isAudit || payload.action === 'audit' || Array.isArray(payload.docs) || payload.data || payload.S) {
        if (!Array.isArray(payload.docs) && !payload.data && !payload.S) {
          return { statusCode: 400, headers, body: JSON.stringify({ success: false, error: 'NO_DATA', message: 'Envoyez { docs: [...] } (format export) ou { data: { rabbits: [...], ... } }.' }) };
        }
        const report = auditData(payload);
        return {
          statusCode: 200, headers,
          body: JSON.stringify({
            success: true,
            service: 'ABV Farm Audit API',
            generatedAt: new Date().toISOString(),
            counts: report.counts,
            totals: report.totals,
            totalIssues: report.totalIssues,
            issues: report.issues
          })
        };
      }
      if (isAlerts || payload.action === 'send_alert') {
        return {
          statusCode: 501, headers,
          body: JSON.stringify({
            success: false,
            error: 'NOT_IMPLEMENTED',
            message: "Envoi de notification non configuré : les alertes restent visibles dans l'application uniquement.",
            alertTitle: payload.title || 'Alerte cheptel',
            count: payload.count || 1,
            timestamp: new Date().toISOString()
          })
        };
      }
      return {
        statusCode: 200, headers,
        body: JSON.stringify({
          success: true,
          message: 'Données traitées avec succès par le backend',
          receivedAction: payload.action || 'default',
          result: { processedAt: new Date().toISOString() }
        })
      };
    }

    return { statusCode: 405, headers, body: JSON.stringify({ error: 'Méthode non autorisée' }) };
  } catch (error) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: error.message }) };
  }
};

// Export pour les tests locaux (node) : ne change rien au comportement Netlify.
if (typeof module !== 'undefined' && module.exports) {
  module.exports.auditData = auditData;
  module.exports.normalizeData = normalizeData;
}
