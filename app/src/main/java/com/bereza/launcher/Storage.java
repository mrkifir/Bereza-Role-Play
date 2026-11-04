package com.bereza.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Доступ до папки .../com.rockstargames.gtasa/files через File API або через SAF. */
abstract class Storage {

    abstract OutputStream openWrite(String rel) throws IOException;

    abstract InputStream openRead(String rel) throws IOException; // null якщо файла нема

    abstract void ensureDir(String rel) throws IOException;

    abstract String describe();

    // ---------- File API (Android 10 і старші) ----------
    static final class Files extends Storage {
        final File root;

        Files(File root) { this.root = root; }

        static Files tryOpen(File root) {
            try {
                if (!root.exists() && !root.mkdirs()) return null;
                File t = new File(root, ".bz_test");
                FileOutputStream o = new FileOutputStream(t);
                o.write(1);
                o.close();
                //noinspection ResultOfMethodCallIgnored
                t.delete();
                return new Files(root);
            } catch (Exception e) {
                return null;
            }
        }

        @Override OutputStream openWrite(String rel) throws IOException {
            File f = new File(root, rel);
            File p = f.getParentFile();
            if (p != null && !p.exists() && !p.mkdirs()) throw new IOException("mkdir " + p);
            return new FileOutputStream(f);
        }

        @Override InputStream openRead(String rel) throws IOException {
            File f = new File(root, rel);
            return f.isFile() ? new FileInputStream(f) : null;
        }

        @Override void ensureDir(String rel) throws IOException {
            File f = new File(root, rel);
            if (!f.exists() && !f.mkdirs()) throw new IOException("mkdir " + f);
        }

        @Override String describe() { return root.getAbsolutePath(); }
    }

    // ---------- SAF (Android 11–12 і деякі прошивки) ----------
    static final class Saf extends Storage {
        final Context ctx;
        final ContentResolver cr;
        final DocumentFile root;
        private final Map<String, DocumentFile> dirs = new HashMap<>();
        private final Map<String, Map<String, DocumentFile>> children = new HashMap<>();

        Saf(Context ctx, DocumentFile root) {
            this.ctx = ctx;
            this.cr = ctx.getContentResolver();
            this.root = root;
            dirs.put("", root);
        }

        /** Перетворює обрану користувачем папку на .../com.rockstargames.gtasa/files */
        static Saf fromTree(Context ctx, Uri tree) throws IOException {
            String id = DocumentsContract.getTreeDocumentId(tree);
            DocumentFile d = DocumentFile.fromTreeUri(ctx, tree);
            if (d == null) throw new IOException("Не вдалося відкрити папку");
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            Saf tmp = new Saf(ctx, d);
            String sub;
            if (path.equalsIgnoreCase("Android/data")) sub = MainActivity.GAME_PKG + "/files";
            else if (path.equalsIgnoreCase("Android/data/" + MainActivity.GAME_PKG)) sub = "files";
            else if (path.equalsIgnoreCase("Android/data/" + MainActivity.GAME_PKG + "/files")) sub = "";
            else if (path.equalsIgnoreCase("Android")) sub = "data/" + MainActivity.GAME_PKG + "/files";
            else throw new IOException("Треба обрати папку Android/data");
            DocumentFile files = sub.isEmpty() ? d : tmp.dir(sub);
            return new Saf(ctx, files);
        }

        private Map<String, DocumentFile> list(String dirRel, DocumentFile dir) {
            Map<String, DocumentFile> m = children.get(dirRel);
            if (m == null) {
                m = new HashMap<>();
                for (DocumentFile f : dir.listFiles()) {
                    if (f.getName() != null) m.put(f.getName(), f);
                }
                children.put(dirRel, m);
            }
            return m;
        }

        private DocumentFile dir(String rel) throws IOException {
            rel = norm(rel);
            DocumentFile c = dirs.get(rel);
            if (c != null) return c;
            int i = rel.lastIndexOf('/');
            String parentRel = i < 0 ? "" : rel.substring(0, i);
            String name = i < 0 ? rel : rel.substring(i + 1);
            DocumentFile parent = dir(parentRel);
            Map<String, DocumentFile> kids = list(parentRel, parent);
            DocumentFile d = kids.get(name);
            if (d == null || !d.isDirectory()) {
                d = parent.createDirectory(name);
                if (d == null) throw new IOException("Не вдалося створити папку " + rel);
                kids.put(name, d);
                children.put(rel, new HashMap<String, DocumentFile>());
            }
            dirs.put(rel, d);
            return d;
        }

        private static String norm(String rel) {
            rel = rel.replace('\\', '/');
            while (rel.startsWith("/")) rel = rel.substring(1);
            while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
            return rel;
        }

        @Override OutputStream openWrite(String rel) throws IOException {
            rel = norm(rel);
            int i = rel.lastIndexOf('/');
            String parentRel = i < 0 ? "" : rel.substring(0, i);
            String name = i < 0 ? rel : rel.substring(i + 1);
            DocumentFile parent = dir(parentRel);
            Map<String, DocumentFile> kids = list(parentRel, parent);
            DocumentFile f = kids.get(name);
            if (f == null) {
                f = parent.createFile("application/octet-stream", name);
                if (f == null) throw new IOException("Не вдалося створити " + rel);
                kids.put(name, f);
            }
            OutputStream o = cr.openOutputStream(f.getUri(), "wt");
            if (o == null) throw new IOException("Не вдалося записати " + rel);
            return o;
        }

        @Override InputStream openRead(String rel) throws IOException {
            rel = norm(rel);
            int i = rel.lastIndexOf('/');
            String parentRel = i < 0 ? "" : rel.substring(0, i);
            String name = i < 0 ? rel : rel.substring(i + 1);
            DocumentFile parent = dir(parentRel);
            DocumentFile f = list(parentRel, parent).get(name);
            if (f == null || !f.isFile()) return null;
            return cr.openInputStream(f.getUri());
        }

        @Override void ensureDir(String rel) throws IOException { dir(rel); }

        @Override String describe() { return "SAF: " + root.getUri(); }
    }
}
