/* Profile authentication and login screen rendering */
const Auth = (() => {
  let _currentUser = null;

  function init() {
    const saved = sessionStorage.getItem('ft_user');
    if (saved) {
      try { _currentUser = JSON.parse(saved); } catch (e) { /* ignore corrupt data */ }
    }
  }

  function getCurrentUser() { return _currentUser; }
  function isParent() { return _currentUser && _currentUser.role === 'parent'; }
  function isKid() { return _currentUser && _currentUser.role === 'kid'; }

  async function login(profileId, pin) {
    const profile = await DB.getProfile(profileId);
    const ok = await DB.verifyPin(pin, profile.pin);
    if (!ok) return false;
    _currentUser = {
      id: profile._id,
      name: profile.name,
      role: profile.role,
      avatar: profile.avatar,
      color: profile.color
    };
    sessionStorage.setItem('ft_user', JSON.stringify(_currentUser));
    return true;
  }

  function logout() {
    _currentUser = null;
    sessionStorage.removeItem('ft_user');
  }

  async function renderLoginScreen() {
    const profiles = await DB.getAllProfiles();
    const parents = profiles.filter(p => p.role === 'parent');
    const kids = profiles.filter(p => p.role === 'kid');
    const ordered = [...parents, ...kids];

    const cards = ordered.map(p => `
      <div class="profile-card" tabindex="0"
           data-id="${p._id}"
           data-has-pin="${p.pin ? 'true' : 'false'}"
           data-role="${p.role}">
        <span class="profile-avatar">${p.avatar || '👤'}</span>
        <div class="profile-name">${p.name}</div>
        <div class="profile-role">${p.role === 'parent' ? 'Parent 🔑' : '⭐ Kid'}</div>
      </div>
    `).join('');

    return `
      <div class="login-screen">
        <div class="login-header">
          <div class="app-logo">🏠</div>
          <h1>FamilyTime</h1>
          <p>Who's using the device?</p>
        </div>
        <div class="profile-grid" id="profile-grid">
          ${cards}
          <div class="add-profile-card" id="btn-add-profile" tabindex="0">
            <span class="add-icon">➕</span>
            <div class="add-label">Add Kid</div>
          </div>
        </div>
      </div>
    `;
  }

  function renderPinScreen(profile) {
    return `
      <div class="pin-screen" id="pin-screen">
        <button class="pin-back" id="btn-pin-back" aria-label="Back">←</button>
        <div class="pin-avatar">${profile.avatar || '👤'}</div>
        <div class="pin-name">${profile.name}</div>
        <div class="pin-label">Enter your PIN</div>
        <div class="pin-dots" id="pin-dots">
          <div class="pin-dot" id="pin-dot-0"></div>
          <div class="pin-dot" id="pin-dot-1"></div>
          <div class="pin-dot" id="pin-dot-2"></div>
          <div class="pin-dot" id="pin-dot-3"></div>
        </div>
        <div class="pin-keypad">
          ${[1,2,3,4,5,6,7,8,9].map(n =>
            `<button class="pin-key" data-key="${n}">${n}</button>`
          ).join('')}
          <button class="pin-key empty" aria-hidden="true"></button>
          <button class="pin-key" data-key="0">0</button>
          <button class="pin-key delete" data-key="del" aria-label="Delete">⌫</button>
        </div>
        <div class="pin-error" id="pin-error">Wrong PIN. Try again.</div>
      </div>
    `;
  }

  return { init, getCurrentUser, isParent, isKid, login, logout, renderLoginScreen, renderPinScreen };
})();
