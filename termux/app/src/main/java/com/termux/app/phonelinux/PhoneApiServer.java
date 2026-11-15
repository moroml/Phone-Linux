package com.termux.app.phonelinux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Minimal HTTP/1.1 server on 127.0.0.1 that feeds requests to a {@link PhoneApiHandler}. One request per connection. */
public final class PhoneApiServer {

    public static final int DEFAULT_PORT = 18765;
    private static final long MAX_LOG_BYTES = 1024 * 1024;

    private final int port;
    private final PhoneApiHandler handler;
    private final ExecutorService pool = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private volatile ServerSocket server;

    public PhoneApiServer(int port, PhoneApiHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    /** Port actually bound (differs from the requested one when that was 0). */
    public int start() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port));
        server = ss;
        Thread t = new Thread(() -> acceptLoop(ss), "phone-api-accept");
        t.setDaemon(true);
        t.start();
        return ss.getLocalPort();
    }

    public void stop() {
        ServerSocket ss = server;
        server = null;
        pool.shutdownNow();
        if (ss != null) try {
            ss.close();
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                Socket s = ss.accept();
                try {
                    pool.execute(() -> serve(s));
                } catch (RuntimeException busy) {
                    s.close();
                }
            } catch (IOException e) {
                if (ss.isClosed()) return;
            }
        }
    }

    private void serve(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(10_000);
            PhoneApiHandler.Response r;
            try {
                r = handler.handle(PhoneApiHandler.parse(sock.getInputStream()));
            } catch (PhoneApiHandler.BadRequest e) {
                byte[] body = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                r = new PhoneApiHandler.Response(e.status, "application/json; charset=utf-8", body);
            }
            OutputStream out = sock.getOutputStream();
            out.write(r.toBytes());
            out.flush();
        } catch (IOException ignored) {
        }
    }

    /** Appends lines to a file, keeping one rotated copy (.1) once it passes 1 MB. */
    public static PhoneApiHandler.Log fileLog(File file) {
        return line -> {
            synchronized (file) {
                try {
                    File dir = file.getParentFile();
                    if (dir != null) dir.mkdirs();
                    if (file.length() > MAX_LOG_BYTES) file.renameTo(new File(file.getPath() + ".1"));
                    try (FileOutputStream out = new FileOutputStream(file, true)) {
                        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException ignored) {
                }
            }
        };
    }
}
