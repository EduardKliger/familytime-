/* Database layer — PouchDB wrapper with all data helpers */
const DB = (() => {
  let _db;
  let _sync = null;

  async function init() {
    _db = new PouchDB('familytime');
    await _ensureDefaults();
    await _generateDailyChores();
    _startSync();
  }

  /* auto-sync to the server this app was loaded from — works on every device without manual config */
  function _startSync() {
    if (!window.location.protocol.startsWith('http')) return;
    const remote = window.location.origin + '/sync/familytime';
    if (_sync) { _sync.cancel(); }
    _sync = _db.sync(remote, { live: true, retry: true })
      .on('change', () => { if (typeof Notifications !== 'undefined') Notifications.toast('Synced ✓', 'success', 1500); })
      .on('error',  e  => console.warn('[sync]', e));
  }

  async function _ensureDefaults() {
    const profiles = await getAllProfiles();
    const parent = profiles.find(p => p.role === 'parent');
    if (!parent) {
      await _db.bulkDocs([
        { _id: 'profile_parent', type: 'profile', name: 'Parent', role: 'parent', pin: '0000', avatar: '👨', color: '#6C63FF' },
        { _id: 'settings_global', type: 'settings', autoApproveChores: false, vacationMode: false, deviceName: 'My Device' }
      ]);
    } else if (parent.pin && parent.pin.length === 64) {
      /* migrate old SHA-256 hashed PIN → plain text 0000 */
      parent.pin = '0000';
      await _db.put(parent);
    }
  }

  function hashPin(pin) { return pin; }

  function verifyPin(pin, stored) {
    if (!stored) return true;
    return pin === stored;
  }

  async function getAllProfiles() {
    const r = await _db.allDocs({ include_docs: true, startkey: 'profile_', endkey: 'profile_\uffff' });
    return r.rows.map(row => row.doc);
  }

  async function getProfile(id) {
    return await _db.get(id);
  }

  async function upsert(doc) {
    try {
      const existing = await _db.get(doc._id);
      doc._rev = existing._rev;
    } catch (e) {
      if (e.status !== 404) throw e;
    }
    return await _db.put(doc);
  }

  async function remove(id) {
    const doc = await _db.get(id);
    return await _db.remove(doc);
  }

  async function getAllChores() {
    const r = await _db.allDocs({ include_docs: true, startkey: 'chore_', endkey: 'chore_\uffff' });
    return r.rows.map(row => row.doc);
  }

  async function getChore(id) {
    return await _db.get(id);
  }

  async function getTodayChores(kidId) {
    const today = _today();
    const all = await getAllChores();
    return all.filter(c => c.assignedTo === kidId && c.date === today && !c.isTemplate);
  }

  async function getChoreTemplates() {
    const all = await getAllChores();
    return all.filter(c => c.isTemplate === true);
  }

  async function getTodayScreenTime(kidId) {
    const today = _today();
    const id = `screentime_${kidId}_${today}`;
    try {
      return await _db.get(id);
    } catch (e) {
      if (e.status !== 404) throw e;
      const profile = await getProfile(kidId);
      const doc = {
        _id: id,
        type: 'screentime',
        kidId,
        date: today,
        budgetMinutes: profile.dailyMinutes || 60,
        earnedMinutes: 0,
        usedSeconds: 0,
        active: false,
        sessionStart: null,
        bonusMinutes: 0,
        locked: false
      };
      await _db.put(doc);
      return doc;
    }
  }

  async function getSettings() {
    try {
      return await _db.get('settings_global');
    } catch (e) {
      return { _id: 'settings_global', autoApproveChores: false, vacationMode: false };
    }
  }

  async function _generateDailyChores() {
    const today = _today();
    const all = await getAllChores();
    const templates = all.filter(c => c.isTemplate === true);
    const todayInstances = all.filter(c => !c.isTemplate && c.date === today);
    /* composite key prevents duplicate generation for all/rotating chores */
    const existingKeys = new Set(todayInstances.map(c => `${c.templateId}_${c.assignedTo}`));
    const dayOfWeek = new Date().getDay();
    const dayIndex = Math.floor(new Date(today + 'T12:00:00Z').getTime() / 86400000);
    const profiles = await getAllProfiles();
    const kids = profiles.filter(p => p.role === 'kid');

    const toCreate = [];
    for (const t of templates) {
      if (t.frequency === 'weekday' && !(dayOfWeek >= 1 && dayOfWeek <= 5)) continue;
      if (t.frequency === 'weekend' && !(dayOfWeek === 0 || dayOfWeek === 6)) continue;
      if (!['daily','weekday','weekend'].includes(t.frequency)) continue;

      if (t.assignedTo === 'all') {
        for (const kid of kids) {
          if (!existingKeys.has(`${t._id}_${kid._id}`))
            toCreate.push(_makeInstance(t, kid._id, today, `${t._id}_${kid._id}`));
        }
      } else if (t.assignedTo === 'rotating' && t.rotationKids?.length > 0) {
        const period = t.rotationPeriod || 1;
        const kidId = t.rotationKids[Math.floor(dayIndex / period) % t.rotationKids.length];
        if (!existingKeys.has(`${t._id}_${kidId}`))
          toCreate.push(_makeInstance(t, kidId, today, t._id));
      } else {
        if (!existingKeys.has(`${t._id}_${t.assignedTo}`))
          toCreate.push(_makeInstance(t, t.assignedTo, today, t._id));
      }
    }

    if (toCreate.length > 0) await _db.bulkDocs(toCreate);
  }

  function _makeInstance(t, assignedTo, date, idSuffix) {
    return {
      _id: `chore_${date}_${idSuffix}`,
      type: 'chore', isTemplate: false, templateId: t._id,
      assignedTo, title: t.title, icon: t.icon,
      screenTimeMinutes: t.screenTimeMinutes, date,
      status: 'pending', createdBy: t.createdBy,
      completedAt: null, approvedAt: null
    };
  }

  function _today() {
    return new Date().toISOString().split('T')[0];
  }

  return {
    init,
    hashPin,
    verifyPin,
    getAllProfiles,
    getProfile,
    upsert,
    remove,
    getAllChores,
    getChore,
    getTodayChores,
    getChoreTemplates,
    getTodayScreenTime,
    getSettings,
    generateDailyChores: _generateDailyChores
  };
})();
