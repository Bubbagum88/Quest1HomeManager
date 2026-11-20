package io.github.bubbagum88.homemanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/**
 * Auto-rotate. Mode 0 = off, -1 = at boot only, N > 0 = at boot and every N hours.
 * The alarm is inexact so it batches with other wakeups.
 */
public final class RotateReceiver extends BroadcastReceiver {
    static final String TAG = "HomeManager";
    static final String ACTION_ROTATE = "io.github.bubbagum88.homemanager.ROTATE";
    static final String PREFS = "rotate";
    static final String MODE = "mode";

    static int mode(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE, 0);
    }

    static void setMode(Context c, int mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(MODE, mode).apply();
        schedule(c);
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Intent i = new Intent(c, RotateReceiver.class).setAction(ACTION_ROTATE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        int m = mode(c);
        if (m > 0) {
            long every = m * AlarmManager.INTERVAL_HOUR;
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + every, every, pi);
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        int m = mode(c);
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        if (boot) schedule(c);
        if (m == 0 || !(boot || ACTION_ROTATE.equals(action))) return;
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                Root.Result r = Homes.applyRandom(app);
                Log.i(TAG, "rotate (" + action + "): code " + r.code + " " + r.out);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
