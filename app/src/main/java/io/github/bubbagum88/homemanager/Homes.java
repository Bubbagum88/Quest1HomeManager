package io.github.bubbagum88.homemanager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installed homes and the setting that picks one.
 *
 * Horizon OS v50 keeps the current home in the Oculus preference
 * environment_selected as apk://PACKAGE/assets/scene.zip; empty means
 * environment_default, then the built-in fallback (SystemUX EnvironmentUtil).
 * A home is any package with meta-data com.oculus.environmentVersion >= 1,
 * the same test Meta's own picker uses.
 */
final class Homes {
    static final String KEY = "environment_selected";

    static final class Home {
        final String pkg, label;
        Home(String pkg, String label) { this.pkg = pkg; this.label = label; }
        @Override public String toString() { return label; }
    }

    static List<Home> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Home> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (ai.metaData != null && ai.metaData.getInt("com.oculus.environmentVersion", 0) >= 1) {
                out.add(new Home(ai.packageName, pm.getApplicationLabel(ai).toString()));
            }
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static String path(String pkg) { return "apk://" + pkg + "/assets/scene.zip"; }

    /** Package of the selected home, "" when the default is in use, null if unreadable. */
    static String current() {
        Root.Result r = Root.run("oculuspreferences --getc " + KEY);
        if (!r.ok()) return null;
        // Output: [environment_selected : "apk://pkg/assets/scene.zip"] retrieved successfully
        Matcher m = Pattern.compile("apk://([^/\"]+)/").matcher(r.out);
        return m.find() ? m.group(1) : "";
    }

    static Root.Result apply(String pkg) {
        return Root.run("oculuspreferences --setc " + KEY + " " + Root.q(path(pkg)));
    }

    static Root.Result reset() {
        return Root.run("oculuspreferences --reset " + KEY);
    }

    /** Picks a random installed home other than the current one. */
    static Root.Result applyRandom(Context c) {
        List<Home> all = installed(c);
        String cur = current();
        List<Home> pick = new ArrayList<>();
        for (Home h : all) if (!h.pkg.equals(cur)) pick.add(h);
        if (pick.isEmpty()) return new Root.Result(1, "no other home installed");
        Home h = pick.get(new Random().nextInt(pick.size()));
        Root.Result r = apply(h.pkg);
        return new Root.Result(r.code, h.label + ": " + r.out);
    }

    private Homes() {}
}
