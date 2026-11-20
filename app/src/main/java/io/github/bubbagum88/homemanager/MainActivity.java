package io.github.bubbagum88.homemanager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two pages: Installed (apply / reset / auto-rotate) and Market (catalog). */
public final class MainActivity extends Activity {
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView status;
    private LinearLayout installedPage, marketPage;

    // Installed page
    private final List<Homes.Home> homes = new ArrayList<>();
    private ArrayAdapter<Homes.Home> homesAdapter;
    private String currentPkg;

    // Market page
    private List<Catalog.Entry> catalog = new ArrayList<>();
    private final List<Catalog.Entry> shown = new ArrayList<>();
    private EditText search;
    private CheckBox directOnly;
    private MarketAdapter marketAdapter;

    private static final String[] ROTATE_LABELS =
            {"Off", "At boot", "Every hour", "Every 3 hours", "Every 6 hours", "Every 12 hours", "Every day"};
    private static final int[] ROTATE_MODES = {0, -1, 1, 3, 6, 12, 24};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = vbox();
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout tabs = hbox();
        Button tabInstalled = button("Installed", v -> show(true));
        Button tabMarket = button("Market", v -> show(false));
        tabs.addView(tabInstalled, weight());
        tabs.addView(tabMarket, weight());
        root.addView(tabs);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        installedPage = buildInstalledPage();
        marketPage = buildMarketPage();
        root.addView(installedPage, fill());
        root.addView(marketPage, fill());
        setContentView(root);
        show(true);
        refreshInstalled();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homesAdapter != null) refreshInstalled();
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    private void show(boolean installed) {
        installedPage.setVisibility(installed ? View.VISIBLE : View.GONE);
        marketPage.setVisibility(installed ? View.GONE : View.VISIBLE);
    }

    // ---------- Installed ----------

    private LinearLayout buildInstalledPage() {
        LinearLayout page = vbox();

        LinearLayout row = hbox();
        row.addView(button("Use default home", v -> work("Resetting...", Homes::reset)), weight());
        row.addView(button("Random home now", v -> work("Picking a home...", () -> Homes.applyRandom(this))), weight());
        page.addView(row);

        LinearLayout rot = hbox();
        TextView l = new TextView(this);
        l.setText("Auto-rotate: ");
        rot.addView(l);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ROTATE_LABELS));
        int cur = RotateReceiver.mode(this);
        for (int i = 0; i < ROTATE_MODES.length; i++) if (ROTATE_MODES[i] == cur) sp.setSelection(i);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (ROTATE_MODES[pos] != RotateReceiver.mode(MainActivity.this)) {
                    RotateReceiver.setMode(MainActivity.this, ROTATE_MODES[pos]);
                    status.setText("Auto-rotate: " + ROTATE_LABELS[pos]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        rot.addView(sp, weight());
        page.addView(rot);

        ListView list = new ListView(this);
        homesAdapter = new ArrayAdapter<Homes.Home>(this, android.R.layout.simple_list_item_2, android.R.id.text1, homes) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                Homes.Home h = getItem(pos);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                boolean active = h.pkg.equals(currentPkg);
                t1.setText((active ? "● " : "") + h.label);
                t1.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
                t2.setText(h.pkg);
                return v;
            }
        };
        list.setAdapter(homesAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Homes.Home h = homes.get(pos);
            work("Applying " + h.label + "...", () -> Homes.apply(h.pkg));
        });
        page.addView(list, fill());
        return page;
    }

    private void refreshInstalled() {
        bg.execute(() -> {
            List<Homes.Home> list = Homes.installed(this);
            String cur = Homes.current();
            ui.post(() -> {
                homes.clear();
                homes.addAll(list);
                currentPkg = cur;
                homesAdapter.notifyDataSetChanged();
                if (cur == null) status.setText("Root not granted: allow Home Manager in Magisk.");
                else status.setText(list.size() + " homes installed. Current: "
                        + (cur.isEmpty() ? "default" : cur) + ". Tap a home to apply it.");
            });
        });
    }

    private interface Job { Root.Result run(); }

    private void work(String msg, Job job) {
        status.setText(msg);
        bg.execute(() -> {
            Root.Result r = job.run();
            ui.post(() -> {
                status.setText((r.ok() ? "Done. " : "Failed (" + r.code + "). ") + r.out);
                refreshInstalledKeepStatus();
            });
        });
    }

    private void refreshInstalledKeepStatus() {
        bg.execute(() -> {
            List<Homes.Home> list = Homes.installed(this);
            String cur = Homes.current();
            ui.post(() -> {
                homes.clear();
                homes.addAll(list);
                currentPkg = cur;
                homesAdapter.notifyDataSetChanged();
            });
        });
    }

    // ---------- Market ----------

    private LinearLayout buildMarketPage() {
        LinearLayout page = vbox();

        LinearLayout row = hbox();
        search = new EditText(this);
        search.setHint("Search homes");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { filter(); }
        });
        row.addView(search, weight());
        row.addView(button("Update list", v -> loadCatalog(true)));
        page.addView(row);

        directOnly = new CheckBox(this);
        directOnly.setText("Only homes that install here (direct download)");
        directOnly.setChecked(true);
        directOnly.setOnCheckedChangeListener((v, c) -> filter());
        page.addView(directOnly);

        ListView list = new ListView(this);
        marketAdapter = new MarketAdapter();
        list.setAdapter(marketAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> get(shown.get(pos)));
        page.addView(list, fill());
        return page;
    }

    private void loadCatalog(boolean latest) {
        if (latest) status.setText("Downloading the latest list...");
        bg.execute(() -> {
            try {
                List<Catalog.Entry> c = latest ? Catalog.refresh(this) : Catalog.load(this);
                ui.post(() -> {
                    catalog = c;
                    filter();
                    if (latest) status.setText("List updated: " + c.size() + " homes.");
                });
            } catch (Exception e) {
                ui.post(() -> status.setText("Could not load the list: " + e.getMessage()));
            }
        });
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (Catalog.Entry e : catalog) {
            if (directOnly.isChecked() && !e.direct()) continue;
            if (!q.isEmpty() && !e.haystack().contains(q)) continue;
            if (e.best() == null) continue;
            shown.add(e);
        }
        marketAdapter.notifyDataSetChanged();
    }

    private void get(Catalog.Entry e) {
        Catalog.Download d = e.best();
        if (d == null) return;
        if (!d.direct()) {
            status.setText("Opening " + e.name + " in the browser...");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(d.url)));
            return;
        }
        status.setText("Downloading " + e.name + "...");
        bg.execute(() -> {
            try {
                File f = Catalog.downloadApk(this, d.url, (done, total) -> {
                    if (done % (1 << 20) < 65536) {
                        String t = total > 0 ? " of " + (total >> 20) + " MB" : "";
                        ui.post(() -> status.setText("Downloading " + e.name + ": " + (done >> 20) + " MB" + t));
                    }
                });
                ui.post(() -> status.setText("Installing " + e.name + "..."));
                Root.Result r = Catalog.installApk(f);
                f.delete();
                ui.post(() -> {
                    status.setText(r.ok() ? e.name + " installed. Apply it on the Installed page."
                            : "Install failed (" + r.code + "): " + r.out);
                    refreshInstalledKeepStatus();
                });
            } catch (Exception ex) {
                ui.post(() -> status.setText("Download failed: " + ex.getMessage()));
            }
        });
    }

    private final class MarketAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            View v = cv != null ? cv : getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            Catalog.Entry e = shown.get(p);
            Catalog.Download d = e.best();
            ((TextView) v.findViewById(android.R.id.text1)).setText(e.name);
            StringBuilder s = new StringBuilder();
            if (e.creator != null) s.append(e.creator).append(" · ");
            s.append(d.direct() ? "Install" : "Opens " + d.kind);
            if (!e.free) s.append(" · paid");
            if (e.description != null) s.append(" · ").append(e.description);
            ((TextView) v.findViewById(android.R.id.text2)).setText(s);
            return v;
        }
    }

    // ---------- small view helpers ----------

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private Button button(String t, View.OnClickListener l) { Button b = new Button(this); b.setText(t); b.setOnClickListener(l); return b; }
    private static LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private static LinearLayout.LayoutParams fill() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f); }
}
