package com.hiople.fotodb;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.LruCache;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ImageDatabase database;
    private android.content.SharedPreferences preferences;
    private EditText searchBox;
    private TextView status;
    private ProgressBar progress;
    private ImageAdapter adapter;
    private final ArrayList<String> roots = new ArrayList<>();
    private boolean scanning;
    private OcrProvider activeOcrProvider;
    private Bitmap activeOcrBitmap;
    private boolean activeOcrRunning;
    private final LruCache<String, Bitmap> thumbnailCache = new LruCache<>(cacheSize()) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private final ActivityResultLauncher<Intent> folderLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            handleFolderResult(result.getData());
                        }
                    });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!night);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!night);
        database = new ImageDatabase(this);
        preferences = getSharedPreferences("library", MODE_PRIVATE);
        loadRoots();
        buildScreen();
        if (!roots.isEmpty()) scanFolders();
        else setStatus();
    }

    private void loadRoots() {
        String stored = preferences.getString("trees", "");
        if (!stored.isEmpty()) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(stored);
                for (int i = 0; i < array.length(); i++) roots.add(array.getString(i));
            } catch (org.json.JSONException ignored) { }
        }
        String legacy = preferences.getString("tree", "");
        if (roots.isEmpty() && !legacy.isEmpty()) roots.add(legacy);
        saveRoots();
    }

    private void saveRoots() {
        org.json.JSONArray array = new org.json.JSONArray();
        for (String root : roots) array.put(root);
        preferences.edit().putString("trees", array.toString()).remove("tree").apply();
    }

    private void buildScreen() {
        boolean night = isNight();
        int muted = night ? Color.rgb(185, 193, 190) : Color.rgb(103, 114, 109);
        FrameLayout screen = new FrameLayout(this);
        setContentView(screen);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(surfaceColor());
        page.setPadding(dp(10), dp(4), dp(10), 0);
        ViewCompat.setOnApplyWindowInsetsListener(page, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(dp(10) + insets.left, dp(4) + insets.top, dp(10) + insets.right, insets.bottom);
            return windowInsets;
        });
        screen.addView(page, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu = new TextView(this);
        menu.setText("☰"); menu.setTextSize(26); menu.setGravity(Gravity.CENTER);
        menu.setTextColor(night ? Color.WHITE : Color.rgb(27, 53, 44));
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(48)));
        searchBox = new EditText(this); searchBox.setSingleLine(true); searchBox.setHint("⌕  搜索描述、标签或文件名"); styleInput(searchBox);
        searchBox.setBackground(rounded(night ? Color.rgb(48, 53, 51) : Color.WHITE, dp(18))); searchBox.setElevation(dp(2));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        searchParams.leftMargin = dp(6);
        titleRow.addView(searchBox, searchParams);
        Button regexToggle = button(".*");
        boolean regexEnabled = preferences.getBoolean("regex_search", false);
        regexToggle.setSelected(regexEnabled);
        regexToggle.setTextColor(regexEnabled ? Color.WHITE : (night ? Color.WHITE : Color.rgb(35, 83, 65)));
        if (regexEnabled) regexToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(63, 135, 108)));
        LinearLayout.LayoutParams regexParams = new LinearLayout.LayoutParams(dp(46), dp(44));
        regexParams.leftMargin = dp(4);
        titleRow.addView(regexToggle, regexParams);
        regexToggle.setOnClickListener(v -> {
            boolean enabled = !preferences.getBoolean("regex_search", false);
            preferences.edit().putBoolean("regex_search", enabled).apply();
            regexToggle.setSelected(enabled);
            regexToggle.setTextColor(enabled ? Color.WHITE : (night ? Color.WHITE : Color.rgb(35, 83, 65)));
            regexToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(enabled
                    ? Color.rgb(63, 135, 108)
                    : (night ? Color.rgb(48, 75, 63) : Color.rgb(226, 239, 230))));
        });
        searchBox.setHint("搜索描述、标签、文件名");
        searchBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBox.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                loadResults();
                return true;
            }
            return false;
        });
        page.addView(titleRow);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!preferences.getBoolean("regex_search", false)) loadResults();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout state = new LinearLayout(this); state.setGravity(Gravity.CENTER_VERTICAL); state.setPadding(dp(4), 0, dp(4), 0);
        status = new TextView(this); status.setTextColor(muted); status.setTextSize(13); status.setMaxLines(2);
        state.addView(status, new LinearLayout.LayoutParams(0, dp(34), 1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall); progress.setVisibility(View.GONE);
        state.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24))); page.addView(state);
        GridView grid = new GridView(this);
        boolean listLayout = preferences.getBoolean("list_layout", false);
        grid.setNumColumns(listLayout ? 1 : 3); grid.setHorizontalSpacing(dp(10)); grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setSelector(new ColorDrawable(Color.TRANSPARENT)); grid.setClipToPadding(false);
        adapter = new ImageAdapter(); grid.setAdapter(adapter); page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout drawerLayer = new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);
        drawerLayer.setBackgroundColor(0x66000000);
        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(20), dp(32), dp(16), dp(16));
        drawer.setBackgroundColor(surfaceColor());
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(280), -1, Gravity.START);
        drawerLayer.addView(drawer, drawerParams);
        screen.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));
        TextView drawerTitle = new TextView(this);
        drawerTitle.setText("FotoDB\n照片库操作"); drawerTitle.setTextSize(20); drawerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        drawerTitle.setTextColor(night ? Color.WHITE : Color.rgb(27, 53, 44));
        LinearLayout.LayoutParams drawerTitleParams = new LinearLayout.LayoutParams(-1, dp(56));
        drawer.addView(drawerTitle, drawerTitleParams);
        Button addFolder = button("＋ 添加文件夹");
        Button manageFolders = button("管理文件夹");
        Button refresh = button("刷新图片");
        Button layoutToggle = button(listLayout ? "▦ 网格视图" : "☷ 列表视图");
        for (Button action : new Button[]{addFolder, manageFolders, refresh, layoutToggle}) {
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(50));
            actionParams.topMargin = dp(6);
            drawer.addView(action, actionParams);
        }
        addFolder.setOnClickListener(v -> { drawerLayer.setVisibility(View.GONE); chooseFolder(); });
        manageFolders.setOnClickListener(v -> { drawerLayer.setVisibility(View.GONE); manageFolders(); });
        refresh.setOnClickListener(v -> { drawerLayer.setVisibility(View.GONE); thumbnailCache.evictAll(); scanFolders(); });
        layoutToggle.setOnClickListener(v -> {
            boolean nextList = !preferences.getBoolean("list_layout", false);
            preferences.edit().putBoolean("list_layout", nextList).apply();
            layoutToggle.setText(nextList ? "▦ 网格视图" : "☷ 列表视图");
            grid.setNumColumns(nextList ? 1 : 3);
            adapter.notifyDataSetChanged();
            drawerLayer.setVisibility(View.GONE);
        });
        menu.setOnClickListener(v -> drawerLayer.setVisibility(View.VISIBLE));
        drawerLayer.setOnClickListener(v -> drawerLayer.setVisibility(View.GONE));
        drawer.setOnClickListener(v -> { });
        grid.setOnItemClickListener((parent, view, position, id) -> editItem(adapter.items.get(position)));
    }

    private int cacheSize() {
        // Runtime APIs are safe during field initialization, unlike Activity system services.
        return (int) Math.max(4L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8);
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }

    private int surfaceColor() { return isNight() ? Color.rgb(24, 29, 27) : Color.rgb(246, 248, 245); }

    private void styleInput(EditText input) {
        boolean night = isNight(); input.setTextColor(night ? Color.WHITE : Color.rgb(25, 39, 33));
        input.setHintTextColor(night ? Color.rgb(174, 186, 180) : Color.rgb(111, 123, 116));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)); input.setPadding(dp(16), dp(6), dp(16), dp(6));
    }

    private Button button(String text) {
        Button button = new Button(this); button.setText(text); button.setTextSize(12); button.setAllCaps(false);
        button.setTextColor(isNight() ? Color.WHITE : Color.rgb(35, 83, 65));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isNight() ? Color.rgb(48, 75, 63) : Color.rgb(226, 239, 230)));
        button.setPadding(dp(2), 0, dp(2), 0); return button;
    }


    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        folderLauncher.launch(intent);
    }

    private void handleFolderResult(@Nullable Intent data) {
        if (data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "无法保留此文件夹的读取授权，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        String value = tree.toString();
        if (!roots.contains(value)) roots.add(value);
        saveRoots();
        searchBox.setText("");
        scanFolders();
    }

    private void scanFolders() {
        if (scanning) return;
        if (roots.isEmpty()) {
            chooseFolder();
            return;
        }
        scanning = true;
        ArrayList<String> selectedRoots = new ArrayList<>(roots);
        setBusy(true, "正在扫描 " + selectedRoots.size() + " 个文件夹…");
        worker.execute(() -> {
            int total = 0;
            ArrayList<String> failedRoots = new ArrayList<>();
            for (String selectedRoot : selectedRoots) {
                ArrayList<ImageDatabase.ImageItem> found = new ArrayList<>();
                try {
                    Uri tree = Uri.parse(selectedRoot);
                    scanDocuments(tree, DocumentsContract.getTreeDocumentId(tree), selectedRoot, found);
                    database.replaceScanned(selectedRoot, found);
                    total += found.size();
                } catch (Exception e) {
                    failedRoots.add(selectedRoot);
                }
            }
            int count = total;
            runOnUiThread(() -> {
                scanning = false;
                String message = failedRoots.isEmpty() ? count + " 张图片 · " + selectedRoots.size() + " 个文件夹" : count + " 张图片；" + failedRoots.size() + " 个文件夹暂时无法访问";
                setBusy(false, message);
                if (!failedRoots.isEmpty()) Toast.makeText(this, "部分文件夹扫描失败，保留原有索引；请检查授权后重试", Toast.LENGTH_LONG).show();
                loadResults();
            });
        });
    }

    private void scanDocuments(Uri tree, String parentId, String rootUri, List<ImageDatabase.ImageItem> found) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        try (Cursor cursor = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("文件夹暂时无法读取");
            int idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String mime = cursor.getString(mimeIndex);
                String documentId = cursor.getString(idIndex);
                String name = cursor.getString(nameIndex);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDocuments(tree, documentId, rootUri, found);
                } else if ((mime != null) && mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                    Uri imageUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
                    found.add(new ImageDatabase.ImageItem(imageUri.toString(), rootUri, name, mime, "", ""));
                }
            }
        }
    }

    private void manageFolders() {
        if (roots.isEmpty()) {
            Toast.makeText(this, "还没有添加文件夹", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[roots.size()];
        for (int i = 0; i < roots.size(); i++) labels[i] = folderLabel(roots.get(i));
        new AlertDialog.Builder(this).setTitle("已添加的文件夹")
                .setItems(labels, (dialog, which) -> confirmRemoveFolder(roots.get(which)))
                .setNegativeButton("关闭", null).show();
    }

    private String folderLabel(String root) {
        try {
            Uri uri = Uri.parse(root);
            String id = DocumentsContract.getTreeDocumentId(uri);
            String name = id.substring(id.lastIndexOf(':') + 1);
            return name.isEmpty() ? root : name;
        } catch (Exception e) {
            return root;
        }
    }

    private void confirmRemoveFolder(String root) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("移除文件夹？");
        builder.setMessage(folderLabel(root) + " 的索引和授权将被移除；原始图片不会删除。");
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("移除", (dialog, which) -> worker.execute(() -> {
            database.removeRoot(root);
            runOnUiThread(() -> {
                roots.remove(root);
                saveRoots();
                try {
                    getContentResolver().releasePersistableUriPermission(Uri.parse(root), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                loadResults();
                Toast.makeText(this, "已移除文件夹索引，原图未删除", Toast.LENGTH_SHORT).show();
            });
        }));
        builder.show();
    }

    @SuppressLint("SetTextI18n")
    private void loadResults() {
        if (adapter == null) return;
        ArrayList<String> selectedRoots = new ArrayList<>(roots);
        String query = searchBox.getText().toString();
        boolean regex = preferences.getBoolean("regex_search", false);
        worker.execute(() -> {
            List<ImageDatabase.ImageItem> items;
            try {
                items = database.search(selectedRoots, query, regex);
            } catch (java.util.regex.PatternSyntaxException invalidPattern) {
                runOnUiThread(() -> {
                    status.setText("正则表达式无效：" + invalidPattern.getDescription());
                    adapter.items = new ArrayList<>();
                    adapter.notifyDataSetChanged();
                });
                return;
            }
            runOnUiThread(() -> {
                if (selectedRoots.equals(roots)) {
                    adapter.items = items;
                    adapter.notifyDataSetChanged();
                    if (!items.isEmpty()) status.setText(items.size() + " 张图片 · " + roots.size() + " 个文件夹");
                    else if (!query.isEmpty()) status.setText("没有匹配的图片");
                    else if (roots.isEmpty()) status.setText("添加图片文件夹，开始建立本地索引");
                    else if (!scanning) status.setText("已添加文件夹中没有图片");
                }
            });
        });
    }

    private void editItem(ImageDatabase.ImageItem item) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.setVerticalScrollBarEnabled(true);
        LinearLayout formContent = new LinearLayout(this);
        formContent.setOrientation(LinearLayout.VERTICAL);
        formScroll.addView(formContent, new ScrollView.LayoutParams(-1, -2));
        form.addView(formScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(231, 235, 232));
        preview.setOnClickListener(v -> showImageViewer(item));
        formContent.addView(preview, new LinearLayout.LayoutParams(-1, dp(190)));
        worker.execute(() -> {
            Bitmap bitmap = loadThumbnail(item.uri);
            if (bitmap != null && !isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> preview.setImageBitmap(bitmap));
            }
        });
        Button viewImage = button("查看大图");
        viewImage.setOnClickListener(v -> showImageViewer(item));
        formContent.addView(viewImage, new LinearLayout.LayoutParams(-1, dp(42)));
        EditText description = new EditText(this);
        description.setHint("图片描述");
        description.setMinLines(2);
        description.setGravity(Gravity.TOP | Gravity.START);
        description.setText(item.description);
        styleInput(description);
        formContent.addView(description, new LinearLayout.LayoutParams(-1, -2));

        Button recognize = button("识别文字（端侧）");
        LinearLayout.LayoutParams recognizeParams = new LinearLayout.LayoutParams(-1, dp(42));
        recognizeParams.topMargin = dp(6);
        formContent.addView(recognize, recognizeParams);

        EditText tags = new EditText(this);
        tags.setHint("标签，用逗号分隔");
        tags.setSingleLine(true);
        tags.setText(item.tags);
        styleInput(tags);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(-1, -2);
        tagParams.topMargin = dp(8);
        formContent.addView(tags, tagParams);

        final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
        final Button save = button("无待保存修改");
        save.setEnabled(false);
        final int[] editVersion = {0};
        final int[] savedVersion = {0};
        final Runnable autoSave = () -> {
            final int version = editVersion[0];
            if (version == savedVersion[0]) return;
            String descriptionText = description.getText().toString().trim();
            String normalizedTags = normalizeTags(tags.getText().toString());
            save.setText("正在保存…");
            worker.execute(() -> {
                try {
                    if (!database.updateText(item.uri, descriptionText, normalizedTags)) {
                        throw new IllegalStateException("Image record no longer exists");
                    }
                    runOnUiThread(() -> {
                        savedVersion[0] = version;
                        if (isFinishing() || isDestroyed()) return;
                        if (editVersion[0] == version) {
                            save.setText("已自动保存");
                            save.setEnabled(false);
                        }
                        loadResults();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (editVersion[0] == version) {
                            save.setText("保存失败，重试");
                            save.setEnabled(true);
                        }
                        Toast.makeText(MainActivity.this, "自动保存失败，请重试", Toast.LENGTH_LONG).show();
                    });
                }
            });
        };
        save.setOnClickListener(v -> {
            autoSaveHandler.removeCallbacks(autoSave);
            autoSave.run();
        });
        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                editVersion[0]++;
                save.setText("待保存…");
                save.setEnabled(false);
                autoSaveHandler.removeCallbacks(autoSave);
                autoSaveHandler.postDelayed(autoSave, 500);
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        description.addTextChangedListener(autoSaveWatcher);
        tags.addTextChangedListener(autoSaveWatcher);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        final OcrProvider[] ocrProvider = {null};
        final boolean[] ocrRunning = {false};
        final Bitmap[] ocrBitmap = {null};
        recognize.setOnClickListener(v -> {
            if (ocrRunning[0] || dialogHolder[0] == null || !dialogHolder[0].isShowing()) return;
            ocrRunning[0] = true;
            recognize.setEnabled(false);
            recognize.setText("正在准备图片并识别…");
            worker.execute(() -> {
                Bitmap bitmap;
                try {
                    bitmap = OcrImageLoader.decode(getContentResolver(), Uri.parse(item.uri));
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        ocrRunning[0] = false;
                        if (dialogHolder[0] == null || !dialogHolder[0].isShowing()
                                || isFinishing() || isDestroyed()) {
                            if (ocrProvider[0] != null) {
                                ocrProvider[0].close();
                                ocrProvider[0] = null;
                            }
                            return;
                        }
                        Toast.makeText(this, "图片无法读取或解码，未保存任何内容", Toast.LENGTH_LONG).show();
                        recognize.setEnabled(true);
                        recognize.setText("重试识别文字");
                    });
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || dialogHolder[0] == null
                            || !dialogHolder[0].isShowing()) {
                        bitmap.recycle();
                        ocrRunning[0] = false;
                        return;
                    }
                    if (ocrProvider[0] == null) {
                        ocrProvider[0] = new LocalOcrProvider();
                        activeOcrProvider = ocrProvider[0];
                    }
                    ocrBitmap[0] = bitmap;
                    activeOcrBitmap = bitmap;
                    activeOcrRunning = true;
                    ocrProvider[0].recognize(bitmap, new OcrProvider.Callback() {
                        @Override public void onSuccess(String text) {
                            if (isFinishing() || isDestroyed() || dialogHolder[0] == null
                                    || !dialogHolder[0].isShowing()) return;
                            String recognizedText = OcrTextUtils.normalize(text);
                            if (recognizedText.isEmpty()) {
                                Toast.makeText(MainActivity.this, "没有识别到文字", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String existing = description.getText().toString();
                            EditText recognizedEditor = new EditText(MainActivity.this);
                            recognizedEditor.setText(recognizedText);
                            recognizedEditor.setMinLines(4);
                            recognizedEditor.setMaxLines(10);
                            recognizedEditor.setGravity(Gravity.TOP | Gravity.START);
                            recognizedEditor.setHint("可修改识别出的文字");
                            styleInput(recognizedEditor);
                            LinearLayout editorContainer = new LinearLayout(MainActivity.this);
                            editorContainer.setOrientation(LinearLayout.VERTICAL);
                            editorContainer.setPadding(dp(24), 0, dp(24), 0);
                            editorContainer.addView(recognizedEditor, new LinearLayout.LayoutParams(-1, -2));
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(existing.trim().isEmpty() ? "识别出的文字" : "已有图片描述")
                                    .setMessage("可先编辑识别结果，再选择替换或追加")
                                    .setView(editorContainer)
                                    .setPositiveButton("替换", (d, which) -> {
                                        String editedText = recognizedEditor.getText().toString().trim();
                                        if (!editedText.isEmpty()) {
                                            description.setText(editedText);
                                            description.setSelection(description.length());
                                        }
                                    })
                                    .setNeutralButton("追加", (d, which) -> {
                                        String editedText = recognizedEditor.getText().toString().trim();
                                        if (!editedText.isEmpty()) {
                                            description.setText(OcrTextUtils.append(existing, editedText));
                                            description.setSelection(description.length());
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }

                        @Override public void onFailure(Exception error) {
                            if (!isFinishing() && !isDestroyed() && dialogHolder[0] != null
                                    && dialogHolder[0].isShowing()) {
                                Toast.makeText(MainActivity.this, "文字识别失败，请检查图片后重试", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override public void onComplete() {
                            Bitmap completed = ocrBitmap[0];
                            ocrBitmap[0] = null;
                            activeOcrBitmap = null;
                            if (completed != null && !completed.isRecycled()) completed.recycle();
                            ocrRunning[0] = false;
                            activeOcrRunning = false;
                            if (ocrProvider[0] != null && (isFinishing() || isDestroyed()
                                    || dialogHolder[0] == null || !dialogHolder[0].isShowing())) {
                                ocrProvider[0].close();
                                ocrProvider[0] = null;
                                activeOcrProvider = null;
                            }
                            if (!isFinishing() && !isDestroyed() && dialogHolder[0] != null
                                    && dialogHolder[0].isShowing()) {
                                recognize.setEnabled(true);
                                recognize.setText("再次识别文字（端侧）");
                            }
                        }
                    });
                });
            });
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button remove = button("删除图片");
        remove.setOnClickListener(v -> {
            AlertDialog confirm = new AlertDialog.Builder(this)
                    .setTitle("删除图片？")
                    .setMessage("将从文件夹中永久删除“" + item.name + "”，同时移除本地索引。此操作不可撤销。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        dialogHolder[0].dismiss();
                        worker.execute(() -> {
                            try {
                                DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(item.uri));
                                database.remove(item.uri);
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "图片已从文件夹中删除", Toast.LENGTH_SHORT).show();
                                    loadResults();
                                });
                            } catch (Exception error) {
                                runOnUiThread(() -> Toast.makeText(this,
                                        "删除失败，图片仍保留在文件夹中", Toast.LENGTH_LONG).show());
                            }
                        });
                    })
                    .create();
            confirm.show();
        });
        Button cancel = button("关闭");
        cancel.setOnClickListener(v -> dialogHolder[0].dismiss());
        Button share = button("分享图片");
        share.setOnClickListener(v -> shareImage(item));
        actions.addView(remove, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(52));
        actionParams.topMargin = dp(8);
        form.addView(actions, actionParams);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setView(form)
                .create();
        dialogHolder[0] = dialog;
        dialog.setOnDismissListener(ignored -> {
            autoSaveHandler.removeCallbacks(autoSave);
            autoSave.run();
            if (ocrRunning[0]) return;
            if (ocrProvider[0] != null) {
                ocrProvider[0].close();
                ocrProvider[0] = null;
            }
            activeOcrProvider = null;
            if (ocrBitmap[0] != null && !ocrBitmap[0].isRecycled()) {
                ocrBitmap[0].recycle();
                ocrBitmap[0] = null;
            }
            activeOcrBitmap = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            // This dialog owns a separate window; the Activity's resize mode does not apply here.
            WindowCompat.setDecorFitsSystemWindows(dialog.getWindow(), true);
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(-1, -1);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showImageViewer(ImageDatabase.ImageItem item) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.MATRIX);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView close = viewerControl("×");
        TextView rotate = viewerControl("↻");
        TextView reset = viewerControl("适配");
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END);
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(dp(56), dp(52), Gravity.BOTTOM | Gravity.END);
        FrameLayout.LayoutParams resetParams = new FrameLayout.LayoutParams(dp(72), dp(52), Gravity.BOTTOM | Gravity.START);
        root.addView(close, closeParams);
        root.addView(rotate, rotateParams);
        root.addView(reset, resetParams);
        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final android.graphics.Matrix matrix = new android.graphics.Matrix();
        final android.graphics.drawable.Drawable[] drawableHolder = new android.graphics.drawable.Drawable[1];
        final float[] scale = {1f};
        final int[] rotation = {0};
        Runnable fit = () -> {
            android.graphics.drawable.Drawable drawable = drawableHolder[0];
            if (drawable == null || image.getWidth() == 0 || image.getHeight() == 0) return;
            int sourceWidth = drawable.getIntrinsicWidth();
            int sourceHeight = drawable.getIntrinsicHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) return;
            matrix.reset();
            boolean sideways = rotation[0] % 180 != 0;
            float rotatedWidth = sideways ? sourceHeight : sourceWidth;
            float rotatedHeight = sideways ? sourceWidth : sourceHeight;
            float factor = Math.min(image.getWidth() / rotatedWidth, image.getHeight() / rotatedHeight);
            matrix.postRotate(rotation[0]);
            matrix.postScale(factor, factor);
            android.graphics.RectF rect = new android.graphics.RectF(0, 0, sourceWidth, sourceHeight);
            matrix.mapRect(rect);
            matrix.postTranslate((image.getWidth() - rect.width()) / 2f - rect.left, (image.getHeight() - rect.height()) / 2f - rect.top);
            scale[0] = 1f;
            image.setImageMatrix(matrix);
        };
        android.view.ScaleGestureDetector pinch = new android.view.ScaleGestureDetector(this,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(@NonNull android.view.ScaleGestureDetector detector) {
                        float next = Math.max(1f, Math.min(8f, scale[0] * detector.getScaleFactor()));
                        float factor = next / scale[0];
                        matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                        scale[0] = next;
                        image.setImageMatrix(matrix);
                        return true;
                    }
                });
        android.view.GestureDetector gestures = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onScroll(android.view.MotionEvent e1, @NonNull android.view.MotionEvent e2, float dx, float dy) {
                if (scale[0] > 1f) { matrix.postTranslate(-dx, -dy); image.setImageMatrix(matrix); }
                return true;
            }
            @Override public boolean onDoubleTap(@NonNull android.view.MotionEvent e) {
                if (scale[0] > 1f) fit.run();
                else { matrix.postScale(2f, 2f, e.getX(), e.getY()); scale[0] = 2f; image.setImageMatrix(matrix); }
                return true;
            }
        });
        image.setOnTouchListener((v, event) -> { pinch.onTouchEvent(event); gestures.onTouchEvent(event); return true; });
        image.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit.run());
        rotate.setOnClickListener(v -> { rotation[0] = (rotation[0] + 90) % 360; fit.run(); });
        reset.setOnClickListener(v -> { rotation[0] = 0; fit.run(); });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(-1, -1);
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.getDecorView().setPadding(0, 0, 0, 0);
                WindowCompat.setDecorFitsSystemWindows(window, false);
                WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.systemBars());
                ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                    Insets safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    closeParams.setMargins(0, safe.top, safe.right, 0);
                    rotateParams.setMargins(0, 0, safe.right, safe.bottom);
                    resetParams.setMargins(safe.left, 0, 0, safe.bottom);
                    close.setLayoutParams(closeParams); rotate.setLayoutParams(rotateParams); reset.setLayoutParams(resetParams);
                    return insets;
                });
            }
        });
        dialog.setOnDismissListener(d -> {
            if (drawableHolder[0] instanceof pl.droidsonroids.gif.GifDrawable) {
                ((pl.droidsonroids.gif.GifDrawable) drawableHolder[0]).stop();
            } else if (android.os.Build.VERSION.SDK_INT >= 28
                    && drawableHolder[0] instanceof android.graphics.drawable.AnimatedImageDrawable) {
                ((android.graphics.drawable.AnimatedImageDrawable) drawableHolder[0]).stop();
            }
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
        worker.execute(() -> {
            android.graphics.drawable.Drawable drawable = loadViewerDrawable(item,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            if (drawable != null) runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    drawableHolder[0] = drawable;
                    image.setImageDrawable(drawable);
                    fit.run();
                    if (drawable instanceof pl.droidsonroids.gif.GifDrawable) {
                        ((pl.droidsonroids.gif.GifDrawable) drawable).start();
                    } else if (android.os.Build.VERSION.SDK_INT >= 28
                            && drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                        ((android.graphics.drawable.AnimatedImageDrawable) drawable).start();
                    }
                }
            });
        });
    }

    private TextView viewerControl(String label) {
        TextView control = new TextView(this);
        control.setText(label);
        control.setTextColor(Color.WHITE);
        control.setTextSize(20);
        control.setGravity(Gravity.CENTER);
        control.setBackgroundColor(0x99000000);
        control.setClickable(true);
        return control;
    }

    private android.graphics.drawable.Drawable loadViewerDrawable(ImageDatabase.ImageItem item, int width, int height) {
        Uri uri = Uri.parse(item.uri);
        if ("GIF".equals(imageType(item))) {
            try {
                pl.droidsonroids.gif.GifDrawable gif = new pl.droidsonroids.gif.GifDrawable(getContentResolver(), uri);
                return gif;
            } catch (Exception ignored) { return null; }
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(getContentResolver(), uri);
                return android.graphics.ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    int sample = Math.max(1, Math.min(info.getSize().getWidth() / Math.max(1, width),
                            info.getSize().getHeight() / Math.max(1, height)));
                    decoder.setTargetSampleSize(sample);
                });
            } catch (Exception ignored) { return null; }
        }
        Bitmap bitmap = loadViewerBitmap(item.uri, width, height);
        return bitmap == null ? null : new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private Bitmap loadViewerBitmap(String value, int width, int height) {
        Uri uri = Uri.parse(value);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= width || bounds.outHeight / (sample * 2) >= height) sample *= 2;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Exception ignored) { return null; }
    }

    private String imageType(ImageDatabase.ImageItem item) {
        String mime = item.mime == null ? "" : item.mime.trim().toLowerCase(java.util.Locale.ROOT);
        if (mime.equals("image/png")) return "PNG";
        if (mime.equals("image/jpeg") || mime.equals("image/jpg")) return "JPEG";
        if (mime.equals("image/gif")) return "GIF";
        String name = item.name == null ? "" : item.name;
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (extension.equals("png")) return "PNG";
        if (extension.equals("jpg") || extension.equals("jpeg")) return "JPEG";
        if (extension.equals("gif")) return "GIF";
        String inferredMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if ("image/png".equals(inferredMime)) return "PNG";
        if ("image/jpeg".equals(inferredMime)) return "JPEG";
        if ("image/gif".equals(inferredMime)) return "GIF";
        return "图片";
    }

    private void shareImage(ImageDatabase.ImageItem item) {
        worker.execute(() -> {
            boolean readable;
            try (InputStream input = getContentResolver().openInputStream(Uri.parse(item.uri))) {
                readable = input != null;
            } catch (Exception e) {
                readable = false;
            }
            boolean canRead = readable;
            runOnUiThread(() -> {
                if (!canRead) {
                    Toast.makeText(this, "图片暂时无法读取，无法分享", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    Uri uri = Uri.parse(item.uri);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(item.mime == null || item.mime.isEmpty() ? "image/*" : item.mime);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(android.content.ClipData.newUri(getContentResolver(), item.name, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "分享图片"));
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开分享面板", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String normalizeTags(String value) {
        String[] pieces = value.split("[,，;；\\n]");
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (String piece : pieces) {
            String tag = piece.trim();
            if (!tag.isEmpty()) unique.add(tag);
        }
        return android.text.TextUtils.join(", ", unique);
    }

    private void setBusy(boolean busy, String message) {
        status.setText(message);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setStatus() { status.setText("添加图片文件夹，开始建立本地索引"); }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (!activeOcrRunning) {
            if (activeOcrProvider != null) activeOcrProvider.close();
            activeOcrProvider = null;
            if (activeOcrBitmap != null && !activeOcrBitmap.isRecycled()) activeOcrBitmap.recycle();
            activeOcrBitmap = null;
        }
        super.onDestroy();
        worker.shutdown();
        database.close();
    }

    private final class ImageAdapter extends BaseAdapter {
        List<ImageDatabase.ImageItem> items = new ArrayList<>();
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).uri.hashCode(); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            boolean listLayout = preferences.getBoolean("list_layout", false);
            ImageDatabase.ImageItem item = items.get(position);
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(listLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(listLayout ? 6 : 5), dp(listLayout ? 3 : 5), dp(listLayout ? 6 : 5), dp(listLayout ? 3 : 5));
            cell.setBackground(rounded(isNight() ? Color.rgb(42, 48, 45) : Color.WHITE, dp(listLayout ? 10 : 12)));
            cell.setClipToOutline(true);
            ImageView image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(isNight() ? Color.rgb(45, 51, 48) : Color.rgb(231, 235, 232));
            LinearLayout.LayoutParams imageParams = listLayout
                    ? new LinearLayout.LayoutParams(dp(56), dp(56))
                    : new LinearLayout.LayoutParams(-1, dp(92));
            cell.addView(image, imageParams);
            TextView caption = new TextView(MainActivity.this);
            String type = imageType(item);
            caption.setText((item.description.isEmpty() ? item.name : item.description)
                    + " · " + type);
            caption.setTextSize(listLayout ? 13 : 11);
            caption.setTextColor(isNight() ? Color.WHITE : Color.rgb(35, 46, 40));
            caption.setMaxLines(2);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            caption.setPadding(dp(listLayout ? 8 : 5), dp(listLayout ? 2 : 5), dp(5), dp(listLayout ? 2 : 5));
            LinearLayout.LayoutParams captionParams = listLayout
                    ? new LinearLayout.LayoutParams(0, -2, 1)
                    : new LinearLayout.LayoutParams(-1, -2);
            cell.addView(caption, captionParams);
            image.setTag(item.uri);
            image.setImageResource(android.R.drawable.ic_menu_gallery);
            worker.execute(() -> {
                Bitmap bitmap = loadThumbnail(item.uri);
                runOnUiThread(() -> {
                    if (bitmap != null && item.uri.equals(image.getTag())) image.setImageBitmap(bitmap);
                });
            });
            return cell;
        }
    }

    private void trimThumbnailDiskCache(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= 256L * 1024 * 1024) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= 192L * 1024 * 1024) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private File thumbnailCacheFile(String uri) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uri.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(digest.length * 2);
            for (byte value : digest) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return new File(new File(getCacheDir(), "photo_thumbnails"), name + ".jpg");
        } catch (Exception e) {
            return new File(new File(getCacheDir(), "photo_thumbnails"), Integer.toHexString(uri.hashCode()) + ".jpg");
        }
    }

    private Bitmap loadThumbnail(String value) {
        Bitmap cached = thumbnailCache.get(value);
        if (cached != null && !cached.isRecycled()) return cached;
        File diskFile = thumbnailCacheFile(value);
        if (diskFile.isFile()) {
            Bitmap diskBitmap = BitmapFactory.decodeFile(diskFile.getAbsolutePath());
            if (diskBitmap != null) {
                // Touch the entry so least-recently-used disk-cache cleanup preserves it longer.
                if (!diskFile.setLastModified(System.currentTimeMillis())) {
                    // Some providers/filesystems do not support changing timestamps; cache remains valid.
                }
                thumbnailCache.put(value, diskBitmap);
                return diskBitmap;
            }
            // Remove an incomplete or corrupt cache entry and regenerate it from source.
            //noinspection ResultOfMethodCallIgnored
            diskFile.delete();
        }
        Uri uri = Uri.parse(value);
        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 480) sample *= 2;
            try (InputStream imageStream = getContentResolver().openInputStream(uri)) {
                if (imageStream == null) return null;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
                if (bitmap != null) {
                    thumbnailCache.put(value, bitmap);
                    File parent = diskFile.getParentFile();
                    if (parent != null && (parent.isDirectory() || parent.mkdirs())) {
                        File temporary = new File(parent, diskFile.getName() + ".tmp");
                        try (FileOutputStream output = new FileOutputStream(temporary)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
                            output.flush();
                            if (temporary.renameTo(diskFile)) trimThumbnailDiskCache(parent);
                            else {
                                //noinspection ResultOfMethodCallIgnored
                                temporary.delete();
                            }
                        } catch (Exception ignored) {
                            // Disk caching is an optimization; displaying the decoded bitmap still works.
                        }
                    }
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
