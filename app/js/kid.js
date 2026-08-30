/* Kid home, active timer, and lock screen views */
const Kid = (() => {

  async function renderHome() {
    const user = Auth.getCurrentUser();
    const profile = await DB.getProfile(user.id);
    const chores = await DB.getTodayChores(user.id);
    const st = await DB.getTodayScreenTime(user.id);
    const settings = await DB.getSettings();
    const eligible = await ScreenTime.isEligible(user.id);
    const remaining = await ScreenTime.getRemainingSeconds(user.id);
    const approved = chores.filter(c => c.status === 'approved').length;
    const total = chores.length;
    const required = settings.vacationMode ? 0 : (profile.choresToUnlock || 0);
    const choreHtml = await Chores.renderChoreList(user.id);
    const color = user.color || '#6C63FF';

    let stSection = '';
    if (eligible) {
      stSection = `
        <div class="screentime-banner" style="background:linear-gradient(135deg,${color},${color}bb)">
          <div class="st-label">⏱️ Screen Time Available</div>
          <div class="st-time">${ScreenTime.formatTime(remaining)}</div>
          <button class="btn btn-lg btn-full" id="btn-start-screentime"
                  style="background:rgba(255,255,255,0.22);color:white;border:2px solid rgba(255,255,255,0.4);margin-top:18px">
            ▶ Start Screen Time
          </button>
        </div>
      `;
    } else if ((st.earnedMinutes + st.bonusMinutes) > 0 && remaining === 0) {
      stSection = `
        <div class="screentime-banner" style="background:linear-gradient(135deg,#2D3748,#4A5568)">
          <div class="st-label">🌙 Screen time is over for today</div>
          <div class="st-status">Great job! You did ${approved} chore${approved !== 1 ? 's' : ''} today 🌟</div>
          <button class="btn btn-ghost btn-sm" id="btn-request-time"
                  style="color:white;border-color:rgba(255,255,255,0.3);margin-top:14px">
            Ask parent for more time
          </button>
        </div>
      `;
    } else {
      const stillNeeded = required - approved;
      stSection = `
        <div class="screentime-banner" style="background:linear-gradient(135deg,#4A5568,#2D3748)">
          <div class="st-label">🔒 Screen Time Locked</div>
          <div class="st-status">${stillNeeded > 0
            ? `Complete ${stillNeeded} more chore${stillNeeded !== 1 ? 's' : ''} to unlock`
            : 'No screen time earned yet. Complete chores!'}</div>
        </div>
      `;
    }

    return `
      <div class="app-header" style="background:${color};border-bottom:none;box-shadow:none">
        <span class="header-title" style="color:white">Hi ${user.name}! ${profile.avatar}</span>
        <button class="btn-icon" style="color:white" id="btn-kid-logout" title="Switch user">🚪</button>
      </div>
      <div class="page" style="padding-top:16px">
        ${stSection}
        <div class="section-header">
          <span class="section-title">Today's Chores</span>
          <span style="font-size:13px;color:var(--text-muted)">${approved}/${total} done</span>
        </div>
        ${choreHtml}
      </div>
    `;
  }

  function renderTimer(remaining) {
    return `
      <div class="timer-screen">
        <div class="timer-emoji">🎮</div>
        <div style="font-size:16px;opacity:0.65;letter-spacing:1px;text-transform:uppercase">Screen Time</div>
        <div class="timer-display" id="timer-countdown">${ScreenTime.formatTime(remaining)}</div>
        <div class="timer-label">remaining today</div>
        <div class="timer-actions">
          <button class="btn btn-ghost" id="btn-stop-timer"
                  style="color:white;border-color:rgba(255,255,255,0.3)">
            ⏸ Pause
          </button>
          <button class="btn btn-ghost" id="btn-request-time-timer"
                  style="color:white;border-color:rgba(255,255,255,0.3)">
            ⏰ Ask for more
          </button>
        </div>
      </div>
    `;
  }

  function renderLocked() {
    return `
      <div class="lock-screen">
        <div class="lock-icon">🌙</div>
        <div class="lock-title">Screen time is over!</div>
        <div class="lock-msg">You did a great job today!<br>Come back tomorrow for more. 🌟</div>
        <button class="btn btn-ghost" id="btn-request-time-lock"
                style="color:white;border-color:rgba(255,255,255,0.3)">
          Ask parent for more time
        </button>
        <button class="btn btn-ghost btn-sm" id="btn-kid-logout-lock"
                style="color:rgba(255,255,255,0.45);border:none;margin-top:16px">
          Switch User
        </button>
      </div>
    `;
  }

  return { renderHome, renderTimer, renderLocked };
})();
