/* SPA router — renders views and attaches all event handlers */
const Router = (() => {
  let _lastParams = null;

  function init() {
    /* intentionally empty — navigation is driven by explicit navigate() calls */
  }

  async function navigate(view, params) {
    _lastParams = params || null;
    const app = document.getElementById('app');
    if (!app) return;

    try {
      let html = '';
      switch (view) {
        case 'login':           html = await Auth.renderLoginScreen(); break;
        case 'parent-dashboard': html = await Parent.renderDashboard(); break;
        case 'parent-chores':
          if (!params || !params.kidId) { navigate('parent-dashboard'); return; }
          html = await Parent.renderChorePage(params.kidId);
          break;
        case 'parent-settings': html = await Parent.renderSettingsPage(); break;
        case 'kid-home':        html = await Kid.renderHome(); break;
        case 'kid-timer': {
          const remaining = await ScreenTime.getRemainingSeconds(Auth.getCurrentUser().id);
          html = Kid.renderTimer(remaining);
          break;
        }
        case 'kid-locked':
          await ScreenTime.stop(false);
          html = Kid.renderLocked();
          break;
        default:
          navigate('login'); return;
      }

      app.innerHTML = html;
      _attachHandlers(view, params);
    } catch (err) {
      console.error('Router error on view:', view, err);
      Notifications.toast('Something went wrong — please try again.', 'error');
    }
  }

  function _attachHandlers(view, params) {
    // ── LOGIN ──────────────────────────────────────────
    if (view === 'login') {
      document.querySelectorAll('.profile-card').forEach(card => {
        card.addEventListener('click', () => _handleProfileTap(card));
        card.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') _handleProfileTap(card); });
      });
      document.getElementById('btn-add-profile')?.addEventListener('click', () => _showKidModal(null));
    }

    // ── PARENT DASHBOARD ───────────────────────────────
    if (view === 'parent-dashboard') {
      document.getElementById('btn-logout')?.addEventListener('click', () => { Auth.logout(); navigate('login'); });
      document.getElementById('btn-parent-settings')?.addEventListener('click', () => navigate('parent-settings'));
      document.getElementById('btn-go-settings')?.addEventListener('click', () => navigate('parent-settings'));
      document.getElementById('nav-home')?.addEventListener('click', () => navigate('parent-dashboard'));
      document.getElementById('nav-settings')?.addEventListener('click', () => navigate('parent-settings'));
      document.querySelectorAll('[data-action]').forEach(btn => btn.addEventListener('click', () => _handleParentAction(btn)));
    }

    // ── PARENT CHORES ──────────────────────────────────
    if (view === 'parent-chores') {
      document.getElementById('btn-back-dashboard')?.addEventListener('click', () => navigate('parent-dashboard'));
      document.getElementById('btn-add-chore')?.addEventListener('click', e => {
        _showChoreModal(e.currentTarget.dataset.kid);
      });
      document.querySelectorAll('[data-action]').forEach(btn => btn.addEventListener('click', async () => {
        const { action, id } = btn.dataset;
        if (action === 'delete-template') {
          if (confirm('Remove this chore?')) { await Chores.deleteTemplate(id); navigate('parent-chores', params); }
        }
        if (action === 'approve-chore') {
          await Chores.approve(id);
          Notifications.toast('Chore approved! ✓', 'success');
          navigate('parent-chores', params);
        }
        if (action === 'reject-chore') {
          await Chores.reject(id);
          Notifications.toast('Chore sent back to pending.', 'warning');
          navigate('parent-chores', params);
        }
      }));
    }

    // ── PARENT SETTINGS ────────────────────────────────
    if (view === 'parent-settings') {
      document.getElementById('btn-back-dashboard')?.addEventListener('click', () => navigate('parent-dashboard'));
      document.getElementById('nav-home')?.addEventListener('click', () => navigate('parent-dashboard'));
      document.getElementById('nav-settings')?.addEventListener('click', () => navigate('parent-settings'));
      document.getElementById('btn-add-kid')?.addEventListener('click', () => _showKidModal(null));
      document.getElementById('toggle-auto-approve')?.addEventListener('change', async e => {
        const s = await DB.getSettings();
        s.autoApproveChores = e.target.checked;
        await DB.upsert(s);
        Notifications.toast(e.target.checked ? 'Auto-approve ON' : 'Auto-approve OFF');
      });
      document.getElementById('toggle-vacation')?.addEventListener('change', async e => {
        const s = await DB.getSettings();
        s.vacationMode = e.target.checked;
        await DB.upsert(s);
        Notifications.toast(e.target.checked ? '🏖️ Vacation mode ON' : 'Vacation mode OFF');
      });
      document.querySelectorAll('[data-action]').forEach(btn => btn.addEventListener('click', async () => {
        const { action, id } = btn.dataset;
        if (action === 'delete-kid') {
          if (confirm('Remove this kid\'s profile and all their data?')) {
            await DB.remove(id);
            Notifications.toast('Profile removed.', 'warning');
            navigate('parent-settings');
          }
        }
        if (action === 'edit-kid') _showKidModal(id);
      }));
    }

    // ── KID HOME ───────────────────────────────────────
    if (view === 'kid-home') {
      document.getElementById('btn-kid-logout')?.addEventListener('click', () => { Auth.logout(); navigate('login'); });
      document.getElementById('btn-start-screentime')?.addEventListener('click', async () => {
        await ScreenTime.start(Auth.getCurrentUser().id);
        navigate('kid-timer');
      });
      document.getElementById('btn-request-time')?.addEventListener('click', () => {
        Notifications.toast('Time request sent to parent! 📩', 'success');
      });
      document.querySelectorAll('.chore-card:not(.done):not(.pending-approval)').forEach(card => {
        card.addEventListener('click', () => _handleChoreTap(card));
        card.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') _handleChoreTap(card); });
      });
    }

    // ── KID TIMER ──────────────────────────────────────
    if (view === 'kid-timer') {
      document.getElementById('btn-stop-timer')?.addEventListener('click', async () => {
        await ScreenTime.stop(true);
        navigate('kid-home');
      });
      document.getElementById('btn-request-time-timer')?.addEventListener('click', () => {
        Notifications.toast('Time request sent to parent! 📩', 'success');
      });
    }

    // ── KID LOCKED ─────────────────────────────────────
    if (view === 'kid-locked') {
      document.getElementById('btn-request-time-lock')?.addEventListener('click', () => {
        Notifications.toast('Time request sent to parent! 📩', 'success');
      });
      document.getElementById('btn-kid-logout-lock')?.addEventListener('click', () => { Auth.logout(); navigate('login'); });
    }
  }

  async function _handleParentAction(btn) {
    const { action, kid: kidId, name } = btn.dataset;
    if (action === 'manage-chores') navigate('parent-chores', { kidId });
    if (action === 'bonus-time') {
      await ScreenTime.grantBonus(kidId, 15);
      navigate('parent-dashboard');
    }
    if (action === 'approve-all') {
      const chores = await DB.getTodayChores(kidId);
      for (const c of chores.filter(ch => ch.status === 'pending_approval')) {
        await Chores.approve(c._id);
      }
      Notifications.toast('All chores approved! ✓', 'success');
      navigate('parent-dashboard');
    }
  }

  async function _handleChoreTap(card) {
    const choreId = card.dataset.choreId;
    if (!choreId) return;
    const settings = await DB.getSettings();
    const confirmed = settings.autoApproveChores || confirm('Mark this chore as done? 🎉');
    if (!confirmed) return;
    const chore = await Chores.markDone(choreId);
    card.classList.add(settings.autoApproveChores ? 'done' : 'pending-approval');
    card.querySelector('.chore-check').textContent = '✓';
    card.classList.add('celebrate');
    if (settings.autoApproveChores) {
      Notifications.toast(`+${chore.screenTimeMinutes} min earned! 🎉`, 'success');
    } else {
      Notifications.toast('Great job! Waiting for parent to approve 👍', 'default');
    }
    setTimeout(() => navigate('kid-home'), 900);
  }

  async function _handleProfileTap(card) {
    const profileId = card.dataset.id;
    const hasPin = card.dataset.hasPin === 'true';

    if (!hasPin) {
      const ok = await Auth.login(profileId, '');
      if (ok) {
        const user = Auth.getCurrentUser();
        navigate(user.role === 'parent' ? 'parent-dashboard' : 'kid-home');
      }
      return;
    }

    const profile = await DB.getProfile(profileId);
    document.getElementById('app').insertAdjacentHTML('beforeend', Auth.renderPinScreen(profile));

    let entered = '';

    document.getElementById('btn-pin-back').addEventListener('click', () => {
      document.getElementById('pin-screen')?.remove();
    });

    document.querySelectorAll('#pin-screen .pin-key').forEach(key => {
      key.addEventListener('click', async () => {
        const val = key.dataset.key;
        if (val === 'del') {
          entered = entered.slice(0, -1);
        } else {
          if (entered.length >= 4) return;
          entered += val;
        }
        _updatePinDots(entered.length);
        if (entered.length < 4) return;

        const ok = await Auth.login(profileId, entered);
        if (ok) {
          document.getElementById('pin-screen')?.remove();
          navigate(profile.role === 'parent' ? 'parent-dashboard' : 'kid-home');
        } else {
          entered = '';
          _updatePinDots(0);
          const errEl = document.getElementById('pin-error');
          const dotsEl = document.getElementById('pin-dots');
          errEl.classList.add('visible');
          dotsEl.style.animation = 'shake 0.45s ease';
          setTimeout(() => { errEl.classList.remove('visible'); dotsEl.style.animation = ''; }, 2200);
        }
      });
    });
  }

  function _updatePinDots(count) {
    [0, 1, 2, 3].forEach(i => {
      document.getElementById(`pin-dot-${i}`)?.classList.toggle('filled', i < count);
    });
  }

  async function _showChoreModal(kidId) {
    const overlay = document.getElementById('modal-overlay');
    overlay.innerHTML = `<div class="modal"><div style="padding:40px;text-align:center;font-size:36px">⏳</div></div>`;
    overlay.classList.remove('hidden');
    const formHtml = await Chores.renderChoreForm(kidId);
    overlay.innerHTML = `<div class="modal">${formHtml}</div>`;
    let selectedIcon = Chores.CHORE_ICONS[0];

    overlay.querySelector('#icon-picker').addEventListener('click', e => {
      const opt = e.target.closest('.icon-option');
      if (!opt) return;
      overlay.querySelectorAll('.icon-option').forEach(o => o.classList.remove('selected'));
      opt.classList.add('selected');
      selectedIcon = opt.dataset.icon;
    });
    overlay.querySelector('#chore-st').addEventListener('input', e => {
      overlay.querySelector('#st-reward-label').textContent = e.target.value + ' min';
    });
    overlay.querySelector('#chore-assign-type').addEventListener('change', e => {
      overlay.querySelector('#rotation-panel').style.display =
        e.target.value === 'rotating' ? 'block' : 'none';
    });
    overlay.querySelector('#btn-chore-cancel').addEventListener('click', _closeModal);
    overlay.querySelector('#btn-chore-save').addEventListener('click', async () => {
      const title = overlay.querySelector('#chore-title').value.trim();
      if (!title) { Notifications.toast('Please enter a chore name.', 'error'); return; }
      const freq   = overlay.querySelector('#chore-freq').value;
      const stMins = parseInt(overlay.querySelector('#chore-st').value);
      const assignType = overlay.querySelector('#chore-assign-type').value;
      let assignedTo = assignType;
      let rotationKids = null;
      let rotationPeriod = 1;
      if (assignType === 'rotating') {
        rotationKids = [...overlay.querySelectorAll('.rotation-kid-cb:checked')].map(cb => cb.value);
        if (rotationKids.length < 2) { Notifications.toast('Select at least 2 kids for rotation.', 'error'); return; }
        rotationPeriod = parseInt(overlay.querySelector('#chore-rotation-period').value) || 1;
      }
      if (freq === 'once') {
        const targetKid = (assignType === 'all' || assignType === 'rotating') ? kidId : assignType;
        await Chores.createOneTimeChore({ title, icon: selectedIcon, screenTimeMinutes: stMins, assignedTo: targetKid });
      } else {
        await Chores.createTemplate({ title, icon: selectedIcon, screenTimeMinutes: stMins, frequency: freq, assignedTo, rotationKids, rotationPeriod });
      }
      _closeModal();
      Notifications.toast('Chore added! ✓', 'success');
      if (assignType === 'all' || assignType === 'rotating') navigate('parent-dashboard');
      else navigate('parent-chores', { kidId: assignType });
    });
  }

  async function _showKidModal(editId) {
    const avatars = ['🦁','🐯','🐼','🐨','🦊','🐸','🦋','🐬','🦄','🐉','🐧','🦖','🐙','🦀','🌟','🚀','🎸','🎨','⚽','🎯'];
    const colors = ['#FF6B6B','#4ECDC4','#45B7D1','#96CEB4','#FFB347','#DDA0DD','#F7DC6F','#BB8FCE','#85C1E9','#F08080'];
    let existingKid = null;
    if (editId) { try { existingKid = await DB.getProfile(editId); } catch (e) { /* ignore */ } }

    const selAvatar = existingKid?.avatar || avatars[0];
    const selColor  = existingKid?.color  || colors[0];

    const overlay = document.getElementById('modal-overlay');
    overlay.innerHTML = `
      <div class="modal">
        <div class="modal-handle"></div>
        <div class="modal-title">${editId ? 'Edit Kid' : 'Add Kid'}</div>
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-input" id="kid-name" type="text" placeholder="e.g. Lior" maxlength="20" value="${existingKid?.name || ''}" autocomplete="off">
        </div>
        <div class="form-group">
          <label class="form-label">Avatar</label>
          <div class="icon-picker" id="kid-avatar-picker">
            ${avatars.map(a => `<div class="icon-option${a === selAvatar ? ' selected' : ''}" data-avatar="${a}">${a}</div>`).join('')}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Color</label>
          <div class="color-picker">
            ${colors.map(c => `<div class="color-swatch${c === selColor ? ' selected' : ''}" data-color="${c}" style="background:${c}"></div>`).join('')}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Daily screen time: <span id="daily-label">${existingKid?.dailyMinutes || 60} min</span></label>
          <input class="form-input" type="range" id="kid-daily" min="15" max="180" step="15" value="${existingKid?.dailyMinutes || 60}">
        </div>
        <div class="form-group">
          <label class="form-label">Chores required to unlock screen time</label>
          <input class="form-input" type="number" id="kid-required" min="0" max="10" value="${existingKid?.choresToUnlock ?? 1}">
        </div>
        <div class="form-group">
          <label class="form-label">PIN (4 digits — leave empty for no PIN)</label>
          <input class="form-input" id="kid-pin" type="tel" inputmode="numeric" placeholder="e.g. 1234" maxlength="4">
        </div>
        <div style="display:flex;gap:12px;margin-top:24px">
          <button class="btn btn-ghost btn-full" id="btn-kid-cancel">Cancel</button>
          <button class="btn btn-primary btn-full" id="btn-kid-save">Save</button>
        </div>
      </div>
    `;
    overlay.classList.remove('hidden');

    let selectedAvatar = selAvatar;
    let selectedColor  = selColor;

    overlay.querySelector('#kid-avatar-picker').addEventListener('click', e => {
      const opt = e.target.closest('.icon-option');
      if (!opt) return;
      overlay.querySelectorAll('#kid-avatar-picker .icon-option').forEach(o => o.classList.remove('selected'));
      opt.classList.add('selected');
      selectedAvatar = opt.dataset.avatar;
    });
    overlay.querySelector('.color-picker').addEventListener('click', e => {
      const sw = e.target.closest('.color-swatch');
      if (!sw) return;
      overlay.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('selected'));
      sw.classList.add('selected');
      selectedColor = sw.dataset.color;
    });
    overlay.querySelector('#kid-daily').addEventListener('input', e => {
      overlay.querySelector('#daily-label').textContent = e.target.value + ' min';
    });
    overlay.querySelector('#btn-kid-cancel').addEventListener('click', _closeModal);
    overlay.querySelector('#btn-kid-save').addEventListener('click', async () => {
      const name = overlay.querySelector('#kid-name').value.trim();
      if (!name) { Notifications.toast('Please enter a name.', 'error'); return; }
      const pin  = overlay.querySelector('#kid-pin').value.trim();
      const daily    = parseInt(overlay.querySelector('#kid-daily').value) || 60;
      const required = parseInt(overlay.querySelector('#kid-required').value) || 0;
      const id = editId || `profile_kid_${Date.now()}`;
      await DB.upsert({ _id: id, type: 'profile', role: 'kid', name, avatar: selectedAvatar, color: selectedColor, dailyMinutes: daily, choresToUnlock: required, pin: pin || null });
      _closeModal();
      Notifications.toast(`${name} ${editId ? 'updated' : 'added'}! 🎉`, 'success');
      navigate('parent-settings');
    });
  }

  function _closeModal() {
    const overlay = document.getElementById('modal-overlay');
    overlay.innerHTML = '';
    overlay.classList.add('hidden');
  }

  return { init, navigate };
})();
