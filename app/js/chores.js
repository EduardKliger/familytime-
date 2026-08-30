/* Chore creation, completion, approval, and rendering */
const Chores = (() => {
  const CHORE_ICONS = ['🛏️','🧹','🍽️','🚿','📚','🐕','🌱','🧺','🗑️','🪣','🧴','🥗','🍎','🎒','🪥','💪','🏃','🚴','🧼','⭐','🎮','📖','🎨','🧩','🏊'];

  async function createTemplate(data) {
    const id = `chore_template_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
    const doc = {
      _id: id,
      type: 'chore',
      isTemplate: true,
      assignedTo: data.assignedTo,
      rotationKids: data.rotationKids || null,
      rotationPeriod: data.rotationPeriod || 1,
      title: data.title,
      icon: data.icon || '⭐',
      screenTimeMinutes: data.screenTimeMinutes || 20,
      frequency: data.frequency || 'daily',
      createdBy: data.createdBy || 'profile_parent',
      createdAt: new Date().toISOString()
    };
    await DB.upsert(doc);
    await DB.generateDailyChores();
    return doc;
  }

  async function createOneTimeChore(data) {
    const today = new Date().toISOString().split('T')[0];
    const id = `chore_${today}_once_${Date.now()}`;
    const doc = {
      _id: id,
      type: 'chore',
      isTemplate: false,
      templateId: null,
      assignedTo: data.assignedTo,
      title: data.title,
      icon: data.icon || '⭐',
      screenTimeMinutes: data.screenTimeMinutes || 20,
      date: today,
      frequency: 'once',
      status: 'pending',
      createdBy: data.createdBy || 'profile_parent',
      completedAt: null,
      approvedAt: null
    };
    await DB.upsert(doc);
    return doc;
  }

  async function markDone(choreId) {
    const chore = await DB.getChore(choreId);
    const settings = await DB.getSettings();
    chore.completedAt = new Date().toISOString();
    if (settings.autoApproveChores || settings.vacationMode) {
      chore.status = 'approved';
      chore.approvedAt = chore.completedAt;
      await _earnScreenTime(chore.assignedTo, chore.screenTimeMinutes);
    } else {
      chore.status = 'pending_approval';
    }
    await DB.upsert(chore);
    return chore;
  }

  async function approve(choreId) {
    const chore = await DB.getChore(choreId);
    if (chore.status === 'pending_approval') {
      chore.status = 'approved';
      chore.approvedAt = new Date().toISOString();
      await DB.upsert(chore);
      await _earnScreenTime(chore.assignedTo, chore.screenTimeMinutes);
    }
    return chore;
  }

  async function reject(choreId) {
    const chore = await DB.getChore(choreId);
    chore.status = 'pending';
    chore.completedAt = null;
    await DB.upsert(chore);
    return chore;
  }

  async function deleteTemplate(id) {
    return await DB.remove(id);
  }

  async function getPendingApprovals() {
    const all = await DB.getAllChores();
    return all.filter(c => c.status === 'pending_approval');
  }

  async function _earnScreenTime(kidId, minutes) {
    const st = await DB.getTodayScreenTime(kidId);
    st.earnedMinutes = (st.earnedMinutes || 0) + minutes;
    await DB.upsert(st);
  }

  async function renderChoreList(kidId) {
    const chores = await DB.getTodayChores(kidId);
    if (chores.length === 0) {
      return `
        <div class="empty-state">
          <span class="empty-icon">🎉</span>
          <div class="empty-title">No chores today!</div>
          <div class="empty-desc">Ask a parent to add some chores.</div>
        </div>
      `;
    }

    const items = chores.map(c => {
      const isDone = c.status === 'approved';
      const isPendingApproval = c.status === 'pending_approval';
      const extra = isPendingApproval
        ? `<div style="font-size:12px;color:var(--orange);margin-top:4px">⏳ Waiting for parent approval</div>`
        : '';
      return `
        <div class="chore-card ${isDone ? 'done' : ''} ${isPendingApproval ? 'pending-approval' : ''}"
             data-chore-id="${c._id}"
             ${!isDone && !isPendingApproval ? 'tabindex="0"' : ''}
             role="button">
          <div class="chore-icon">${c.icon}</div>
          <div class="chore-info">
            <div class="chore-title">${c.title}</div>
            <div class="chore-reward">+${c.screenTimeMinutes} min screen time</div>
            ${extra}
          </div>
          <div class="chore-check">${isDone || isPendingApproval ? '✓' : ''}</div>
        </div>
      `;
    }).join('');

    return `<div class="chore-list">${items}</div>`;
  }

  async function renderChoreForm(defaultKidId) {
    const profiles = await DB.getAllProfiles();
    const kids = profiles.filter(p => p.role === 'kid');
    const iconOptions = CHORE_ICONS.map((ic, i) =>
      `<div class="icon-option${i === 0 ? ' selected' : ''}" data-icon="${ic}">${ic}</div>`
    ).join('');
    const kidOptions = kids.map(k =>
      `<option value="${k._id}"${k._id === defaultKidId ? ' selected' : ''}>${k.avatar} ${k.name}</option>`
    ).join('');
    const rotationChecks = kids.map(k =>
      `<label style="display:flex;align-items:center;gap:10px;padding:8px 0;cursor:pointer">
        <input type="checkbox" value="${k._id}" class="rotation-kid-cb" checked style="width:18px;height:18px;accent-color:var(--purple)">
        <span style="font-size:22px">${k.avatar}</span>
        <span style="font-weight:600">${k.name}</span>
      </label>`
    ).join('');

    return `
      <div class="modal-handle"></div>
      <div class="modal-title">Add Chore</div>
      <div class="form-group">
        <label class="form-label">Chore Name</label>
        <input class="form-input" id="chore-title" type="text" placeholder="e.g. Clean Room" maxlength="30" autocomplete="off">
      </div>
      <div class="form-group">
        <label class="form-label">Icon</label>
        <div class="icon-picker" id="icon-picker">${iconOptions}</div>
        <input type="hidden" id="chore-icon" value="${CHORE_ICONS[0]}">
      </div>
      <div class="form-group">
        <label class="form-label">Screen Time Reward: <span id="st-reward-label">20 min</span></label>
        <input class="form-input" type="range" id="chore-st" min="5" max="60" step="5" value="20">
      </div>
      <div class="form-group">
        <label class="form-label">Frequency</label>
        <select class="form-input" id="chore-freq">
          <option value="daily">Every Day</option>
          <option value="weekday">Weekdays Only</option>
          <option value="weekend">Weekends Only</option>
          <option value="once">One Time (Today Only)</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">Assign To</label>
        <select class="form-input" id="chore-assign-type">
          ${kidOptions}
          <option value="all">👨‍👩‍👧 All Kids (each gets their own)</option>
          <option value="rotating">🔄 Rotating (takes turns by date)</option>
        </select>
      </div>
      <div id="rotation-panel" style="display:none;background:var(--bg);border-radius:var(--radius);padding:14px;margin-top:8px">
        <div style="font-size:12px;font-weight:700;color:var(--text-muted);margin-bottom:8px">WHICH KIDS ROTATE</div>
        ${rotationChecks}
        <div class="form-group" style="margin-top:12px;margin-bottom:0">
          <label class="form-label">Each kid does it for</label>
          <select class="form-input" id="chore-rotation-period">
            <option value="1">1 day then next kid</option>
            <option value="3">3 days then next kid</option>
            <option value="7" selected>1 week then next kid</option>
            <option value="14">2 weeks then next kid</option>
          </select>
        </div>
      </div>
      <div style="display:flex;gap:12px;margin-top:24px">
        <button class="btn btn-ghost btn-full" id="btn-chore-cancel">Cancel</button>
        <button class="btn btn-primary btn-full" id="btn-chore-save">Save Chore</button>
      </div>
    `;
  }

  return {
    CHORE_ICONS,
    createTemplate,
    createOneTimeChore,
    markDone,
    approve,
    reject,
    deleteTemplate,
    getPendingApprovals,
    renderChoreList,
    renderChoreForm
  };
})();
