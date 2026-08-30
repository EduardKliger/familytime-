/* Screen time timer engine — earn, count down, lock, grant bonus */
const ScreenTime = (() => {
  let _timer = null;
  let _currentKidId = null;

  async function getTodayData(kidId) {
    return await DB.getTodayScreenTime(kidId);
  }

  async function getRemainingSeconds(kidId) {
    const st = await getTodayData(kidId);
    const totalMinutes = (st.earnedMinutes || 0) + (st.bonusMinutes || 0);
    return Math.max(0, totalMinutes * 60 - (st.usedSeconds || 0));
  }

  async function isEligible(kidId) {
    const st = await getTodayData(kidId);
    if (st.locked) return false;
    const chores = await DB.getTodayChores(kidId);
    const profile = await DB.getProfile(kidId);
    const settings = await DB.getSettings();
    if (!settings.vacationMode) {
      const approved = chores.filter(c => c.status === 'approved').length;
      const required = profile.choresToUnlock || 0;
      if (approved < required) return false;
    }
    return (await getRemainingSeconds(kidId)) > 0;
  }

  async function start(kidId) {
    if (_timer) await stop(true);
    _currentKidId = kidId;
    const st = await DB.getTodayScreenTime(kidId);
    st.active = true;
    st.sessionStart = Date.now();
    await DB.upsert(st);
    _timer = setInterval(_tick, 1000);
    document.addEventListener('visibilitychange', _onVisibilityChange);
  }

  async function stop(save = true) {
    if (_timer) { clearInterval(_timer); _timer = null; }
    document.removeEventListener('visibilitychange', _onVisibilityChange);
    if (save && _currentKidId) {
      try {
        const st = await DB.getTodayScreenTime(_currentKidId);
        if (st.active && st.sessionStart) {
          st.usedSeconds = (st.usedSeconds || 0) + Math.floor((Date.now() - st.sessionStart) / 1000);
          st.active = false;
          st.sessionStart = null;
          await DB.upsert(st);
        }
      } catch (e) { /* ignore if profile deleted */ }
    }
    _currentKidId = null;
  }

  async function _tick() {
    if (!_currentKidId) return;
    const remaining = await getRemainingSeconds(_currentKidId);
    const display = document.getElementById('timer-countdown');
    if (display) display.textContent = formatTime(remaining);
    if (remaining <= 0) {
      await stop(true);
      Router.navigate('kid-locked');
      return;
    }
    if (remaining === 300) Notifications.toast('⏰ 5 minutes left!', 'warning', 5000);
  }

  async function _onVisibilityChange() {
    if (document.hidden && _timer) await stop(true);
  }

  async function grantBonus(kidId, minutes) {
    const st = await DB.getTodayScreenTime(kidId);
    st.bonusMinutes = (st.bonusMinutes || 0) + minutes;
    if (st.locked) st.locked = false;
    await DB.upsert(st);
    const profile = await DB.getProfile(kidId);
    Notifications.toast(`+${minutes} min added for ${profile.name}! 🎉`, 'success');
  }

  async function pause(kidId) {
    if (_currentKidId === kidId && _timer) await stop(true);
  }

  function formatTime(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  return { getTodayData, getRemainingSeconds, isEligible, start, stop, grantBonus, pause, formatTime };
})();
