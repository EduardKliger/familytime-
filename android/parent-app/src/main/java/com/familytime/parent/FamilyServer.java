package com.familytime.parent;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Embedded HTTP server + mDNS. Serves kid devices; parent activities use static helpers directly. */
public class FamilyServer {
    private static final String TAG = "FamilyServer";
    static final int    PORT         = 3000;
    static final String SERVICE_TYPE = "_familytime._tcp.";
    static final String PREFS        = "familytime_rules";
    static final String[] KIDS       = {
        "profile_kid_amy", "profile_kid_guy", "profile_kid_mia"
    };

    private final Context ctx;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private NsdManager.RegistrationListener nsdListener;

    FamilyServer(Context ctx) { this.ctx = ctx; }

    void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        running = true;
        new Thread(this::acceptLoop, "FamilyServer").start();
        registerNsd();
    }

    void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        if (nsd != null && nsdListener != null) {
            try { nsd.unregisterService(nsdListener); } catch (Exception ignored) {}
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket client = serverSocket.accept();
                new Thread(() -> handle(client), "FamilyClient").start();
            } catch (IOException e) {
                if (running) Log.e(TAG, "accept", e);
            }
        }
    }

    private void handle(Socket socket) {
        try {
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream   out = socket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null) return;

            int contentLen = 0;
            String hdrLine;
            while ((hdrLine = in.readLine()) != null && !hdrLine.isEmpty()) {
                if (hdrLine.toLowerCase().startsWith("content-length:"))
                    contentLen = Integer.parseInt(hdrLine.split(":")[1].trim());
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], path = parts[1];
            String cors = "Access-Control-Allow-Origin: *\r\n";
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String[] seg = path.split("/"); // ["","api",endpoint,kid,...]

            if ("OPTIONS".equals(method)) {
                write(out, "HTTP/1.1 204 No Content\r\n" + cors + "\r\n");

            } else if ("GET".equals(method) && path.startsWith("/api/status/") && seg.length >= 4) {
                String kid = seg[3];
                ensureDayChores(prefs, kid);
                tickTimer(prefs, kid);
                int remaining = getRemaining(prefs, kid);
                boolean blocked = remaining <= 0;
                prefs.edit().putBoolean(kid + "_blocked", blocked).apply();
                int pending = countPendingApproval(prefs, kid);
                json(out, cors, "{\"blocked\":" + blocked
                    + ",\"remainingSeconds\":" + remaining
                    + ",\"choresPending\":" + pending + "}");

            } else if ("GET".equals(method) && path.startsWith("/api/chores/") && seg.length == 4) {
                String kid = seg[3];
                ensureDayChores(prefs, kid);
                json(out, cors, getChores(prefs, kid).toString());

            } else if ("POST".equals(method) && path.startsWith("/api/chores/") && path.endsWith("/done") && seg.length >= 5) {
                String kid = seg[3], id = seg[4];
                markChoreDone(prefs, kid, id);
                json(out, cors, "{\"ok\":true}");

            } else if ("POST".equals(method) && path.startsWith("/api/time/start/") && seg.length >= 5) {
                String kid = seg[4];
                tickTimer(prefs, kid);
                int rem = getRemaining(prefs, kid);
                if (rem > 0) prefs.edit().putLong(kid + "_timer_start", System.currentTimeMillis()).apply();
                json(out, cors, "{\"ok\":true,\"remainingSeconds\":" + rem + "}");

            } else if ("POST".equals(method) && path.startsWith("/api/time/stop/") && seg.length >= 5) {
                String kid = seg[4];
                tickTimer(prefs, kid);
                prefs.edit().putLong(kid + "_timer_start", 0).apply();
                json(out, cors, "{\"ok\":true,\"remainingSeconds\":" + getRemaining(prefs, kid) + "}");

            } else {
                write(out, "HTTP/1.1 404 Not Found\r\n" + cors + "Content-Length: 0\r\n\r\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "handle", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Timer — ticked on every poll so no separate background thread needed ──

    static void tickTimer(SharedPreferences prefs, String kid) {
        long start = prefs.getLong(kid + "_timer_start", 0);
        if (start == 0) return;
        long elapsed = (System.currentTimeMillis() - start) / 1000;
        if (elapsed <= 0) return;
        String date = today();
        int rem = Math.max(0, prefs.getInt(kid + "_remaining_" + date,
            getDefaultBudget(prefs, kid)) - (int) elapsed);
        prefs.edit()
            .putInt(kid + "_remaining_" + date, rem)
            .putLong(kid + "_timer_start", rem > 0 ? System.currentTimeMillis() : 0)
            .apply();
    }

    static int getRemaining(SharedPreferences prefs, String kid) {
        return prefs.getInt(kid + "_remaining_" + today(), getDefaultBudget(prefs, kid));
    }

    static int getDefaultBudget(SharedPreferences prefs, String kid) {
        return prefs.getInt(kid + "_budget", 60) * 60;
    }

    static void addTime(SharedPreferences prefs, String kid, int seconds) {
        prefs.edit().putInt(kid + "_remaining_" + today(), getRemaining(prefs, kid) + seconds).apply();
    }

    static boolean isTimerRunning(SharedPreferences prefs, String kid) {
        return prefs.getLong(kid + "_timer_start", 0) > 0;
    }

    // ── Chores ────────────────────────────────────────────────────────────────

    static void ensureDayChores(SharedPreferences prefs, String kid) {
        String key = "chores_" + kid + "_" + today();
        if (prefs.contains(key)) return;
        JSONArray templates = getTemplates(prefs);
        JSONArray instances  = new JSONArray();
        try {
            for (int i = 0; i < templates.length(); i++) {
                JSONObject t = templates.getJSONObject(i);
                String assignedTo = t.optString("assignedTo", "all");
                if ("all".equals(assignedTo) || kid.equals(assignedTo)) {
                    JSONObject inst = new JSONObject();
                    inst.put("id", t.getString("id"));
                    inst.put("title", t.getString("title"));
                    inst.put("icon", t.optString("icon", "✅"));
                    inst.put("rewardMinutes", t.optInt("rewardMinutes", 15));
                    inst.put("status", "pending");
                    instances.put(inst);
                }
            }
        } catch (Exception e) { Log.e(TAG, "ensureDayChores", e); }
        prefs.edit().putString(key, instances.toString()).apply();
    }

    static void regenerateDayChores(SharedPreferences prefs) {
        String date = today();
        SharedPreferences.Editor ed = prefs.edit();
        for (String kid : KIDS) ed.remove("chores_" + kid + "_" + date);
        ed.apply();
    }

    static JSONArray getChores(SharedPreferences prefs, String kid) {
        try { return new JSONArray(prefs.getString("chores_" + kid + "_" + today(), "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    static void saveChores(SharedPreferences prefs, String kid, JSONArray chores) {
        prefs.edit().putString("chores_" + kid + "_" + today(), chores.toString()).apply();
    }

    static void markChoreDone(SharedPreferences prefs, String kid, String id) {
        JSONArray chores = getChores(prefs, kid);
        try {
            for (int i = 0; i < chores.length(); i++) {
                JSONObject c = chores.getJSONObject(i);
                if (id.equals(c.getString("id"))) { c.put("status", "done_pending"); break; }
            }
        } catch (Exception e) { Log.e(TAG, "markChoreDone", e); }
        saveChores(prefs, kid, chores);
    }

    static void approveChore(SharedPreferences prefs, String kid, String id) {
        JSONArray chores = getChores(prefs, kid);
        try {
            for (int i = 0; i < chores.length(); i++) {
                JSONObject c = chores.getJSONObject(i);
                if (id.equals(c.getString("id"))) {
                    c.put("status", "approved");
                    addTime(prefs, kid, c.optInt("rewardMinutes", 15) * 60);
                    break;
                }
            }
        } catch (Exception e) { Log.e(TAG, "approveChore", e); }
        saveChores(prefs, kid, chores);
    }

    static int countPendingApproval(SharedPreferences prefs, String kid) {
        JSONArray chores = getChores(prefs, kid);
        int count = 0;
        try {
            for (int i = 0; i < chores.length(); i++)
                if ("done_pending".equals(chores.getJSONObject(i).optString("status"))) count++;
        } catch (Exception ignored) {}
        return count;
    }

    static JSONArray getTemplates(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString("chore_templates", defaultTemplates())); }
        catch (Exception e) { return new JSONArray(); }
    }

    static void saveTemplates(SharedPreferences prefs, JSONArray templates) {
        prefs.edit().putString("chore_templates", templates.toString()).apply();
    }

    private static String defaultTemplates() {
        return "[" +
            "{\"id\":\"t_dishes\",\"title\":\"Wash Dishes\",\"icon\":\"\uD83C\uDF7D\uFE0F\",\"assignedTo\":\"all\",\"rewardMinutes\":20}," +
            "{\"id\":\"t_garbage\",\"title\":\"Take Out Garbage\",\"icon\":\"\uD83D\uDDD1\uFE0F\",\"assignedTo\":\"all\",\"rewardMinutes\":15}," +
            "{\"id\":\"t_room\",\"title\":\"Clean Room\",\"icon\":\"\uD83E\uDDF9\",\"assignedTo\":\"all\",\"rewardMinutes\":25}" +
            "]";
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private void json(OutputStream out, String extra, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        write(out, "HTTP/1.1 200 OK\r\n" + extra
            + "Content-Type: application/json\r\nContent-Length: " + b.length + "\r\n\r\n");
        out.write(b);
        out.flush();
    }

    private void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void registerNsd() {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("FamilyTime");
        info.setServiceType(SERVICE_TYPE);
        info.setPort(PORT);
        nsdListener = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo i, int e) { Log.e(TAG, "NSD reg failed: " + e); }
            @Override public void onUnregistrationFailed(NsdServiceInfo i, int e) {}
            @Override public void onServiceRegistered(NsdServiceInfo i)   { Log.d(TAG, "NSD up"); }
            @Override public void onServiceUnregistered(NsdServiceInfo i) {}
        };
        NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        if (nsd != null) nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
    }
}
