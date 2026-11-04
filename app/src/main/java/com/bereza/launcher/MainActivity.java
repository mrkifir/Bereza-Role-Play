package com.bereza.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {

    // ================== НАЛАШТУВАННЯ СЕРВЕРА ==================
    static final String SERVER_IP = "188.127.241.74";
    static final int SERVER_PORT = 2406;
    static final String CACHE_URL =
            "https://www.dropbox.com/scl/fi/h8ne4u9mn3u45x8h015z9/cache.zip?rlkey=v55ae68s0egn9zqgyu8cy4bab&st=ej74439p&dl=1";
    /** Пряме посилання на APK клієнта (необов'язково). Порожнє = шукати APK у cache.zip або обрати вручну. */
    static final String CLIENT_APK_URL = "";
    static final String GAME_PKG = "com.rockstargames.gtasa";
    static final String SETTINGS_INI = "SAMP/settings.ini";
    // ==========================================================

    private static final int RC_PERM = 1, RC_TREE = 2, RC_PICK_APK = 3, RC_INSTALL = 4;
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_\\[\\]().$@=]{3,20}$");

    private EditText nick;
    private TextView status;
    private ProgressBar progress;
    private Button main;
    private SharedPreferences prefs;
    private Storage storage;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        nick = findViewById(R.id.nick);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        main = findViewById(R.id.main);
        ((TextView) findViewById(R.id.server)).setText(SERVER_IP + ":" + SERVER_PORT);
        nick.setText(prefs.getString("nick", ""));

        main.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onMain(); }
        });
        findViewById(R.id.extra).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickApk(); }
        });
        findViewById(R.id.redownload).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (busy) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Скачати й розпакувати кеш заново?")
                        .setPositiveButton("Так", (d, w) -> {
                            prefs.edit().putBoolean("cache_done", false).apply();
                            refresh(true);
                        })
                        .setNegativeButton("Ні", null).show();
            }
        });

        if (Build.VERSION.SDK_INT <= 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, RC_PERM);
        } else {
            refresh(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && !busy && storage != null) refresh(false);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        refresh(true);
    }

    // ---------------- Стан ----------------

    private void openStorage() {
        if (storage != null) return;
        File root = new File(Environment.getExternalStorageDirectory(), "Android/data/" + GAME_PKG + "/files");
        storage = Storage.Files.tryOpen(root);
        if (storage != null) return;
        String tree = prefs.getString("tree", null);
        if (tree != null) {
            try {
                storage = Storage.Saf.fromTree(this, Uri.parse(tree));
            } catch (Exception e) {
                prefs.edit().remove("tree").apply();
            }
        }
    }

    private boolean gameInstalled() {
        try {
            getPackageManager().getPackageInfo(GAME_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private File apkDir() {
        File d = new File(getExternalFilesDir(null), "apk");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private File clientApk() {
        File[] l = apkDir().listFiles();
        if (l != null) for (File f : l) if (f.getName().toLowerCase(Locale.ROOT).endsWith(".apk") && f.length() > 0) return f;
        return null;
    }

    /** Оновлює кнопку; autoStart — одразу запускати завантаження кешу. */
    private void refresh(boolean autoStart) {
        if (busy) return;
        progress.setVisibility(View.GONE);
        main.setEnabled(true);
        openStorage();
        if (storage == null) {
            status.setText("Потрібен доступ до папки гри:\nAndroid/data/" + GAME_PKG + "/files\n\n"
                    + "Натисни кнопку і в провіднику обери папку Android/data → «Використати цю папку» → «Дозволити».");
            main.setText("Дати доступ до папки");
            return;
        }
        if (!prefs.getBoolean("cache_done", false)) {
            status.setText("Кеш гри ще не встановлено.");
            main.setText("Завантажити кеш");
            if (autoStart) startCache();
            return;
        }
        if (!gameInstalled()) {
            File apk = clientApk();
            if (apk != null) {
                status.setText("Кеш встановлено. Тепер встанови клієнт SA-MP.");
                main.setText("Встановити клієнт");
                if (autoStart || !prefs.getBoolean("asked_install", false)) {
                    prefs.edit().putBoolean("asked_install", true).apply();
                    offerInstall(apk);
                }
            } else if (!CLIENT_APK_URL.isEmpty()) {
                status.setText("Кеш встановлено. Потрібно завантажити клієнт SA-MP.");
                main.setText("Завантажити клієнт");
            } else {
                status.setText("Кеш встановлено, але клієнт SA-MP (" + GAME_PKG + ") не встановлено.\n"
                        + "У cache.zip не знайдено APK. Обери APK клієнта вручну.");
                main.setText("Обрати APK клієнта");
            }
            return;
        }
        status.setText("Все готово. Введи нік і грай!");
        main.setText("Грати");
    }

    private void onMain() {
        if (busy) return;
        if (storage == null) { requestTree(); return; }
        if (!prefs.getBoolean("cache_done", false)) { startCache(); return; }
        if (!gameInstalled()) {
            File apk = clientApk();
            if (apk != null) installApk(apk);
            else if (!CLIENT_APK_URL.isEmpty()) startClientDownload();
            else pickApk();
            return;
        }
        play();
    }

    // ---------------- Доступ через SAF ----------------

    private void requestTree() {
        if (Build.VERSION.SDK_INT >= 33) {
            new AlertDialog.Builder(this)
                    .setTitle("Android 13+")
                    .setMessage("На Android 13 і новіших система може не дозволити обрати папку Android/data. "
                            + "Якщо провідник напише «Не можна використати цю папку» — кеш доведеться покласти вручну "
                            + "через ПК або файловий менеджер з доступом до Android/data (наприклад, через Shizuku).")
                    .setPositiveButton("Спробувати", (d, w) -> openTreePicker())
                    .setNegativeButton("Скасувати", null).show();
        } else {
            openTreePicker();
        }
    }

    private void openTreePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri init = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/data");
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, init);
        try {
            startActivityForResult(i, RC_TREE);
        } catch (Exception e) {
            toast("Провідник файлів недоступний");
        }
    }

    // ---------------- Кеш ----------------

    private void startCache() {
        setBusy("Підключення…");
        new Thread(() -> {
            PowerManager.WakeLock wl = ((PowerManager) getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bereza:cache");
            wl.acquire(3 * 60 * 60 * 1000L);
            try {
                storage.ensureDir("");
                File zip = new File(getExternalFilesDir(null), "cache.zip");
                download(CACHE_URL, zip, "Завантаження кешу");
                extract(zip);
                //noinspection ResultOfMethodCallIgnored
                zip.delete();
                prefs.edit().putBoolean("cache_done", true).putBoolean("asked_install", false).apply();
                done(null);
            } catch (Exception e) {
                done("Помилка: " + e.getMessage() + "\nНатисни кнопку, щоб продовжити (завантаження докачається).");
            } finally {
                if (wl.isHeld()) wl.release();
            }
        }).start();
    }

    private void startClientDownload() {
        setBusy("Підключення…");
        new Thread(() -> {
            try {
                File f = new File(apkDir(), "client.apk");
                download(CLIENT_APK_URL, f, "Завантаження клієнта");
                done(null);
            } catch (Exception e) {
                done("Помилка: " + e.getMessage());
            }
        }).start();
    }

    /** Завантаження з докачкою. */
    private void download(String url, File out, String label) throws IOException {
        File part = new File(out.getPath() + ".part");
        if (out.exists() && !part.exists()) {
            // файл уже повністю завантажений раніше
            return;
        }
        for (int attempt = 0; ; attempt++) {
            long have = part.exists() ? part.length() : 0;
            HttpURLConnection c = null;
            try {
                c = open(url);
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                int code = c.getResponseCode();
                if (code == 416) { break; }
                if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                if (code == 200) have = 0;
                long len = c.getContentLengthLong();
                long total = len > 0 ? have + len : -1;
                InputStream in = c.getInputStream();
                RandomAccessFile raf = new RandomAccessFile(part, "rw");
                raf.setLength(have);
                raf.seek(have);
                byte[] buf = new byte[64 * 1024];
                int n;
                long done = have, lastUi = 0, lastBytes = done, lastT = System.currentTimeMillis();
                double speed = 0;
                try {
                    while ((n = in.read(buf)) > 0) {
                        raf.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastUi > 300) {
                            if (now - lastT >= 1000) {
                                speed = (done - lastBytes) * 1000.0 / (now - lastT);
                                lastBytes = done;
                                lastT = now;
                            }
                            lastUi = now;
                            postProgress(label, done, total, speed);
                        }
                    }
                } finally {
                    raf.close();
                    in.close();
                }
                if (total > 0 && done < total) throw new IOException("Зв'язок обірвався");
                break;
            } catch (IOException e) {
                if (attempt >= 5) throw e;
                final int a = attempt + 1;
                runOnUiThread(() -> status.setText("Зв'язок обірвався, повтор " + a + "/5…"));
                try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        if (out.exists()) //noinspection ResultOfMethodCallIgnored
            out.delete();
        if (!part.renameTo(out)) throw new IOException("Не вдалося зберегти файл");
    }

    private HttpURLConnection open(String url) throws IOException {
        String u = url;
        for (int i = 0; i < 8; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BerezaLauncher");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Порожнє перенаправлення");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            c.disconnect();
            HttpURLConnection r = (HttpURLConnection) new URL(u).openConnection();
            r.setConnectTimeout(20000);
            r.setReadTimeout(30000);
            r.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BerezaLauncher");
            return r;
        }
        throw new IOException("Забагато перенаправлень");
    }

    private void extract(File zipFile) throws IOException {
        ZipFile zf;
        try {
            zf = new ZipFile(zipFile);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
            throw new IOException("Архів пошкоджений, буде завантажено заново");
        }
        try {
            List<ZipEntry> entries = new ArrayList<>();
            long total = 0;
            for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                entries.add(e);
                if (!e.isDirectory() && e.getSize() > 0) total += e.getSize();
            }
            String strip = commonPrefix(entries);
            byte[] buf = new byte[64 * 1024];
            long done = 0, lastUi = 0;
            for (ZipEntry e : entries) {
                String name = e.getName().replace('\\', '/');
                if (name.startsWith(strip)) name = name.substring(strip.length());
                if (name.isEmpty() || name.contains("../") || name.startsWith("/")) continue;
                if (e.isDirectory()) { storage.ensureDir(name); continue; }
                boolean isApk = name.toLowerCase(Locale.ROOT).endsWith(".apk");
                OutputStream out = isApk
                        ? new FileOutputStream(new File(apkDir(), new File(name).getName()))
                        : storage.openWrite(name);
                InputStream in = zf.getInputStream(e);
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastUi > 300) {
                            lastUi = now;
                            postProgress("Розпакування", done, total, -1);
                        }
                    }
                } finally {
                    in.close();
                    out.close();
                }
            }
        } finally {
            zf.close();
        }
    }

    /** Якщо архів містить зайву обгортку (files/, com.rockstargames.gtasa/files/ …) — прибираємо її. */
    private static String commonPrefix(List<ZipEntry> entries) {
        String[] cands = {
                "Android/data/" + GAME_PKG + "/files/",
                "data/" + GAME_PKG + "/files/",
                GAME_PKG + "/files/",
                "files/",
                GAME_PKG + "/",
        };
        for (String p : cands) {
            boolean all = true, any = false;
            for (ZipEntry e : entries) {
                String n = e.getName().replace('\\', '/');
                if (n.startsWith("__MACOSX/")) continue;
                if (!n.startsWith(p) && !p.startsWith(n)) { all = false; break; }
                any = true;
            }
            if (all && any) return p;
        }
        return "";
    }

    // ---------------- Встановлення клієнта ----------------

    private void offerInstall(final File apk) {
        new AlertDialog.Builder(this)
                .setTitle("Клієнт SA-MP")
                .setMessage("Кеш встановлено. Встановити клієнт гри зараз?")
                .setPositiveButton("Встановити", (d, w) -> installApk(apk))
                .setNegativeButton("Пізніше", null).show();
    }

    private void installApk(File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            toast("Дозволь встановлення з цього джерела і повернись назад");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, RC_INSTALL);
        } catch (Exception e) {
            toast("Не вдалося відкрити встановлювач: " + e.getMessage());
        }
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        try {
            startActivityForResult(i, RC_PICK_APK);
        } catch (Exception e) {
            toast("Провідник файлів недоступний");
        }
    }

    // ---------------- Гра ----------------

    private void play() {
        String n = nick.getText().toString().trim();
        if (!NICK.matcher(n).matches()) {
            nick.setError("3–20 символів: латиниця, цифри, _ (наприклад Ivan_Petrenko)");
            nick.requestFocus();
            return;
        }
        prefs.edit().putString("nick", n).apply();
        try {
            writeSettings(n);
        } catch (Exception e) {
            toast("Не вдалося записати settings.ini: " + e.getMessage());
            return;
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(GAME_PKG);
        if (i == null) { toast("Клієнт не знайдено"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    /** Оновлює settings.ini, зберігаючи решту налаштувань клієнта. */
    private void writeSettings(String name) throws IOException {
        List<String> lines = new ArrayList<>();
        InputStream in = storage.openRead(SETTINGS_INI);
        if (in != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) lines.add(l);
            r.close();
        }
        int sec = -1, end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equalsIgnoreCase("[client]")) { sec = i; end = lines.size(); }
            else if (sec >= 0 && t.startsWith("[") && i > sec) { end = i; break; }
        }
        if (sec < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) lines.add("");
            lines.add("[client]");
            sec = lines.size() - 1;
            end = lines.size();
        }
        boolean[] set = new boolean[3];
        String[][] keys = {{"name", "nick", "nickname"}, {"host", "ip", "server"}, {"port"}};
        String[] vals = {name, SERVER_IP, String.valueOf(SERVER_PORT)};
        for (int i = sec + 1; i < end; i++) {
            String t = lines.get(i);
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            String k = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            for (int g = 0; g < keys.length; g++)
                for (String key : keys[g])
                    if (k.equals(key)) {
                        lines.set(i, t.substring(0, eq).trim() + "=" + vals[g]);
                        set[g] = true;
                    }
        }
        String[] def = {"name", "host", "port"};
        int ins = end;
        for (int g = 0; g < 3; g++) if (!set[g]) lines.add(ins++, def[g] + "=" + vals[g]);

        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append("\r\n");
        storage.ensureDir("SAMP");
        OutputStream o = storage.openWrite(SETTINGS_INI);
        o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        o.close();
    }

    // ---------------- Результати ----------------

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_TREE && res == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                storage = Storage.Saf.fromTree(this, tree);
                prefs.edit().putString("tree", tree.toString()).apply();
            } catch (Exception e) {
                storage = null;
                toast("Помилка: " + e.getMessage());
            }
            refresh(true);
        } else if (rc == RC_PICK_APK && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            setBusy("Копіювання APK…");
            new Thread(() -> {
                try {
                    File[] old = apkDir().listFiles();
                    if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    File dst = new File(apkDir(), "client.apk");
                    InputStream in = getContentResolver().openInputStream(u);
                    if (in == null) throw new IOException("Не вдалося прочитати файл");
                    OutputStream out = new FileOutputStream(dst);
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    out.close();
                    runOnUiThread(() -> {
                        busy = false;
                        refresh(false);
                        installApk(dst);
                    });
                } catch (Exception e) {
                    done("Помилка: " + e.getMessage());
                }
            }).start();
        } else if (rc == RC_INSTALL) {
            refresh(false);
        }
    }

    // ---------------- UI ----------------

    private void setBusy(String msg) {
        busy = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        main.setEnabled(false);
        status.setText(msg);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
    }

    private void done(final String error) {
        runOnUiThread(() -> {
            busy = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refresh(false);
            if (error != null) status.setText(error);
        });
    }

    private void postProgress(final String label, final long done, final long total, final double speed) {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder(label).append("…\n").append(mb(done));
            if (total > 0) {
                progress.setIndeterminate(false);
                progress.setProgress((int) (done * 1000 / total));
                sb.append(" / ").append(mb(total))
                        .append(String.format(Locale.ROOT, "  (%d%%)", done * 100 / total));
            } else {
                progress.setIndeterminate(true);
            }
            if (speed > 0) sb.append(String.format(Locale.ROOT, "\n%.1f МБ/с", speed / 1048576.0));
            status.setText(sb.toString());
        });
    }

    private static String mb(long b) {
        return String.format(Locale.ROOT, "%.1f МБ", b / 1048576.0);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
