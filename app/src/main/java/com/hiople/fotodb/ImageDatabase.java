package com.hiople.fotodb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class ImageDatabase extends SQLiteOpenHelper {
    static final class ImageItem {
        final String uri;
        final String root;
        final String name;
        final String mime;
        final String description;
        final String tags;

        ImageItem(String uri, String root, String name, String mime, String description, String tags) {
            this.uri = uri;
            this.root = root;
            this.name = name;
            this.mime = mime;
            this.description = description;
            this.tags = tags;
        }
    }

    ImageDatabase(Context context) {
        super(context, "fotodb.sqlite", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE images (uri TEXT PRIMARY KEY, root TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX images_root_idx ON images(root)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }


    List<ImageItem> search(List<String> roots, String query) {
        return search(roots, query, false);
    }

    List<ImageItem> search(List<String> roots, String query, boolean regex) {
        ArrayList<ImageItem> result = new ArrayList<>();
        if (roots.isEmpty()) return result;
        java.util.regex.Pattern expression = null;
        if (regex && !query.isEmpty()) {
            expression = java.util.regex.Pattern.compile(query, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
        }
        StringBuilder placeholders = new StringBuilder();
        String[] args;
        String selection;
        if (regex) {
            args = new String[roots.size()];
            for (int i = 0; i < roots.size(); i++) {
                if (i > 0) placeholders.append(',');
                placeholders.append('?');
                args[i] = roots.get(i);
            }
            selection = "root IN (" + placeholders + ")";
        } else {
            String pattern = "%" + query.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            args = new String[roots.size() + 3];
            for (int i = 0; i < roots.size(); i++) {
                if (i > 0) placeholders.append(',');
                placeholders.append('?');
                args[i] = roots.get(i);
            }
            args[roots.size()] = pattern;
            args[roots.size() + 1] = pattern;
            args[roots.size() + 2] = pattern;
            selection = "root IN (" + placeholders + ") AND (name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')";
        }
        try (Cursor cursor = getReadableDatabase().query("images", new String[]{"uri", "root", "name", "mime", "description", "tags"}, selection, args, null, null, "name COLLATE NOCASE")) {
            while (cursor.moveToNext()) {
                ImageItem item = new ImageItem(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5));
                if (expression == null || expression.matcher(item.name).find()
                        || expression.matcher(item.description).find()
                        || expression.matcher(item.tags).find()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    void removeRoot(String root) {
        getWritableDatabase().delete("images", "root=?", new String[]{root});
    }

    void replaceScanned(String root, List<ImageItem> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS scanned_uris (uri TEXT PRIMARY KEY)");
            db.delete("scanned_uris", null, null);
            for (ImageItem item : items) {
                ContentValues values = new ContentValues();
                values.put("uri", item.uri);
                values.put("root", root);
                values.put("name", item.name);
                values.put("mime", item.mime);
                db.insertWithOnConflict("images", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                ContentValues update = new ContentValues();
                update.put("name", item.name);
                update.put("mime", item.mime);
                db.update("images", update, "uri=?", new String[]{item.uri});
                ContentValues scanned = new ContentValues();
                scanned.put("uri", item.uri);
                db.insertOrThrow("scanned_uris", null, scanned);
            }
            db.execSQL("DELETE FROM images WHERE root=? AND uri NOT IN (SELECT uri FROM scanned_uris)", new Object[]{root});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void updateText(String uri, String description, String tags) {
        ContentValues values = new ContentValues();
        values.put("description", description);
        values.put("tags", tags);
        getWritableDatabase().update("images", values, "uri=?", new String[]{uri});
    }

    void remove(String uri) { getWritableDatabase().delete("images", "uri=?", new String[]{uri}); }
}
