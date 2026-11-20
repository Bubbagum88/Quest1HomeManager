package io.github.bubbagum88.homemanager;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** The home catalog: bundled copy, replaced by the latest from GitHub when refreshed. */
final class Catalog {
    static final String URL_LATEST =
            "https://raw.githubusercontent.com/Bubbagum88/Quest1HomeManager/main/catalog/catalog.json";

    static final class Download {
        final String url, kind, variant;
        Download(String url, String kind, String variant) { this.url = url; this.kind = kind; this.variant = variant; }
        boolean direct() { return "apk".equals(kind); }
    }

    static final class Entry {
        final String id, name, creator, description, source, category;
        final boolean free;
        final List<Download> downloads = new ArrayList<>();
        Entry(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name");
            creator = str(o, "creator");
            description = str(o, "description");
            source = o.optString("source");
            category = str(o, "category");
            free = o.optBoolean("free", true);
            JSONArray d = o.optJSONArray("downloads");
            for (int i = 0; d != null && i < d.length(); i++) {
                JSONObject x = d.optJSONObject(i);
                downloads.add(new Download(x.optString("url"), x.optString("kind"), str(x, "variant")));
            }
        }
        Download best() {
            for (Download d : downloads) if (d.direct() && !"silent".equals(d.variant)) return d;
            for (Download d : downloads) if (d.direct()) return d;
            for (Download d : downloads) if (!"expired".equals(d.kind)) return d;
            return null;
        }
        boolean direct() { Download d = best(); return d != null && d.direct(); }
        String haystack() {
            return (name + " " + (creator == null ? "" : creator) + " " + (description == null ? "" : description)
                    + " " + (category == null ? "" : category)).toLowerCase();
        }
    }

    private static String str(JSONObject o, String k) {
        return o.isNull(k) ? null : o.optString(k, null);
    }

    static File cacheFile(Context c) { return new File(c.getFilesDir(), "catalog.json"); }

    /** Cached copy if there is one, else the copy bundled in the APK. */
    static List<Entry> load(Context c) throws Exception {
        File f = cacheFile(c);
        String json = f.exists() ? read(new FileInputStream(f)) : read(c.getAssets().open("catalog.json"));
        return parse(json);
    }

    /** Downloads the latest catalog, checks it parses, then caches it. */
    static List<Entry> refresh(Context c) throws Exception {
        String json = read(open(URL_LATEST));
        List<Entry> list = parse(json);
        try (FileOutputStream out = new FileOutputStream(cacheFile(c))) {
            out.write(json.getBytes("UTF-8"));
        }
        return list;
    }

    static List<Entry> parse(String json) throws Exception {
        JSONArray a = new JSONObject(json).getJSONArray("homes");
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(new Entry(a.getJSONObject(i)));
        return out;
    }

    static InputStream open(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        h.setInstanceFollowRedirects(true);
        int code = h.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code + " for " + url);
        return h.getInputStream();
    }

    static String read(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    /** Downloads an APK into the app's cache, reporting progress, and returns the file. */
    interface Progress { void update(long done, long total); }

    static File downloadApk(Context c, String url, Progress p) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        int code = h.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        long total = h.getContentLengthLong();
        File f = new File(c.getCacheDir(), "home.apk");
        try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                p.update(done, total);
            }
        }
        return f;
    }

    /** Installs an APK as root: copy to /data/local/tmp, pm install, clean up. */
    static Root.Result installApk(File f) {
        String tmp = "/data/local/tmp/qhm-home.apk";
        return Root.run("cp " + Root.q(f.getAbsolutePath()) + " " + tmp
                + " && chmod 644 " + tmp + " && pm install -r " + tmp + "; r=$?; rm -f " + tmp + "; exit $r");
    }

    private Catalog() {}
}
