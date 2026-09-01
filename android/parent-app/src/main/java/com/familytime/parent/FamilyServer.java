package com.familytime.parent;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Embedded HTTP server + mDNS so kid devices auto-discover this phone on WiFi. */
public class FamilyServer {
    private static final String TAG = "FamilyServer";
    static final int    PORT         = 3000;
    static final String SERVICE_TYPE = "_familytime._tcp.";
    static final String PREFS        = "familytime_rules";

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

            // drain headers, capture Content-Length
            int contentLen = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:"))
                    contentLen = Integer.parseInt(line.split(":")[1].trim());
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], path = parts[1];

            String cors  = "Access-Control-Allow-Origin: *\r\n";
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            if ("OPTIONS".equals(method)) {
                write(out, "HTTP/1.1 204 No Content\r\n" + cors + "\r\n");
            } else if ("GET".equals(method) && path.startsWith("/api/status/")) {
                String kid     = path.substring(12);
                boolean blocked = prefs.getBoolean(kid + "_blocked", false);
                String  reason  = prefs.getString(kid + "_reason",  "");
                json(out, cors, "{\"blocked\":" + blocked + ",\"reason\":\"" + reason + "\"}");
            } else if ("POST".equals(method) && path.startsWith("/api/block/")) {
                String kid  = path.substring(11);
                String body = readBody(in, contentLen);
                prefs.edit()
                    .putBoolean(kid + "_blocked", true)
                    .putString(kid  + "_reason",  body.isEmpty() ? "chores" : body)
                    .apply();
                json(out, cors, "{\"ok\":true}");
            } else if ("POST".equals(method) && path.startsWith("/api/unblock/")) {
                String kid = path.substring(13);
                prefs.edit().putBoolean(kid + "_blocked", false)
                    .putString(kid + "_reason", "").apply();
                json(out, cors, "{\"ok\":true}");
            } else {
                write(out, "HTTP/1.1 404 Not Found\r\n" + cors + "Content-Length: 0\r\n\r\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "handle", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

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

    private String readBody(BufferedReader in, int len) throws IOException {
        if (len <= 0) return "";
        char[] buf = new char[len];
        in.read(buf, 0, len);
        return new String(buf).trim();
    }

    private void registerNsd() {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("FamilyTime");
        info.setServiceType(SERVICE_TYPE);
        info.setPort(PORT);
        nsdListener = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo i, int e) { Log.e(TAG, "NSD reg failed: " + e); }
            @Override public void onUnregistrationFailed(NsdServiceInfo i, int e) {}
            @Override public void onServiceRegistered(NsdServiceInfo i)   { Log.d(TAG, "NSD up: " + i.getServiceName()); }
            @Override public void onServiceUnregistered(NsdServiceInfo i) {}
        };
        NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        if (nsd != null) nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
    }
}
