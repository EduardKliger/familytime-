#!/usr/bin/env python3
"""FamilyTime — static files + minimal CouchDB sync for PouchDB + REST API for native clients."""

import json, os, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote

ROOT     = os.path.dirname(os.path.abspath(__file__))
APP_DIR  = os.path.join(ROOT, 'app')
DATA_DIR = os.path.join(ROOT, 'sync-data')
DB_FILE  = os.path.join(DATA_DIR, 'familytime.json')

_lock = threading.Lock()
_MIME = {
    '.html': 'text/html; charset=utf-8',
    '.css':  'text/css',
    '.js':   'application/javascript',
    '.json': 'application/json',
    '.png':  'image/png',
    '.svg':  'image/svg+xml',
    '.ico':  'image/x-icon',
    '.webp': 'image/webp',
}

# ── persistence ───────────────────────────────────────────────────────────────

def _load():
    if os.path.exists(DB_FILE):
        with open(DB_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {'docs': {}, 'seq': 0, 'changes': [], 'checkpoints': {}}

def _save(db):
    os.makedirs(DATA_DIR, exist_ok=True)
    tmp = DB_FILE + '.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(db, f)
    os.replace(tmp, DB_FILE)  # atomic so a crash never corrupts the file

_db = _load()

def _store(doc):
    """Accept doc with last-write-wins on generation number. Returns stored rev."""
    doc_id = doc.get('_id')
    if not doc_id:
        return None
    incoming_rev = doc.get('_rev', '1-0')
    try:
        incoming_gen = int(incoming_rev.split('-')[0])
    except (ValueError, IndexError):
        incoming_gen = 1

    existing = _db['docs'].get(doc_id)
    existing_gen = 0
    if existing:
        try:
            existing_gen = int(existing.get('_rev', '0-').split('-')[0])
        except (ValueError, IndexError):
            existing_gen = 0

    if incoming_gen >= existing_gen:
        _db['docs'][doc_id] = doc
        _db['seq'] += 1
        _db['changes'].append({'seq': _db['seq'], 'id': doc_id, 'rev': incoming_rev})
        _save(_db)
        return incoming_rev
    return existing['_rev'] if existing else incoming_rev


# ── request handler ───────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):

    # ── routing ──────────────────────────────────────────

    def do_OPTIONS(self):
        self.send_response(200)
        self._cors()
        self.send_header('Content-Length', '0')
        self.end_headers()

    def do_GET(self):
        p    = urlparse(self.path)
        path = p.path
        qs   = parse_qs(p.query)

        # ── CouchDB sync ─────────────────────────────────

        if path.rstrip('/') == '/sync/familytime':
            with _lock:
                self._json(200, {'db_name': 'familytime', 'update_seq': _db['seq'],
                                 'doc_count': len(_db['docs']), 'instance_start_time': '0'})
            return

        if path == '/sync/familytime/_changes':
            raw_since = qs.get('since', ['0'])[0]
            # CouchDB seqs can be "N-hash"; extract the numeric part
            try:
                since = int(raw_since.split('-')[0])
            except ValueError:
                since = 0
            limit = int(qs.get('limit', ['999999'])[0])
            with _lock:
                # deduplicate: only latest change entry per doc
                seen = {}
                for c in _db['changes']:
                    if c['seq'] > since:
                        seen[c['id']] = c
                results = list(seen.values())[:limit]
                last_seq = _db['seq']
            self._json(200, {'results': results, 'last_seq': last_seq})
            return

        if path.startswith('/sync/familytime/_local/'):
            cp_id = unquote(path[len('/sync/familytime/_local/'):])
            with _lock:
                cp = _db['checkpoints'].get(cp_id)
            self._json(200 if cp else 404,
                       cp or {'error': 'not_found', 'reason': 'missing'})
            return

        # single-doc fetch (used by PouchDB in some edge cases)
        if path.startswith('/sync/familytime/'):
            doc_id = unquote(path[len('/sync/familytime/'):])
            with _lock:
                doc = _db['docs'].get(doc_id)
            self._json(200 if doc else 404,
                       doc or {'error': 'not_found', 'reason': 'missing'})
            return

        # ── REST API for Android / Windows clients ────────

        if path.startswith('/api/status/'):
            self._api_status(unquote(path[len('/api/status/'):]))
            return

        # ── static files ─────────────────────────────────

        self._static(path)

    def do_POST(self):
        path = urlparse(self.path).path.rstrip('/')
        body = self._body()

        if path == '/sync/familytime/_revs_diff':
            with _lock:
                result = {}
                for doc_id, revs in body.items():
                    stored     = _db['docs'].get(doc_id)
                    stored_rev = stored['_rev'] if stored else None
                    missing    = [r for r in revs if r != stored_rev]
                    if missing:
                        result[doc_id] = {'missing': missing}
            self._json(200, result)
            return

        if path == '/sync/familytime/_bulk_get':
            with _lock:
                results = []
                for item in body.get('docs', []):
                    doc = _db['docs'].get(item['id'])
                    if doc:
                        results.append({'id': item['id'], 'docs': [{'ok': doc}]})
                    else:
                        results.append({'id': item['id'], 'docs': [
                            {'error': {'id': item['id'], 'error': 'not_found', 'reason': 'missing'}}
                        ]})
            self._json(200, {'results': results})
            return

        if path == '/sync/familytime/_bulk_docs':
            resp = []
            with _lock:
                for doc in body.get('docs', []):
                    if '_id' in doc:
                        rev = _store(doc)
                        resp.append({'ok': True, 'id': doc['_id'], 'rev': rev})
            self._json(201, resp)
            return

        if path.startswith('/api/chores/') and path.endswith('/complete'):
            chore_id = unquote(path[len('/api/chores/'):-len('/complete')])
            self._api_chore_complete(chore_id)
            return

        self._json(404, {'error': 'not_found'})

    def do_PUT(self):
        path = urlparse(self.path).path.rstrip('/')
        body = self._body()

        if path.startswith('/sync/familytime/_local/'):
            cp_id = unquote(path[len('/sync/familytime/_local/'):])
            with _lock:
                body['_id'] = f'_local/{cp_id}'
                body.setdefault('_rev', '0-1')
                _db['checkpoints'][cp_id] = body
                _save(_db)
            self._json(201, {'ok': True, 'id': f'_local/{cp_id}', 'rev': '0-1'})
            return

        self._json(404, {'error': 'not_found'})

    # ── REST API handlers ─────────────────────────────────

    def _api_status(self, kid_id):
        with _lock:
            today    = time.strftime('%Y-%m-%d')
            st       = _db['docs'].get(f'screentime_{kid_id}_{today}', {})
            settings = _db['docs'].get('settings_global', {})
            profile  = _db['docs'].get(kid_id, {})
            chores   = [d for d in _db['docs'].values()
                        if d.get('type') == 'chore' and not d.get('isTemplate')
                        and d.get('assignedTo') == kid_id and d.get('date') == today]

        if settings.get('vacationMode'):
            self._json(200, {'blocked': False, 'reason': 'vacation'}); return

        earned    = (st.get('earnedMinutes', 0) + st.get('bonusMinutes', 0)) * 60
        used      = st.get('usedSeconds', 0)
        remaining = max(0, earned - used)
        locked    = st.get('locked', False)
        approved  = sum(1 for c in chores if c.get('status') == 'approved')
        required  = profile.get('choresToUnlock', 0)

        if locked or (earned > 0 and remaining == 0):
            self._json(200, {'blocked': True, 'reason': 'no_time', 'remaining': 0})
        elif approved < required:
            self._json(200, {'blocked': True, 'reason': 'chores',
                             'remaining': remaining, 'approved': approved, 'required': required})
        else:
            self._json(200, {'blocked': False, 'remaining': remaining})

    def _api_chore_complete(self, chore_id):
        with _lock:
            doc = _db['docs'].get(chore_id)
        if not doc:
            self._json(404, {'error': 'not_found'}); return
        doc['status']      = 'pending_approval'
        doc['completedAt'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        with _lock:
            _store(doc)
        self._json(200, {'ok': True})

    # ── low-level helpers ─────────────────────────────────

    def _body(self):
        n = int(self.headers.get('Content-Length', 0))
        return json.loads(self.rfile.read(n)) if n else {}

    def _cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type,Accept')

    def _json(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', len(body))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _static(self, path):
        if path in ('', '/'):
            path = '/index.html'
        fp = os.path.normpath(os.path.join(APP_DIR, path.lstrip('/')))
        # block path traversal outside APP_DIR
        if not fp.startswith(os.path.abspath(APP_DIR)):
            self.send_error(403); return
        if os.path.isdir(fp):
            fp = os.path.join(fp, 'index.html')
        if not os.path.isfile(fp):
            self.send_error(404); return
        ext = os.path.splitext(fp)[1].lower()
        ct  = _MIME.get(ext, 'application/octet-stream')
        with open(fp, 'rb') as f:
            data = f.read()
        self.send_response(200)
        self.send_header('Content-Type', ct)
        self.send_header('Content-Length', len(data))
        self._cors()
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        msg = fmt % args
        if '/sync/' in msg or '/api/' in msg:
            super().log_message(fmt, *args)


# ── entry point ───────────────────────────────────────────────────────────────

if __name__ == '__main__':
    os.makedirs(DATA_DIR, exist_ok=True)
    server = ThreadingHTTPServer(('', 3000), Handler)
    print('=' * 50)
    print('  FamilyTime Server  —  http://localhost:3000')
    print('  Other devices: http://YOUR-PC-IP:3000')
    print('  Press Ctrl+C to stop.')
    print('=' * 50)
    server.serve_forever()
