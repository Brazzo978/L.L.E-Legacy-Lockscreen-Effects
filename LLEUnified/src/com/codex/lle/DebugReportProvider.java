package com.codex.lle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only provider for reports generated into L.L.E's private cache. */
public final class DebugReportProvider extends ContentProvider {
    static Uri uriFor(Context context, File report) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".debugreports")
                .appendPath("report")
                .appendPath(report.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        resolveReport(uri);
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        File report = resolveReport(uri);
        String[] columns = projection == null
                ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(report.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(report.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Debug reports are read-only");
        }
        return ParcelFileDescriptor.open(
                resolveReport(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Debug reports are read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Debug reports are read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Debug reports are read-only");
    }

    private File resolveReport(Uri uri) {
        Context context = getContext();
        String name = uri == null ? null : uri.getLastPathSegment();
        if (context == null || !DebugReport.isShareableReportName(name)) {
            throw new IllegalArgumentException("Invalid debug report URI");
        }
        File directory = DebugReport.reportDirectory(context);
        File report = new File(directory, name);
        try {
            if (!report.getCanonicalFile().getParentFile()
                    .equals(directory.getCanonicalFile())) {
                throw new IllegalArgumentException("Invalid debug report path");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid debug report path", error);
        }
        if (!report.isFile()) {
            throw new IllegalArgumentException("Debug report does not exist");
        }
        return report;
    }
}
