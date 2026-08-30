/* Parent dashboard, chore management, and settings views */
const Parent = (() => {

  async function renderDashboard() {
    const profiles = await DB.getAllProfiles();
    const kids = profiles.filter(p => p.role === 'kid');
    const pending = await Chores.getPendingApprovals();
    const user = Auth.getCurrentUser();

    if (kids.length === 0) {
      return `
        ${_header('🏠 FamilyTime', pending.length)}
        <div class="page">
          <div class="empty-state">
            <span class="empty-icon">👧</span>
            <div class="empty-title">No kids added yet</div>
            <div class="empty-desc">Add your kids' profiles in Settings to get started.</div>
            <button class="btn btn-primary" style="margin-top:24px" id="btn-go-settings">Go to Settings</button>
          </div>
        </div>
        ${_bottomNav('home', pending.length)}
      `;
    }

    const kidCards = await Promise.all(kids.map(async kid => {
      const chores = await DB.getTodayChores(kid._id);
      const st = await DB.getTodayScreenTime(kid._id);
      return _renderKidCard(kid, chores, st);
    }));

    return `
      ${_header('🏠 FamilyTime', pending.length)}
      <div class="page">
        <div class="section-header" style="margin-bottom:20px">
          <span class="section-title">Hi ${user.name}! ${user.avatar}</span>
          <button class="btn btn-ghost btn-sm" id="btn-logout">Logout</button>
        </div>
        ${kidCards.join('')}
      </div>
      ${_bottomNav('home', pending.length)}
    `;
  }

  function _renderKidCard(kid, chores, st) {
    const approved = chores.filter(c => c.status === 'approved').length;
    const pendingApproval = chores.filter(c => c.status === 'pending_approval').length;
    const total = chores.length;
    const progress = total > 0 ? Math.round((approved / total) * 100) : 0;
    const remaining = Math.max(0, ((st.earnedMinutes || 0) + (st.bonusMinutes || 0)) * 60 - (st.usedSeconds || 0));
    const remainingMin = Math.floor(remaining / 60);

    return `
      <div class="kid-card" style="--kid-color:${kid.color || '#6C63FF'}">
        <div class="kid-card-header">
          <div class="kid-card-avatar">${kid.avatar}</div>
          <div class="kid-card-info">
            <div class="kid-card-name">${kid.name}</div>
            <div class="kid-card-status">
              ${approved}/${total} chores done
              ${pendingApproval > 0 ? `&nbsp;•&nbsp;<span style="color:var(--orange);font-weight:600">${pendingApproval} need approval</span>` : ''}
            </div>
          </div>
          <span class="badge ${remainingMin > 0 ? 'badge-success' : 'badge-muted'}">${remainingMin}m left</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill ${progress === 100 ? 'green' : ''}" style="width:${progress}%"></div>
        </div>
        <div class="kid-card-actions">
          <button class="btn btn-ghost btn-sm" data-action="manage-chores" data-kid="${kid._id}">📋 Chores</button>
          <button class="btn btn-success btn-sm" data-action="bonus-time" data-kid="${kid._id}">+15 min</button>
          ${pendingApproval > 0 ? `<button class="btn btn-warning btn-sm" data-action="approve-all" data-kid="${kid._id}">✓ Approve All</button>` : ''}
        </div>
      </div>
    `;
  }

  async function renderChorePage(kidId) {
    const kid = await DB.getProfile(kidId);
    const allTemplates = await DB.getChoreTemplates();
    const templates = allTemplates.filter(t =>
      t.assignedTo === kidId ||
      t.assignedTo === 'all' ||
      (t.assignedTo === 'rotating' && Array.isArray(t.rotationKids) && t.rotationKids.includes(kidId))
    );
    const todayChores = await DB.getTodayChores(kidId);
    const pending = await Chores.getPendingApprovals();

    const templateList = templates.length > 0
      ? templates.map(t => {
          const assignBadge = t.assignedTo === 'all'
            ? `<span style="font-size:11px;background:#E3F2FD;color:#1565C0;border-radius:8px;padding:2px 7px;font-weight:700;white-space:nowrap">👨‍👩‍👧 All Kids</span>`
            : t.assignedTo === 'rotating'
            ? `<span style="font-size:11px;background:#FFF8E1;color:#E65100;border-radius:8px;padding:2px 7px;font-weight:700;white-space:nowrap">🔄 Rotating</span>`
            : '';
          return `
          <div class="card" style="display:flex;align-items:center;gap:12px;margin-bottom:10px">
            <span style="font-size:30px;flex-shrink:0">${t.icon}</span>
            <div style="flex:1;min-width:0">
              <div style="font-weight:600;font-size:15px">${t.title}</div>
              <div style="font-size:12px;color:var(--text-muted);margin-top:2px;display:flex;align-items:center;gap:6px">${_freqLabel(t.frequency)} &bull; +${t.screenTimeMinutes} min ${assignBadge}</div>
            </div>
            <button class="btn btn-ghost btn-sm" data-action="delete-template" data-id="${t._id}" title="Delete">🗑️</button>
          </div>`;
        }).join('')
      : `<div class="empty-state" style="padding:24px 0"><span class="empty-icon" style="font-size:40px">📋</span><div class="empty-desc">No recurring chores yet. Add one!</div></div>`;

    const todayList = todayChores.length > 0
      ? todayChores.map(c => `
          <div class="card" style="display:flex;align-items:center;gap:12px;margin-bottom:10px">
            <span style="font-size:30px;flex-shrink:0">${c.icon}</span>
            <div style="flex:1;min-width:0">
              <div style="font-weight:600;font-size:15px">${c.title}</div>
              <span class="badge ${c.status === 'approved' ? 'badge-success' : c.status === 'pending_approval' ? 'badge-warning' : 'badge-muted'}" style="margin-top:4px;display:inline-flex">
                ${c.status === 'approved' ? '✓ Done' : c.status === 'pending_approval' ? '⏳ Pending Approval' : '○ To Do'}
              </span>
            </div>
            ${c.status === 'pending_approval' ? `
              <button class="btn btn-success btn-sm" data-action="approve-chore" data-id="${c._id}">✓ Approve</button>
              <button class="btn btn-ghost btn-sm" data-action="reject-chore" data-id="${c._id}">✗</button>
            ` : ''}
          </div>
        `).join('')
      : `<div style="color:var(--text-muted);font-size:14px;padding:12px 0">No chores yet for today.</div>`;

    return `
      <div class="app-header">
        <button class="btn-icon" id="btn-back-dashboard">←</button>
        <span class="header-title">${kid.avatar} ${kid.name}'s Chores</span>
        <button class="btn btn-primary btn-sm" id="btn-add-chore" data-kid="${kidId}">+ Add</button>
      </div>
      <div class="page">
        <div class="section-header"><span class="section-title">Recurring Chores</span></div>
        ${templateList}
        <div class="section-header" style="margin-top:24px"><span class="section-title">Today</span></div>
        ${todayList}
      </div>
    `;
  }

  async function renderSettingsPage() {
    const profiles = await DB.getAllProfiles();
    const kids = profiles.filter(p => p.role === 'kid');
    const parent = profiles.find(p => p.role === 'parent');
    const settings = await DB.getSettings();

    const kidRows = kids.length > 0
      ? kids.map(k => `
          <div class="card" style="display:flex;align-items:center;gap:14px;margin-bottom:10px">
            <span style="font-size:36px">${k.avatar}</span>
            <div style="flex:1;min-width:0">
              <div style="font-weight:600;font-size:16px">${k.name}</div>
              <div style="font-size:12px;color:var(--text-muted);margin-top:2px">${k.dailyMinutes || 60} min/day &bull; ${k.choresToUnlock || 0} chores required &bull; PIN: ${k.pin ? '****' : 'none'}</div>
            </div>
            <button class="btn btn-ghost btn-sm" data-action="edit-kid" data-id="${k._id}">Edit</button>
            <button class="btn btn-ghost btn-sm" data-action="delete-kid" data-id="${k._id}">🗑️</button>
          </div>
        `).join('')
      : `<div style="color:var(--text-muted);font-size:14px;margin-bottom:12px">No kids added yet.</div>`;

    return `
      <div class="app-header">
        <button class="btn-icon" id="btn-back-dashboard">←</button>
        <span class="header-title">⚙️ Settings</span>
      </div>
      <div class="page">
        <div class="section-header">
          <span class="section-title">Kids</span>
          <button class="btn btn-primary btn-sm" id="btn-add-kid">+ Add Kid</button>
        </div>
        ${kidRows}

        <div class="section-header" style="margin-top:28px"><span class="section-title">Options</span></div>
        <div class="card" style="margin-bottom:12px">
          <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 0">
            <div>
              <div style="font-weight:600">Auto-approve chores</div>
              <div style="font-size:13px;color:var(--text-muted);margin-top:2px">Kids earn time immediately when they mark a chore done</div>
            </div>
            <label class="toggle-wrap">
              <input type="checkbox" id="toggle-auto-approve" ${settings.autoApproveChores ? 'checked' : ''}>
              <span class="toggle-slider"></span>
            </label>
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 0;border-top:1px solid var(--border)">
            <div>
              <div style="font-weight:600">🏖️ Vacation mode</div>
              <div style="font-size:13px;color:var(--text-muted);margin-top:2px">Screen time unlocks without completing chores</div>
            </div>
            <label class="toggle-wrap">
              <input type="checkbox" id="toggle-vacation" ${settings.vacationMode ? 'checked' : ''}>
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>

        <div class="section-header" style="margin-top:28px"><span class="section-title">Parent Account</span></div>
        <div class="card">
          <div style="display:flex;align-items:center;gap:12px">
            <span style="font-size:36px">${parent ? parent.avatar : '👨'}</span>
            <div>
              <div style="font-weight:600;font-size:16px">${parent ? parent.name : 'Parent'}</div>
              <div style="font-size:13px;color:var(--text-muted)">Default PIN: 0000</div>
            </div>
          </div>
        </div>
      </div>
      ${_bottomNav('settings', 0)}
    `;
  }

  function _header(title, pendingCount) {
    return `
      <div class="app-header">
        <span class="header-title">${title}</span>
        <div class="header-actions">
          ${pendingCount > 0 ? `<span style="font-size:12px;background:var(--coral);color:white;border-radius:12px;padding:3px 10px;font-weight:700">${pendingCount} pending</span>` : ''}
          <button class="btn-icon" id="btn-parent-settings" title="Settings">⚙️</button>
        </div>
      </div>
    `;
  }

  function _bottomNav(active, badgeCount) {
    return `
      <nav class="bottom-nav">
        <button class="nav-item ${active === 'home' ? 'active' : ''}" id="nav-home">
          <span class="nav-icon">🏠</span><span>Home</span>
        </button>
        <button class="nav-item ${active === 'chores' ? 'active' : ''}" id="nav-chores">
          <span class="nav-icon">📋</span><span>Chores</span>
          ${badgeCount > 0 ? `<span class="nav-badge">${badgeCount}</span>` : ''}
        </button>
        <button class="nav-item ${active === 'settings' ? 'active' : ''}" id="nav-settings">
          <span class="nav-icon">⚙️</span><span>Settings</span>
        </button>
      </nav>
    `;
  }

  function _freqLabel(freq) {
    return { daily: 'Every day', weekday: 'Weekdays', weekend: 'Weekends', once: 'One time' }[freq] || freq;
  }

  return { renderDashboard, renderChorePage, renderSettingsPage };
})();
