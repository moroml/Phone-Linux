package com.termux.app.phonelinux;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTP/1.1 server on a filesystem Unix domain socket inside the app's private files dir (reachable only by
 * this app's uid), feeding requests to a {@link PhoneApiHandler}. One request per connection.
 */
public final class PhoneApiServer {

    private static final long MAX_LOG_BYTES = 1024 * 1024;

    private final File socketFile;
    private final PhoneApiHandler handler;
    private final ExecutorService pool = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private volatile LocalServerSocket server;
    private volatile LocalSocket bound;

    public PhoneApiServer(File socketFile, PhoneApiHandler handler) {
        this.socketFile = socketFile;
        this.handler = handler;
    }

    public void start() throws IOException {
        File dir = socketFile.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        try {
            Os.chmod(dir.getPath(), 0700);
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
        //noinspection ResultOfMethodCallIgnored
        socketFile.delete(); // stale socket from a previous run
        LocalSocket ls = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            ls.bind(new LocalSocketAddress(socketFile.getPath(), LocalSocketAddress.Namespace.FILESYSTEM));
            Os.chmod(socketFile.getPath(), 0600);
            LocalServerSocket ss = new LocalServerSocket(ls.getFileDescriptor());
            bound = ls;
            server = ss;
            Thread t = new Thread(() -> acceptLoop(ss), "phone-api-accept");
            t.setDaemon(true);
            t.start();
        } catch (IOException | ErrnoException | RuntimeException e) {
            closeQuietly(ls);
            //noinspection ResultOfMethodCallIgnored
            socketFile.delete();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    public void stop() {
        LocalServerSocket ss = server;
        LocalSocket ls = bound;
        server = null;
        bound = null;
        pool.shutdownNow();
        if (ss != null) try {
            ss.close();
        } catch (IOException ignored) {
        }
        closeQuietly(ls);
        //noinspection ResultOfMethodCallIgnored
        socketFile.delete();
    }

    private static void closeQuietly(LocalSocket s) {
        if (s != null) try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop(LocalServerSocket ss) {
        while (server == ss) {
            try {
                LocalSocket s = ss.accept();
                try {
                    pool.execute(() -> serve(s));
                } catch (RuntimeException busy) {
                    s.close();
                }
            } catch (IOException e) {
                if (server != ss) return;
            }
        }
    }

    private void serve(LocalSocket s) {
        try (LocalSocket sock = s) {
            if (sock.getPeerCredentials().getUid() != Process.myUid()) return;
            sock.setSoTimeout(10_000);
            serve(handler, sock.getInputStream(), sock.getOutputStream());
        } catch (IOException ignored) {
        }
    }

    /** Reads one request from in, answers on out. Pure stream logic, unit tested. */
    static void serve(PhoneApiHandler handler, InputStream in, OutputStream out) throws IOException {
        PhoneApiHandler.Response r;
        try {
            r = handler.handle(PhoneApiHandler.parse(in));
        } catch (PhoneApiHandler.BadRequest e) {
            byte[] body = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            r = new PhoneApiHandler.Response(e.status, "application/json; charset=utf-8", body);
        }
        out.write(r.toBytes());
        out.flush();
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
