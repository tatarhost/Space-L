package com.spaceL.menu;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * UI-only Space L panel.
 *
 * This class deliberately contains no game-memory access, gameplay hooks,
 * purchase-state modification, or DLC entitlement changes.
 */
public final class SpaceLMenu {
    private static final int BLACK = Color.rgb(0, 0, 0);
    private static final int WHITE = Color.rgb(255, 255, 255);
    private static final int MUTED = Color.rgb(185, 185, 185);
    private static final int ACCENT = Color.rgb(40, 210, 205);

    private static Activity activity;
    private static FrameLayout overlay;
    private static LinearLayout panel;
    private static LinearLayout page;
    private static TextView playerTab;
    private static TextView unlockerTab;
    private static TextView systemTab;
    private static float uiScale = 1.0f;
    private static String activeTab = "Player";

    private SpaceLMenu() {}

    public static void init(Activity host) {
        if (host == null || host.isFinishing()) return;
        activity = host;

        final View decor = host.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;

        decor.post(new Runnable() {
            @Override
            public void run() {
                attach((ViewGroup) decor);
            }
        });
    }

    private static void attach(ViewGroup decor) {
        if (overlay != null && overlay.getParent() != null) return;
        if (activity == null || activity.isFinishing()) return;

        overlay = new FrameLayout(activity);
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.setClickable(false);

        TextView launcher = makeButton("Space L", 14);
        launcher.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams launcherLp =
                new FrameLayout.LayoutParams(dp(104), dp(42), Gravity.TOP | Gravity.LEFT);
        launcherLp.leftMargin = dp(14);
        launcherLp.topMargin = dp(14);
        overlay.addView(launcher, launcherLp);

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(rect(BLACK, WHITE, 2));
        panel.setVisibility(View.GONE);

        FrameLayout.LayoutParams panelLp =
                new FrameLayout.LayoutParams(dp(296), ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.LEFT);
        panelLp.leftMargin = dp(14);
        panelLp.topMargin = dp(64);
        overlay.addView(panel, panelLp);

        buildPanel();

        launcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        decor.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void buildPanel() {
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = label("SPACE L  /  MENU", 14, WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));

        TextView close = makeButton("X", 12);
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(34)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                panel.setVisibility(View.GONE);
            }
        });
        panel.addView(header);

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(8), 0, dp(8));
        playerTab = tab("Player");
        unlockerTab = tab("Unlocker");
        systemTab = tab("System");
        tabs.addView(playerTab, new LinearLayout.LayoutParams(0, dp(36), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(36), 1);
        second.leftMargin = dp(4);
        tabs.addView(unlockerTab, second);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, dp(36), 1);
        third.leftMargin = dp(4);
        tabs.addView(systemTab, third);
        panel.addView(tabs);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(2), dp(4), dp(2), dp(4));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(270)));

        playerTab.setOnClickListener(tabClick("Player"));
        unlockerTab.setOnClickListener(tabClick("Unlocker"));
        systemTab.setOnClickListener(tabClick("System"));
        showTab(activeTab);
    }

    private static View.OnClickListener tabClick(final String name) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTab(name);
            }
        };
    }

    private static TextView tab(String text) {
        TextView view = makeButton(text, 11);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private static void showTab(String name) {
        activeTab = name;
        if (page == null) return;
        playerTab.setBackground(rect("Player".equals(name) ? WHITE : BLACK,
                WHITE, 1));
        playerTab.setTextColor("Player".equals(name) ? BLACK : WHITE);
        unlockerTab.setBackground(rect("Unlocker".equals(name) ? WHITE : BLACK,
                WHITE, 1));
        unlockerTab.setTextColor("Unlocker".equals(name) ? BLACK : WHITE);
        systemTab.setBackground(rect("System".equals(name) ? WHITE : BLACK,
                WHITE, 1));
        systemTab.setTextColor("System".equals(name) ? BLACK : WHITE);

        page.removeAllViews();
        if ("Player".equals(name)) {
            addHeading("PLAYER");
            addBody("This build provides the Space L interface and configuration tools.");
            addBody("Runtime fuel, damage, gravity and teleport controls are not included.");
        } else if ("Unlocker".equals(name)) {
            addHeading("UNLOCKER");
            addBody("DLC and paid parts remain governed by official purchase entitlements.");
            addBody("This build does not alter billing results or ownership checks.");
        } else {
            addHeading("SYSTEM");
            addBody("Interface scale");
            final TextView scaleValue = label("100%", 12, WHITE);
            page.addView(scaleValue, margins(dp(2), dp(4), 0, dp(2)));
            SeekBar scale = new SeekBar(activity);
            scale.setMax(100);
            scale.setProgress(Math.round((uiScale - 0.8f) * 250f));
            page.addView(scale, margins(0, 0, 0, dp(10)));
            scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    uiScale = 0.8f + (progress / 250f);
                    scaleValue.setText(Math.round(uiScale * 100f) + "%");
                    if (panel != null) {
                        panel.setScaleX(uiScale);
                        panel.setScaleY(uiScale);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            TextView save = makeButton("SAVE CONFIG TO DOWNLOADS", 11);
            page.addView(save, margins(0, dp(4), 0, dp(8)));
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveConfig();
                }
            });

            TextView website = makeButton("CHEATLAB.FUN", 11);
            website.setTextColor(ACCENT);
            page.addView(website);
            website.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://cheatlab.fun")));
                    } catch (Exception error) {
                        Toast.makeText(activity, "Unable to open link", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private static void addHeading(String text) {
        TextView view = label(text, 12, WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(view, margins(dp(2), dp(4), 0, dp(8)));
    }

    private static void addBody(String text) {
        TextView view = label(text, 12, MUTED);
        view.setGravity(Gravity.LEFT);
        page.addView(view, margins(dp(2), dp(2), dp(2), dp(10)));
    }

    private static void saveConfig() {
        if (activity == null) return;
        OutputStream stream = null;
        try {
            JSONObject config = new JSONObject();
            config.put("name", "Space L");
            config.put("version", 1);
            config.put("uiScale", (double) uiScale);
            config.put("activeTab", activeTab);
            byte[] bytes = config.toString(2).getBytes(StandardCharsets.UTF_8);

            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = activity.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SpaceL_Config.json");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Downloads provider returned no URI");
                stream = resolver.openOutputStream(uri);
                if (stream == null) throw new IllegalStateException("Unable to open Downloads output");
                stream.write(bytes);
                stream.flush();
            } else {
                File base = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (base == null) throw new IllegalStateException("External app storage unavailable");
                if (!base.exists() && !base.mkdirs()) {
                    throw new IllegalStateException("Unable to create config directory");
                }
                File file = new File(base, "SpaceL_Config.json");
                FileOutputStream output = new FileOutputStream(file);
                output.write(bytes);
                output.flush();
                output.close();
            }
            Toast.makeText(activity, "SpaceL_Config.json saved", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, "Config save failed: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static TextView makeButton(String text, int size) {
        TextView view = label(text, size, WHITE);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(6), 0, dp(6), 0);
        view.setBackground(rect(BLACK, WHITE, 1));
        view.setClickable(true);
        return view;
    }

    private static TextView label(String text, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static GradientDrawable rect(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams margins(
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static int dp(int value) {
        if (activity == null) return value;
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
