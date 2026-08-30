"""
FamilyTime Windows Blocker — runs in the background on kids' Windows sessions.
Polls the server every 5 seconds and shows a fullscreen lock window when blocked.

Run once to set up: windows-agent\setup.bat
After setup it auto-starts with Windows login.
"""

import time
import json
import threading
import tkinter as tk
from urllib.request import urlopen
from urllib.error import URLError

SERVER_URL = "http://localhost:3000"   # edit this if kids use a different PC
KID_ID     = "profile_kid_amy"         # edit this per device
POLL_SECS  = 5

# ─────────────────────────────────────────────────────────────────────────────

def get_status():
    try:
        url  = f"{SERVER_URL}/api/status/{KID_ID}"
        resp = urlopen(url, timeout=4)
        return json.loads(resp.read())
    except (URLError, Exception):
        return None   # server unreachable — keep current lock state

# ─────────────────────────────────────────────────────────────────────────────

class LockWindow:
    """Fullscreen, always-on-top window the kid cannot dismiss."""

    def __init__(self):
        self.root   = None
        self.locked = False
        self._lock  = threading.Lock()

    def show(self, reason: str):
        with self._lock:
            if self.locked:
                return          # already showing
            self.locked = True
        threading.Thread(target=self._run, args=(reason,), daemon=True).start()

    def hide(self):
        with self._lock:
            if not self.locked:
                return
            self.locked = False
        if self.root:
            self.root.after(0, self._destroy)

    def _run(self, reason: str):
        self.root = tk.Tk()
        root = self.root

        root.title("FamilyTime")
        root.configure(bg="#1a1a2e")

        # fullscreen, always on top, no window decorations
        root.attributes("-fullscreen", True)
        root.attributes("-topmost",    True)
        root.overrideredirect(True)

        root.bind("<Key>",    lambda e: "break")   # swallow all keystrokes
        root.protocol("WM_DELETE_WINDOW", lambda: None)

        frame = tk.Frame(root, bg="#1a1a2e")
        frame.place(relx=0.5, rely=0.5, anchor="center")

        tk.Label(frame, text="🔒",       font=("Segoe UI Emoji", 80),
                 bg="#1a1a2e", fg="white").pack(pady=(0, 20))
        tk.Label(frame, text="FamilyTime",
                 font=("Segoe UI", 36, "bold"),
                 bg="#1a1a2e", fg="white").pack(pady=(0, 12))

        msg = self._reason_text(reason)
        tk.Label(frame, text=msg,
                 font=("Segoe UI", 22),
                 bg="#1a1a2e", fg="#E0E0FF",
                 wraplength=700, justify="center").pack(pady=(0, 40))

        tk.Label(frame, text="Ask a parent to unlock",
                 font=("Segoe UI", 16),
                 bg="#1a1a2e", fg="#8888AA").pack()

        root.mainloop()
        self.root = None

    def _destroy(self):
        if self.root:
            self.root.destroy()

    @staticmethod
    def _reason_text(reason: str) -> str:
        if reason == "chores":
            return "Do your chores first! 📋"
        if reason == "no_time":
            return "Screen time is up for today! ⏰"
        return "Screen time is locked 🔒"


# ─────────────────────────────────────────────────────────────────────────────

def main():
    window  = LockWindow()
    blocked = False   # last known state — keeps blocking if server goes offline

    print(f"FamilyTime blocker running for {KID_ID} → {SERVER_URL}")
    print("Press Ctrl+C to stop.")

    while True:
        status = get_status()

        if status is not None:
            now_blocked = status.get("blocked", False)
            reason      = status.get("reason", "")

            if now_blocked and not blocked:
                print(f"[lock]   reason={reason}")
                window.show(reason)
            elif not now_blocked and blocked:
                print("[unlock]")
                window.hide()

            blocked = now_blocked
        # if status is None (server offline), keep current blocked state unchanged

        time.sleep(POLL_SECS)


if __name__ == "__main__":
    main()
