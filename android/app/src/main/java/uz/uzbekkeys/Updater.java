package uz.uzbekkeys;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks GitHub (release "android") for a newer build and opens the APK download. */
public final class Updater {
    public static final String VERSION_URL =
            "https://github.com/shaxik75-debug/uzbek-keys/releases/download/android/version.txt";
    public static final String APK_URL =
            "https://github.com/shaxik75-debug/uzbek-keys/releases/download/android/UzbekKeys.apk";

    private static final long INTERVAL = 6L * 60 * 60 * 1000;

    public interface Callback {
        /** remote = -1 when the server could not be reached. */
        void onResult(int remote, int local);
    }

    private Updater() {}

    @SuppressWarnings("deprecation")
    public static int localVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String localName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Always asks the server. */
    public static void check(Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int remote = -1;
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                    con.setUseCaches(false);
                    con.setInstanceFollowRedirects(true);
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(10000);
                    if (con.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        String line = r.readLine();
                        r.close();
                        if (line != null) remote = Integer.parseInt(line.trim());
                    }
                } catch (Exception ignored) {
                    remote = -1;
                } finally {
                    if (con != null) con.disconnect();
                }
                if (remote > 0) {
                    app.getSharedPreferences("uk", Context.MODE_PRIVATE).edit().putInt("remote", remote).apply();
                }
                final int rv = remote;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(rv, localVersion(app));
                    }
                });
            }
        }).start();
    }

    /** Asks the server at most every 6 hours; otherwise answers from the last known result. */
    public static void maybeCheck(Context ctx, Callback cb) {
        SharedPreferences p = ctx.getSharedPreferences("uk", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - p.getLong("lastCheck", 0) < INTERVAL) {
            cb.onResult(p.getInt("remote", 0), localVersion(ctx));
            return;
        }
        p.edit().putLong("lastCheck", now).apply();
        check(ctx, cb);
    }

    public static void openDownload(Context ctx) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
