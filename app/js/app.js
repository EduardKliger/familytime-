/* App entry point — initialises DB, auth, and routes to first view */
(async function () {
  if (typeof PouchDB === 'undefined') {
    document.getElementById('app').innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:16px;padding:24px;text-align:center;background:#F0F2FF">
        <div style="font-size:64px">📶</div>
        <div style="font-size:22px;font-weight:700;color:#2D3748">Could not load the app</div>
        <div style="font-size:15px;color:#718096;max-width:320px;line-height:1.5">
          PouchDB failed to load from CDN. Check your internet connection, then refresh the page.
        </div>
        <button onclick="location.reload()" style="padding:14px 28px;background:#6C63FF;color:white;border:none;border-radius:14px;font-size:16px;font-weight:600;cursor:pointer">
          Retry
        </button>
      </div>
    `;
    return;
  }

  try {
    await DB.init();
    Auth.init();
    Router.init();

    const user = Auth.getCurrentUser();
    if (user) {
      Router.navigate(user.role === 'parent' ? 'parent-dashboard' : 'kid-home');
    } else {
      Router.navigate('login');
    }
  } catch (err) {
    console.error('App startup failed:', err);
    document.getElementById('app').innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:16px;padding:24px;text-align:center;background:#F0F2FF">
        <div style="font-size:64px">😵</div>
        <div style="font-size:22px;font-weight:700;color:#2D3748">Oops! Failed to start</div>
        <div style="font-size:13px;color:#999;font-family:monospace;background:#eee;padding:10px 16px;border-radius:8px;max-width:90%">${err.message}</div>
        <button onclick="location.reload()" style="padding:14px 28px;background:#6C63FF;color:white;border:none;border-radius:14px;font-size:16px;font-weight:600;cursor:pointer">
          Try Again
        </button>
      </div>
    `;
  }
})();
