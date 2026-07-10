package com.codex.s4unlockfx;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class QmgDumpActivity extends Activity {
    private static final String TAG = "QmgDump";
    private static final String MANIFEST_ASSET = "qmgdump/manifest.tsv";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private TextView pathText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(10, 14, 20));

        TextView title = new TextView(this);
        title.setText("QMG dump");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextColor(Color.argb(230, 230, 240, 255));
        statusText.setTextSize(15f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        pathText = new TextView(this);
        pathText.setTextColor(Color.argb(190, 190, 205, 220));
        pathText.setTextSize(12f);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = dp(8);
        root.addView(pathText, pathParams);
        setContentView(root);

        updateStatus("Starting...", "");
        new Thread(new Runnable() {
            @Override
            public void run() {
                dumpAssets();
            }
        }, "QmgDumpThread").start();
    }

    private void dumpAssets() {
        File baseDir = getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = getFilesDir();
        }
        File outRoot = new File(baseDir, "qmg_dump");
        deleteRecursive(outRoot);
        if (!outRoot.mkdirs() && !outRoot.isDirectory()) {
            updateStatus("Failed to create output dir", outRoot.getAbsolutePath());
            return;
        }

        List<Row> rows;
        try {
            rows = readManifest();
        } catch (Throwable t) {
            updateStatus("Manifest error: " + t.getClass().getSimpleName(), t.getMessage());
            return;
        }

        File report = new File(outRoot, "decode_report.tsv");
        int ok = 0;
        int failed = 0;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(report), "UTF-8"));
            writer.write("status\tasset_path\toutput_rel\twidth\theight\terror\treview_file\toriginal_path\n");
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (i == 0 || i % 25 == 0) {
                    updateStatus("Converting " + (i + 1) + " / " + rows.size()
                            + "  ok=" + ok + " fail=" + failed, outRoot.getAbsolutePath());
                }
                Result result = convertOne(row, outRoot);
                if (result.ok) {
                    ok++;
                } else {
                    failed++;
                }
                writer.write(result.status);
                writer.write('\t');
                writer.write(row.assetPath);
                writer.write('\t');
                writer.write(row.outputRel);
                writer.write('\t');
                writer.write(Integer.toString(result.width));
                writer.write('\t');
                writer.write(Integer.toString(result.height));
                writer.write('\t');
                writer.write(clean(result.error));
                writer.write('\t');
                writer.write(clean(row.reviewFile));
                writer.write('\t');
                writer.write(clean(row.originalPath));
                writer.write('\n');
            }
        } catch (Throwable t) {
            updateStatus("Dump crashed: " + t.getClass().getSimpleName(), t.getMessage());
            Log.e(TAG, "dump failed", t);
            return;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }

        File done = new File(outRoot, "_DONE.txt");
        try {
            BufferedWriter doneWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(done), "UTF-8"));
            doneWriter.write("ok=" + ok + "\n");
            doneWriter.write("failed=" + failed + "\n");
            doneWriter.write("total=" + rows.size() + "\n");
            doneWriter.close();
        } catch (Throwable ignored) {
        }
        updateStatus("Done. ok=" + ok + " fail=" + failed + " total=" + rows.size(),
                outRoot.getAbsolutePath());
    }

    private Result convertOne(Row row, File outRoot) {
        InputStream input = null;
        Bitmap bitmap = null;
        try {
            input = openAsset(row.assetPath, AssetManager.ACCESS_STREAMING);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) {
                return Result.failed("NULL_BITMAP");
            }
            File outFile = new File(outRoot, row.outputRel);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return Result.failed("MKDIR_FAILED");
            }
            FileOutputStream output = new FileOutputStream(outFile);
            boolean compressed;
            try {
                compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally {
                output.close();
            }
            if (!compressed) {
                return Result.failed("PNG_COMPRESS_FAILED");
            }
            return Result.ok(bitmap.getWidth(), bitmap.getHeight());
        } catch (Throwable t) {
            Log.w(TAG, "convert failed: " + row.assetPath, t);
            return Result.failed(t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private List<Row> readManifest() throws Exception {
        ArrayList<Row> rows = new ArrayList<Row>();
        InputStream input = openAsset(MANIFEST_ASSET, AssetManager.ACCESS_BUFFER);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) {
                first = false;
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 9) {
                continue;
            }
            Row row = new Row();
            row.assetPath = parts[0];
            row.outputRel = parts[1];
            row.reviewFile = parts[4];
            row.originalPath = parts[5];
            rows.add(row);
        }
        reader.close();
        return rows;
    }

    private InputStream openAsset(String path, int accessMode) throws Exception {
        AssetManager assets = getAssets();
        try {
            return assets.open(path, accessMode);
        } catch (Throwable first) {
            String alternate = path.replace('/', '\\');
            if (!alternate.equals(path)) {
                try {
                    return assets.open(alternate, accessMode);
                } catch (Throwable ignored) {
                }
            }
            throw new java.io.FileNotFoundException(path + " / " + alternate);
        }
    }

    private void updateStatus(final String status, final String path) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
                pathText.setText(path == null ? "" : path);
            }
        });
        Log.i(TAG, status + " " + path);
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not delete " + file);
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Row {
        String assetPath;
        String outputRel;
        String reviewFile;
        String originalPath;
    }

    private static class Result {
        boolean ok;
        String status;
        int width;
        int height;
        String error;

        static Result ok(int width, int height) {
            Result result = new Result();
            result.ok = true;
            result.status = "OK";
            result.width = width;
            result.height = height;
            result.error = "";
            return result;
        }

        static Result failed(String error) {
            Result result = new Result();
            result.ok = false;
            result.status = "FAIL";
            result.width = 0;
            result.height = 0;
            result.error = error == null ? "" : error;
            return result;
        }
    }
}
