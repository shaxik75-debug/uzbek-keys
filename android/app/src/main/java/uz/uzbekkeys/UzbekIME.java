package uz.uzbekkeys;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Uzbek Keys keyboard, same look and behaviour as the Apple keyboard. by işax
 * Keyboards: Yangi lotin (o' -> ö, g' -> ğ, sh -> ş, ch -> ç), Oʻzbekcha (o' -> oʻ, g' -> gʻ),
 * Ўзбекча (Cyrillic), Русский, English; the flag key switches between the ones turned on in the app.
 * aa / Aa / AA -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom", rr -> "Rahmat" (Cyrillic: аа, вв, рр).
 * Backspace right after a replacement brings back what was typed; the space bar shows the last replacement.
 * Word suggestions from the words the user types, emoji panel, key colour / shape / height / weight,
 * key click volume, key popups, fast typing with several fingers, long-press alternates,
 * double space = ". ", hold space and slide to move the cursor, hold 123 and slide to a symbol.
 */
public class UzbekIME extends InputMethodService {

    private static final String[] LAT_UZ = {"qwertyuiop", "asdfghjkl'", "zxcvbnm"};
    private static final String[] LAT_EN = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final String[] CYR_UZ = {"йцукенгшўзх", "фқвапролджэ", "ячсмитғбюҳ"};
    private static final String[] CYR_RU = {"йцукенгшщзх", "фывапролджэ", "ячсмитьбю"};
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""},
            {".", ",", "?", "!", "'"}
    };
    private static final String[][] SYMBOLS2 = {
            {"[", "]", "{", "}", "#", "%", "^", "*", "+", "="},
            {"_", "\\", "|", "~", "<", ">", "€", "£", "₽", "•"},
            {".", ",", "?", "!", "'"}
    };
    private static final String[][] LAT_PHRASES = {
            {"aa", "Assalomu Alaykum"}, {"vv", "Va Alaykum Assalom"}, {"rr", "Rahmat"}
    };
    private static final String[][] CYR_PHRASES = {
            {"аа", "Ассалому алайкум"}, {"вв", "Ва алайкум ассалом"}, {"рр", "Раҳмат"}
    };

    private static final int MODE_EMOJI = 3;
    private static final int EMOJI_COLS = 8;

    private static final int BG_DARK = 0xFF2C2C2E;
    private static final int BG_LIGHT = 0xFFD3D6DC;
    private static final int KEY_DARK = 0xFF6C6C6E;
    private static final int KEY_LIGHT = 0xFFFFFFFF;
    private static final int PRESSED_DARK = 0xFF9A9A9C;
    private static final int PRESSED_LIGHT = 0xFFC4C7CD;
    private static final int BLUE = 0xFF0A84FF;
    private static final int BLUE_PRESSED = 0xFF4FA6FF;
    private static final int GREEN = 0xFF34C759;

    private static final class Key {
        final String id;
        final RectF r = new RectF();
        boolean pressed;

        Key(String id) {
            this.id = id;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<List<Key>> rows = new ArrayList<>();

    private SharedPreferences prefs;
    private KeyPrefs kp;
    private WordMemory memory;
    private AudioManager audio;
    private LinearLayout root;
    private KeyboardView kv;
    private TextView banner;

    private int mode = 0;          // 0 letters, 1 symbols, 2 more symbols, 3 emoji
    private int shift = 0;         // 0 off, 1 next letter, 2 caps lock
    private long lastShiftTap = 0;
    private long lastSpaceTap = 0;
    private boolean enabled = true;
    private String lastOriginal = null;
    private String lastReplacement = null;
    private String toggleNotice = null;

    // Settings (from the app).
    private String lang = "YA";
    private int langCount = 1;
    private boolean phrasesOn = true;
    private boolean suggestOn = true;
    private int shapeIdx = 0;
    private int heightIdx = 1;
    private int themeMode = 0;
    private int volume = 40;
    private int cmode = KeyPrefs.COLOR_APPLE;
    private int oneColor = KEY_LIGHT;
    private int oneSp = KEY_LIGHT;
    private HashMap<String, Integer> custom = new HashMap<>();
    private Typeface letterFace = Typeface.DEFAULT;
    private Typeface specialFace = Typeface.DEFAULT;

    // Suggestions bar.
    private List<String> suggestions = new ArrayList<>();
    private int barPointer = -1;
    private int barSlot = -1;

    // Emoji panel.
    private int emojiCat = 1;
    private float emojiScroll = 0;
    private int emojiPointer = -1;
    private int emojiTab = -1;
    private float emojiDownY = 0;
    private float emojiLastY = 0;
    private boolean emojiScrolling = false;

    // Touch tracking (one entry per finger).
    private final SparseArray<Key> pointerKeys = new SparseArray<>();
    private final HashSet<Integer> armed = new HashSet<>();   // fingers that type their key when lifted
    private int modePointer = -1;
    private int altPointer = -1;
    private int cursorPointer = -1;
    private int holdPointer = -1;
    private String altText = null;
    private float cursorX = 0;
    private Runnable holdRunnable = null;
    private Key popupKey = null;
    private String popupText = "";
    private boolean popupHighlight = false;

    private long deleteInterval = 100;
    private int deleteCount = 0;
    private final Runnable deleteRepeat = new Runnable() {
        @Override
        public void run() {
            doDelete();
            afterEdit();
            deleteCount++;
            if (deleteCount == 15) deleteInterval = 50;
            handler.postDelayed(this, deleteInterval);
        }
    };

    private float gridTop, gridKeyH, gridGap;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (key == null || key.equals("recent") || key.equals("remote") || key.equals("lastCheck")) return;
                    if (key.equals("memClear")) {
                        memory.clearCache();
                        suggestions = new ArrayList<>();
                        invalidateKeys();
                        return;
                    }
                    loadSettings();
                    if (root != null) root.setBackgroundColor(isDark() ? BG_DARK : BG_LIGHT);
                    buildKeys();
                    if (kv != null) kv.requestLayout();
                }
            };

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("uk", MODE_PRIVATE);
        kp = new KeyPrefs(this);
        memory = new WordMemory(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        loadSettings();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        memory.save();
        super.onDestroy();
    }

    private void loadSettings() {
        langCount = kp.enabledLangs().size();
        lang = kp.lang();
        enabled = kp.replace();
        phrasesOn = kp.phrases();
        suggestOn = kp.suggest();
        shapeIdx = kp.shape();
        heightIdx = kp.height();
        themeMode = kp.theme();
        volume = kp.volume();
        cmode = kp.colorMode();
        oneColor = kp.oneColor();
        oneSp = kp.oneSpecial();
        custom = kp.customColors();
        int w = kp.weight();
        letterFace = face(w);
        specialFace = face(Math.min(w, 500));
    }

    private static Typeface face(int weight) {
        if (weight >= 600) return Typeface.DEFAULT_BOLD;
        if (weight >= 500) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (weight <= 300) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        return Typeface.DEFAULT;
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        banner = new TextView(this);
        banner.setTextColor(Color.WHITE);
        banner.setBackgroundColor(0xFF2E7D32);
        banner.setGravity(Gravity.CENTER);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int bp = (int) dp(8);
        banner.setPadding(bp, bp, bp, bp);
        banner.setText("Yangi versiya bor — körish / Есть обновление — посмотреть");
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Opens the app: it shows what is new in the update and the download button.
                Intent i = new Intent(UzbekIME.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (Exception e) {
                    Updater.openDownload(UzbekIME.this);
                }
                requestHideSelf(0);
            }
        });
        root.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        kv = new KeyboardView(this);
        root.addView(kv, new LinearLayout.LayoutParams(-1, -2));
        buildKeys();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        loadSettings();
        lastOriginal = null;
        lastReplacement = null;
        toggleNotice = null;
        selStart = info.initialSelStart;
        selEnd = info.initialSelEnd;
        resetCache(selStart);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        mode = (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) ? 1 : 0;
        if (shift == 2) shift = 0;
        buildKeys();
        if (kv != null) kv.requestLayout();
        updateAutoShift();
        updateSuggestions();
        if (root != null) root.setBackgroundColor(isDark() ? BG_DARK : BG_LIGHT);
        Updater.maybeCheck(this, new Updater.Callback() {
            @Override
            public void onResult(int remote, int local) {
                if (banner != null) banner.setVisibility(remote > local ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        handler.removeCallbacks(deleteRepeat);
        cancelHold();
        pointerKeys.clear();
        armed.clear();
        modePointer = altPointer = cursorPointer = -1;
        barPointer = barSlot = -1;
        emojiPointer = emojiTab = -1;
        popupKey = null;
        memory.save();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        selStart = newSelStart;
        selEnd = newSelEnd;
        if (newSelStart == newSelEnd && pending.contains(newSelStart)) {
            // The cursor moved because of our own typing: the remembered text is still right.
            while (!pending.isEmpty() && pending.peekFirst() != newSelStart) pending.pollFirst();
            pending.pollFirst();
            return;
        }
        // Moved by the user or the app: read the text again next time it is needed.
        resetCache(newSelStart);
        updateAutoShift();
        updateSuggestions();
        invalidateKeys();
    }

    // ---------------------------------------------------------------- helpers

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private boolean isDark() {
        if (themeMode == 1) return false;
        if (themeMode == 2) return true;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isCyrillic() {
        return lang.equals("UZ") || lang.equals("RU");
    }

    private boolean hasToggle() {
        return lang.equals("YA") || lang.equals("OZ");
    }

    private boolean offerNext() {
        return Build.VERSION.SDK_INT < 28 || shouldOfferSwitchingToNextInputMethod();
    }

    private static boolean isCharacter(String id) {
        return id.length() == 1;
    }

    private String alternate(String id) {
        switch (lang) {
            case "YA":
                switch (id) {
                    case "o": return "ö";
                    case "g": return "ğ";
                    case "s": return "ş";
                    case "c": return "ç";
                    default: return null;
                }
            case "OZ":
                switch (id) {
                    case "o": return "oʻ";
                    case "g": return "gʻ";
                    default: return null;
                }
            case "UZ":
                switch (id) {
                    case "е": return "ё";
                    case "х": return "ъ";
                    case "ш": return "щ";
                    case "и": return "ы";
                    case "б": return "ь";
                    default: return null;
                }
            case "RU":
                switch (id) {
                    case "е": return "ё";
                    case "ь": return "ъ";
                    default: return null;
                }
            default:
                return null;
        }
    }

    private void invalidateKeys() {
        if (kv != null) kv.invalidate();
    }

    private String[] letterRows() {
        switch (lang) {
            case "UZ": return CYR_UZ;
            case "RU": return CYR_RU;
            case "EN": return LAT_EN;
            default: return LAT_UZ;
        }
    }

    private static List<Key> keysOf(String s) {
        List<Key> row = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) row.add(new Key(String.valueOf(s.charAt(i))));
        return row;
    }

    private static List<Key> keysOf(String[] ids) {
        List<Key> row = new ArrayList<>();
        for (String id : ids) row.add(new Key(id));
        return row;
    }

    private void buildKeys() {
        rows.clear();
        popupKey = null;
        if (mode != MODE_EMOJI) {
            if (mode == 0) {
                String[] src = letterRows();
                rows.add(keysOf(src[0]));
                rows.add(keysOf(src[1]));
                List<Key> r3 = new ArrayList<>();
                r3.add(new Key("SHIFT"));
                r3.addAll(keysOf(src[2]));
                r3.add(new Key("DEL"));
                rows.add(r3);
            } else {
                String[][] src = mode == 1 ? SYMBOLS : SYMBOLS2;
                rows.add(keysOf(src[0]));
                rows.add(keysOf(src[1]));
                List<Key> r3 = new ArrayList<>();
                r3.add(new Key(mode == 1 ? "SYM2" : "SYM1"));
                r3.addAll(keysOf(src[2]));
                r3.add(new Key("DEL"));
                rows.add(r3);
            }
            List<Key> bottom = new ArrayList<>();
            bottom.add(new Key(mode == 0 ? "SYM1" : "ABC"));
            if (langCount > 1 || offerNext()) bottom.add(new Key("LANG"));
            bottom.add(new Key("EMOJI"));
            if (hasToggle()) bottom.add(new Key("TOGGLE"));
            bottom.add(new Key("SPACE"));
            bottom.add(new Key("RET"));
            rows.add(bottom);
        }
        if (kv != null) {
            kv.layoutKeys(kv.getWidth(), kv.getHeight());
            kv.invalidate();
        }
    }

    private Key keyAt(float x, float y) {
        if (rows.isEmpty()) return null;
        int r = (int) Math.floor((y - gridTop + gridGap / 2f) / (gridKeyH + gridGap));
        r = Math.min(Math.max(r, 0), rows.size() - 1);
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (Key k : rows.get(r)) {
            float d = x < k.r.left ? k.r.left - x : (x > k.r.right ? x - k.r.right : 0);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    private void playClick(String id) {
        if (volume <= 0 || audio == null) return;
        int fx = AudioManager.FX_KEYPRESS_STANDARD;
        if ("DEL".equals(id)) fx = AudioManager.FX_KEYPRESS_DELETE;
        else if ("SPACE".equals(id)) fx = AudioManager.FX_KEYPRESS_SPACEBAR;
        else if ("RET".equals(id)) fx = AudioManager.FX_KEYPRESS_RETURN;
        try {
            audio.playSoundEffect(fx, volume / 100f);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- labels

    private String label(String id) {
        switch (id) {
            case "SHIFT": return shift == 2 ? "⇪" : (shift == 1 ? "⬆" : "⇧");
            case "DEL": return "⌫";
            case "LANG": {
                if (langCount <= 1) return "🌐";
                int i = KeyPrefs.langIndex(lang);
                return KeyPrefs.LANG_FLAGS[i] + " " + KeyPrefs.LANG_SHORT[i];
            }
            case "EMOJI": return "😊";
            case "TOGGLE": return "öğ";
            case "SPACE": return spaceHint();
            case "RET": return returnLabel();
            case "SYM1": return "123";
            case "SYM2": return "#+=";
            case "ABC": return isCyrillic() ? "АБВ" : "ABC";
            default:
                return (mode == 0 && shift > 0) ? id.toUpperCase(Locale.ROOT) : id;
        }
    }

    private String spaceHint() {
        if (toggleNotice != null) return toggleNotice;
        if (lastReplacement != null && lastOriginal != null) {
            return lastOriginal.trim() + " → " + lastReplacement.trim();
        }
        return KeyPrefs.LANG_NAMES[KeyPrefs.langIndex(lang)];
    }

    private int imeAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return EditorInfo.IME_ACTION_NONE;
        if ((ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return EditorInfo.IME_ACTION_NONE;
        if ((ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) return EditorInfo.IME_ACTION_NONE;
        return ei.imeOptions & EditorInfo.IME_MASK_ACTION;
    }

    private String returnLabel() {
        boolean cyr = isCyrillic();
        switch (imeAction()) {
            case EditorInfo.IME_ACTION_GO: return cyr ? "перейти" : "go";
            case EditorInfo.IME_ACTION_SEARCH: return cyr ? "поиск" : "search";
            case EditorInfo.IME_ACTION_SEND: return cyr ? "отпр." : "send";
            case EditorInfo.IME_ACTION_DONE: return cyr ? "готово" : "done";
            case EditorInfo.IME_ACTION_NEXT: return cyr ? "далее" : "next";
            case EditorInfo.IME_ACTION_PREVIOUS: return cyr ? "назад" : "prev";
            default: return "↵";
        }
    }

    private boolean returnIsBlue() {
        int a = imeAction();
        return a == EditorInfo.IME_ACTION_GO || a == EditorInfo.IME_ACTION_SEARCH
                || a == EditorInfo.IME_ACTION_SEND || a == EditorInfo.IME_ACTION_DONE;
    }

    // ---------------------------------------------------------------- suggestions

    private String suggestionForSlot(int slot) {
        int idx = slot == 1 ? 0 : (slot == 0 ? 1 : 2);   // best guess in the middle, like Apple
        return slot >= 0 && idx < suggestions.size() ? suggestions.get(idx) : null;
    }

    private static String trailingWord(String b) {
        int i = b.length();
        while (i > 0 && WordMemory.isWordChar(b.charAt(i - 1))) i--;
        while (i < b.length() && !Character.isLetter(b.charAt(i))) i++;
        return b.substring(i);
    }

    private void updateSuggestions() {
        List<String> out = new ArrayList<>();
        if (suggestOn && mode == 0 && !hasSelection()) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                String w = trailingWord(before(ic));
                if (!w.isEmpty()) {
                    boolean cap = Character.isUpperCase(w.charAt(0));
                    for (String s : memory.suggest(lang, w, 3)) {
                        out.add(cap ? s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1) : s);
                    }
                }
            }
        }
        suggestions = out;
    }

    private void learnBefore(String b) {
        if (!suggestOn) return;
        String w = trailingWord(b);
        if (w.length() < 2 || phraseFor(w) != null) return;
        memory.learn(lang, w);
    }

    private void pickSuggestion(int slot) {
        String word = suggestionForSlot(slot);
        InputConnection ic = getCurrentInputConnection();
        if (word == null || ic == null) return;
        String w = trailingWord(before(ic));
        ic.beginBatchEdit();
        if (w.length() > 0) cachedDelete(ic, w.length());
        cachedCommit(ic, word + " ");
        ic.endBatchEdit();
        markPending();
        lastOriginal = null;
        lastReplacement = null;
        toggleNotice = null;
        memory.learn(lang, word);
        playClick("x");
        if (shift == 1) setShift(0);
        afterEdit();
    }

    // ---------------------------------------------------------------- emoji

    private String[] emojiList(int cat) {
        return cat == 0 ? kp.recentEmoji() : Emojis.list(cat);
    }

    private void openEmoji() {
        mode = MODE_EMOJI;
        emojiCat = kp.recentEmoji().length > 0 ? 0 : 1;
        emojiScroll = 0;
        emojiPointer = -1;
        emojiTab = -1;
        toggleNotice = null;
        pointerKeys.clear();
        armed.clear();
        buildKeys();
    }

    private void closeEmoji() {
        mode = 0;
        buildKeys();
        updateAutoShift();
        updateSuggestions();
    }

    private void typeEmoji(String em) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastOriginal = null;
        lastReplacement = null;
        if (hasSelection()) {
            ic.commitText(em, 1);
            forgetCache();
        } else {
            cachedCommit(ic, em);
            markPending();
        }
        kp.addRecentEmoji(em);
    }

    // ---------------------------------------------------------------- view

    private final class KeyboardView extends View {
        private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final Paint.FontMetrics fm = new Paint.FontMetrics();

        KeyboardView(Context c) {
            super(c);
            textPaint.setTextAlign(Paint.Align.CENTER);
            capPaint.setTextAlign(Paint.Align.LEFT);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(0.7f));
            setHapticFeedbackEnabled(true);
        }

        private float keyHeight() {
            if (isLandscape()) return dp(33);
            return dp(heightIdx == 0 ? 36 : (heightIdx == 2 ? 50 : 42));
        }

        private float barH() {
            return suggestOn ? dp(isLandscape() ? 30 : 38) : 0;
        }

        private float emojiCapH() {
            return dp(26);
        }

        private float emojiTabsH() {
            return dp(44);
        }

        private float emojiCellH() {
            return dp(46);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            boolean land = isLandscape();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            float h = barH() + dp(land ? 6 : 10) + dp(land ? 4 : 6) + 3 * dp(land ? 7 : 11) + 4 * keyHeight();
            setMeasuredDimension(width, (int) Math.ceil(h));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutKeys(w, h);
        }

        void layoutKeys(int W, int H) {
            if (W <= 0 || H <= 0 || rows.size() < 4) return;
            boolean land = isLandscape();
            float side = dp(land ? 5 : 6);
            float g10 = dp(6);
            float bar = barH();
            float topPad = dp(land ? 6 : 10);
            float bottomPad = dp(land ? 4 : 6);
            float rowGap = dp(land ? 7 : 11);
            float keyH = (H - bar - topPad - bottomPad - 3 * rowGap) / 4f;
            int cols = Math.max(10, Math.max(rows.get(0).size(), rows.get(1).size()));
            float gap = cols > 10 ? dp(4.5f) : g10;
            float unit = (W - 2 * side - (cols - 1) * gap) / cols;
            float unit10 = (W - 2 * side - 9 * g10) / 10f;
            gridTop = bar + topPad;
            gridKeyH = keyH;
            gridGap = rowGap;
            for (int r = 0; r < rows.size(); r++) {
                List<Key> row = rows.get(r);
                float y = gridTop + r * (keyH + rowGap);
                int n = row.size();
                if (r < 2) {
                    float x = (W - (n * unit + (n - 1) * gap)) / 2f;
                    for (Key k : row) {
                        k.r.set(x, y, x + unit, y + keyH);
                        x += unit + gap;
                    }
                } else if (r == 2) {
                    int m = n - 2;
                    if (mode == 0) {
                        float wide = unit10 * 1.3f + dp(2);
                        float lw = unit;
                        float need = m * lw + (m - 1) * gap + 2 * wide + 2 * g10 + 2 * side;
                        if (need > W) {
                            wide = Math.max(unit, wide - (need - W) / 2f);
                            need = m * lw + (m - 1) * gap + 2 * wide + 2 * gap + 2 * side;
                            if (need > W) lw = (W - 2 * side - 2 * wide - (m + 1) * gap) / m;
                        }
                        row.get(0).r.set(side, y, side + wide, y + keyH);
                        float x = (W - (m * lw + (m - 1) * gap)) / 2f;
                        for (int i = 1; i <= m; i++) {
                            row.get(i).r.set(x, y, x + lw, y + keyH);
                            x += lw + gap;
                        }
                        row.get(n - 1).r.set(W - side - wide, y, W - side, y + keyH);
                    } else {
                        float wide = unit10 * 1.3f + dp(2);
                        float inner = W - 2 * side - 2 * wide - 4 * g10;
                        float w = (inner - (m - 1) * g10) / m;
                        row.get(0).r.set(side, y, side + wide, y + keyH);
                        float x = side + wide + 2 * g10;
                        for (int i = 1; i <= m; i++) {
                            row.get(i).r.set(x, y, x + w, y + keyH);
                            x += w + g10;
                        }
                        row.get(n - 1).r.set(W - side - wide, y, W - side, y + keyH);
                    }
                } else {
                    float[] fixed = new float[n];
                    float used = (n - 1) * g10;
                    for (int i = 0; i < n; i++) {
                        String id = row.get(i).id;
                        float w;
                        switch (id) {
                            case "SPACE": w = 0; break;
                            case "RET": w = unit10 * 1.9f; break;
                            case "LANG": w = unit10 * (langCount > 1 ? 1.5f : 1.1f); break;
                            case "EMOJI":
                            case "TOGGLE": w = unit10 * 1.05f; break;
                            default: w = unit10 * 1.25f;
                        }
                        fixed[i] = w;
                        used += w;
                    }
                    float spaceW = Math.max(unit10, W - 2 * side - used);
                    float x = side;
                    for (int i = 0; i < n; i++) {
                        Key k = row.get(i);
                        float w = k.id.equals("SPACE") ? spaceW : fixed[i];
                        k.r.set(x, y, x + w, y + keyH);
                        x += w + g10;
                    }
                }
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean dark = isDark();
            c.drawColor(dark ? BG_DARK : BG_LIGHT);
            if (mode == MODE_EMOJI) {
                drawEmoji(c, dark);
                return;
            }
            if (barH() > 0) drawBar(c, dark);
            int count = 0;
            for (List<Key> row : rows) count += row.size();
            int idx = 0;
            for (List<Key> row : rows) {
                for (Key k : row) drawKey(c, k, dark, idx++, count);
            }
            if (popupKey != null) drawPopup(c, dark);
        }

        private void drawBar(Canvas c, boolean dark) {
            float bh = barH();
            int fg = dark ? Color.WHITE : Color.BLACK;
            float cw = getWidth() / 3f;
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(sp(16));
            textPaint.getFontMetrics(fm);
            for (int i = 0; i < 3; i++) {
                if (i > 0) {
                    keyPaint.setColor((fg & 0x00FFFFFF) | 0x33000000);
                    c.drawRect(i * cw - dp(0.5f), bh * 0.28f, i * cw + dp(0.5f), bh * 0.72f, keyPaint);
                }
                String s = suggestionForSlot(i);
                if (s == null) continue;
                if (barSlot == i) {
                    keyPaint.setColor(dark ? 0x33FFFFFF : 0x22000000);
                    tmp.set(i * cw + dp(3), dp(3), (i + 1) * cw - dp(3), bh - dp(2));
                    c.drawRoundRect(tmp, dp(8), dp(8), keyPaint);
                }
                textPaint.setColor(fg);
                c.drawText(ellipsize(s, cw - dp(12)), i * cw + cw / 2f, bh / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
            }
        }

        private String ellipsize(String s, float maxW) {
            if (textPaint.measureText(s) <= maxW) return s;
            String t = s;
            while (t.length() > 1 && textPaint.measureText(t + "…") > maxW) t = t.substring(0, t.length() - 1);
            return t + "…";
        }

        private int baseColor(Key k, boolean special, boolean dark, int index, int count) {
            switch (cmode) {
                case KeyPrefs.COLOR_ONE:
                    return special ? oneSp : oneColor;
                case KeyPrefs.COLOR_RAINBOW:
                    return Color.HSVToColor(new float[]{(index * 330f) / Math.max(1, count),
                            dark ? 0.55f : 0.40f, dark ? 0.62f : 1f});
                case KeyPrefs.COLOR_EACH: {
                    Integer col = custom.get(k.id);
                    if (col != null) return col;
                    break;
                }
                default:
                    break;
            }
            return dark ? KEY_DARK : KEY_LIGHT;
        }

        private void drawKey(Canvas c, Key k, boolean dark, int index, int count) {
            boolean special = !isCharacter(k.id) && !k.id.equals("SPACE");
            boolean apple = cmode == KeyPrefs.COLOR_APPLE
                    || (cmode == KeyPrefs.COLOR_EACH && !custom.containsKey(k.id));
            int bg = baseColor(k, special, dark, index, count);
            int fg = apple ? (dark ? Color.WHITE : Color.BLACK) : KeyPrefs.textOn(bg);
            boolean blue = apple && k.id.equals("RET") && returnIsBlue();
            if (blue) {
                bg = k.pressed ? BLUE_PRESSED : BLUE;
                fg = Color.WHITE;
            } else if (k.pressed) {
                bg = apple ? (dark ? PRESSED_DARK : PRESSED_LIGHT) : KeyPrefs.darker(bg, 0.8f);
            }
            keyPaint.setColor(bg);
            float rad = shapeIdx == 2 ? Math.min(k.r.width(), k.r.height()) / 2f : dp(shapeIdx == 1 ? 13 : 8.5f);
            c.drawRoundRect(k.r, rad, rad, keyPaint);
            if (cursorPointer != -1) return;          // labels hidden while moving the cursor

            String text = label(k.id);
            float size;
            int color = fg;
            Typeface face = special ? specialFace : letterFace;
            switch (k.id) {
                case "SHIFT":
                case "DEL":
                    size = sp(22);
                    break;
                case "LANG":
                    size = sp(langCount > 1 ? 15 : 20);
                    break;
                case "EMOJI":
                    size = sp(20);
                    break;
                case "TOGGLE":
                    size = sp(19);
                    face = Typeface.DEFAULT_BOLD;
                    color = enabled ? (apple ? GREEN : fg) : ((fg & 0x00FFFFFF) | 0x59000000);
                    break;
                case "SPACE":
                    size = sp(15);
                    face = specialFace;
                    color = (fg & 0x00FFFFFF) | 0x99000000;
                    break;
                case "RET":
                    size = text.length() == 1 ? sp(22) : sp(16);
                    break;
                case "SYM1":
                case "SYM2":
                case "ABC":
                    size = sp(17);
                    break;
                default:
                    size = sp(mode == 0 ? 26 : 24);
            }
            if (text.length() > 0) {
                textPaint.setTypeface(face);
                textPaint.setTextSize(size);
                textPaint.setColor(color);
                float maxW = k.r.width() - dp(8);
                float tw = textPaint.measureText(text);
                if (tw > maxW && tw > 0) textPaint.setTextSize(size * maxW / tw);
                textPaint.getFontMetrics(fm);
                c.drawText(text, k.r.centerX(), k.r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
            }
        }

        private void drawPopup(Canvas c, boolean dark) {
            RectF r = popupKey.r;
            float w = r.width() + dp(20);
            float h = r.height() + dp(14);
            float x = Math.min(Math.max(r.centerX() - w / 2f, dp(2)), getWidth() - w - dp(2));
            float y = Math.max(r.top - h + dp(6), 0);
            tmp.set(x, y, x + w, y + h);
            keyPaint.setColor(popupHighlight ? BLUE : (dark ? 0xFF8E8E90 : Color.WHITE));
            c.drawRoundRect(tmp, dp(10), dp(10), keyPaint);
            strokePaint.setColor(0x33000000);
            c.drawRoundRect(tmp, dp(10), dp(10), strokePaint);
            textPaint.setTypeface(letterFace);
            textPaint.setTextSize(sp(34));
            textPaint.setColor(popupHighlight || dark ? Color.WHITE : Color.BLACK);
            float maxW = w - dp(6);
            float tw = textPaint.measureText(popupText);
            if (tw > maxW && tw > 0) textPaint.setTextSize(sp(34) * maxW / tw);
            textPaint.getFontMetrics(fm);
            c.drawText(popupText, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
        }

        // ------------------------------------------------ emoji panel

        private float centerOffset() {
            textPaint.getFontMetrics(fm);
            return -(fm.ascent + fm.descent) / 2f;
        }

        private void drawEmoji(Canvas c, boolean dark) {
            int fg = dark ? Color.WHITE : Color.BLACK;
            float W = getWidth();
            float H = getHeight();
            float top = emojiCapH();
            float tabsTop = H - emojiTabsH();
            float cellH = emojiCellH();
            float cw = W / EMOJI_COLS;

            capPaint.setTextSize(sp(12));
            capPaint.setTypeface(Typeface.DEFAULT_BOLD);
            capPaint.setColor((fg & 0x00FFFFFF) | 0x8C000000);
            c.drawText(Emojis.TITLES[emojiCat], dp(12), top - dp(8), capPaint);

            String[] list = emojiList(emojiCat);
            c.save();
            c.clipRect(0, top, W, tabsTop);
            textPaint.setTypeface(Typeface.DEFAULT);
            if (list.length == 0) {
                textPaint.setTextSize(sp(14));
                textPaint.setColor((fg & 0x00FFFFFF) | 0x8C000000);
                c.drawText("Bu yerda oxirgi smayliklar böladi", W / 2f, top + dp(40), textPaint);
                c.drawText("Здесь будут недавние смайлики", W / 2f, top + dp(62), textPaint);
            } else {
                textPaint.setTextSize(sp(28));
                textPaint.setColor(Color.BLACK);
                float dy = centerOffset();
                int first = Math.max(0, (int) (emojiScroll / cellH)) * EMOJI_COLS;
                for (int i = first; i < list.length; i++) {
                    int r = i / EMOJI_COLS;
                    int col = i % EMOJI_COLS;
                    float y = top + r * cellH - emojiScroll;
                    if (y > tabsTop) break;
                    c.drawText(list[i], col * cw + cw / 2f, y + cellH / 2f + dy, textPaint);
                }
            }
            c.restore();

            int n = Emojis.ICONS.length;
            float uw = W / (n + 3f);
            float ty = tabsTop + emojiTabsH() / 2f;
            textPaint.setTypeface(specialFace);
            textPaint.setTextSize(sp(16));
            textPaint.setColor(emojiTab == 0 ? BLUE : fg);
            c.drawText(isCyrillic() ? "АБВ" : "ABC", uw * 0.75f, ty + centerOffset(), textPaint);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(sp(19));
            float dIcon = centerOffset();
            for (int i = 0; i < n; i++) {
                float cx = uw * 1.5f + uw * i + uw / 2f;
                if (i == emojiCat) {
                    keyPaint.setColor(dark ? 0x33FFFFFF : 0x1F000000);
                    tmp.set(cx - uw / 2f + dp(1), tabsTop + dp(5), cx + uw / 2f - dp(1), H - dp(5));
                    c.drawRoundRect(tmp, dp(8), dp(8), keyPaint);
                }
                textPaint.setColor(Color.BLACK);
                c.drawText(Emojis.ICONS[i], cx, ty + dIcon, textPaint);
            }
            textPaint.setTextSize(sp(21));
            textPaint.setColor(emojiTab == n + 1 ? BLUE : fg);
            c.drawText("⌫", W - uw * 0.75f, ty + centerOffset(), textPaint);
        }

        private int tabAt(float x) {
            int n = Emojis.ICONS.length;
            float uw = getWidth() / (n + 3f);
            if (x < uw * 1.5f) return 0;
            if (x >= getWidth() - uw * 1.5f) return n + 1;
            int t = 1 + (int) ((x - uw * 1.5f) / uw);
            return Math.min(Math.max(t, 1), n);
        }

        private float clampScroll(float v) {
            String[] list = emojiList(emojiCat);
            int rowsN = (list.length + EMOJI_COLS - 1) / EMOJI_COLS;
            float visible = getHeight() - emojiTabsH() - emojiCapH();
            float max = Math.max(0, rowsN * emojiCellH() - visible);
            return Math.min(Math.max(v, 0), max);
        }

        private String emojiAt(float x, float y) {
            float top = emojiCapH();
            float bottom = getHeight() - emojiTabsH();
            if (y < top || y >= bottom) return null;
            float cw = getWidth() / (float) EMOJI_COLS;
            int col = Math.min(EMOJI_COLS - 1, (int) (x / cw));
            int row = (int) ((y - top + emojiScroll) / emojiCellH());
            int idx = row * EMOJI_COLS + col;
            String[] list = emojiList(emojiCat);
            return idx >= 0 && idx < list.length ? list[idx] : null;
        }

        private void emojiTouch(MotionEvent e, int act) {
            int n = Emojis.ICONS.length;
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    if (emojiPointer != -1) return;
                    int i = e.getActionIndex();
                    emojiPointer = e.getPointerId(i);
                    float x = e.getX(i);
                    float y = e.getY(i);
                    emojiDownY = y;
                    emojiLastY = y;
                    emojiScrolling = false;
                    emojiTab = -1;
                    if (y >= getHeight() - emojiTabsH()) {
                        emojiTab = tabAt(x);
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (emojiTab == n + 1) {
                            playClick("DEL");
                            deleteBegan();
                        } else {
                            playClick("x");
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    int i = e.findPointerIndex(emojiPointer);
                    if (i < 0) return;
                    float y = e.getY(i);
                    if (emojiTab == -1) {
                        if (!emojiScrolling && Math.abs(y - emojiDownY) > dp(8)) emojiScrolling = true;
                        if (emojiScrolling) emojiScroll = clampScroll(emojiScroll - (y - emojiLastY));
                    }
                    emojiLastY = y;
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    if (e.getPointerId(i) != emojiPointer) return;
                    emojiPointer = -1;
                    float x = e.getX(i);
                    float y = e.getY(i);
                    if (emojiTab != -1) {
                        int t = emojiTab;
                        emojiTab = -1;
                        if (t == n + 1) {
                            deleteEnded();
                        } else if (y >= getHeight() - emojiTabsH() - dp(10) && tabAt(x) == t) {
                            if (t == 0) {
                                closeEmoji();
                            } else {
                                emojiCat = t - 1;
                                emojiScroll = 0;
                            }
                        }
                        return;
                    }
                    if (!emojiScrolling) {
                        String em = emojiAt(x, y);
                        if (em != null) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            playClick("x");
                            typeEmoji(em);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (emojiTab == n + 1) deleteEnded();
                    emojiPointer = -1;
                    emojiTab = -1;
                    break;
                default:
                    break;
            }
        }

        // ------------------------------------------------ touches

        private int slotAt(float x) {
            int s = (int) (x / (getWidth() / 3f));
            return Math.min(Math.max(s, 0), 2);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getActionMasked();
            if (mode == MODE_EMOJI) {
                emojiTouch(e, act);
                invalidate();
                return true;
            }
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    int pid = e.getPointerId(i);
                    float x = e.getX(i);
                    float y = e.getY(i);
                    if (y < barH() && barPointer == -1 && cursorPointer == -1 && altPointer == -1) {
                        barPointer = pid;
                        int slot = slotAt(x);
                        barSlot = suggestionForSlot(slot) != null ? slot : -1;
                        break;
                    }
                    began(pid, x, y);
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        int pid = e.getPointerId(i);
                        if (pid == barPointer) continue;
                        moved(pid, e.getX(i), e.getY(i));
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    int pid = e.getPointerId(i);
                    if (pid == barPointer) {
                        int slot = barSlot;
                        barPointer = -1;
                        barSlot = -1;
                        if (slot >= 0 && e.getY(i) < barH() + dp(12) && slotAt(e.getX(i)) == slot) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            pickSuggestion(slot);
                        }
                        break;
                    }
                    ended(pid, false);
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    barPointer = -1;
                    barSlot = -1;
                    for (int i = 0; i < e.getPointerCount(); i++) ended(e.getPointerId(i), true);
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }
    }

    // ---------------------------------------------------------------- touches

    private void scheduleHold(int pid, final Runnable action) {
        cancelHold();
        holdPointer = pid;
        holdRunnable = new Runnable() {
            @Override
            public void run() {
                holdPointer = -1;
                holdRunnable = null;
                action.run();
                invalidateKeys();
            }
        };
        handler.postDelayed(holdRunnable, 400);
    }

    private void cancelHold() {
        if (holdRunnable != null) handler.removeCallbacks(holdRunnable);
        holdRunnable = null;
        holdPointer = -1;
    }

    private void press(Key k) {
        if (isCharacter(k.id)) {
            popupKey = k;
            popupText = label(k.id);
            popupHighlight = false;
        } else {
            k.pressed = true;
        }
    }

    private void unpress(Key k) {
        k.pressed = false;
        if (popupKey == k) popupKey = null;
    }

    private void began(final int pid, final float x, final float y) {
        if (cursorPointer != -1 || altPointer != -1) return;
        // Fast typing: a new finger types what the other fingers are still holding.
        for (int i = 0; i < pointerKeys.size(); i++) {
            int other = pointerKeys.keyAt(i);
            if (other != pid && armed.contains(other)) {
                armed.remove(other);
                Key ok = pointerKeys.valueAt(i);
                commit(ok.id);
                unpress(ok);
            }
        }
        if (mode == MODE_EMOJI) return;
        cancelHold();
        Key k = keyAt(x, y);
        if (k == null) return;
        pointerKeys.put(pid, k);
        if (kv != null) kv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        playClick(k.id);
        switch (k.id) {
            case "DEL":
                k.pressed = true;
                deleteBegan();
                break;
            case "SHIFT":
                shiftTapped();
                break;
            case "SYM1":
            case "SYM2":
            case "ABC": {
                switchMode(k.id);
                modePointer = pid;
                Key nk = keyAt(x, y);
                if (nk != null) {
                    pointerKeys.put(pid, nk);
                    nk.pressed = true;
                }
                break;
            }
            case "LANG":
                k.pressed = true;
                armed.add(pid);
                scheduleHold(pid, new Runnable() {
                    @Override
                    public void run() {
                        armed.remove(pid);
                        showPicker();
                    }
                });
                break;
            case "SPACE":
                k.pressed = true;
                armed.add(pid);
                scheduleHold(pid, new Runnable() {
                    @Override
                    public void run() {
                        beginCursorMode(pid, x);
                    }
                });
                break;
            case "RET":
            case "TOGGLE":
            case "EMOJI":
                k.pressed = true;
                armed.add(pid);
                break;
            default:
                press(k);
                armed.add(pid);
                if (alternate(k.id) != null) {
                    scheduleHold(pid, new Runnable() {
                        @Override
                        public void run() {
                            showAlternate(pid);
                        }
                    });
                }
        }
    }

    private void moved(final int pid, float x, float y) {
        if (pid == cursorPointer) {
            moveCursor(x);
            return;
        }
        if (pid == altPointer) return;
        Key old = pointerKeys.get(pid);
        if (old == null || old.id.equals("DEL") || old.id.equals("LANG")) return;
        Key k = keyAt(x, y);
        if (k == null || k == old) return;
        if (pid == holdPointer) cancelHold();
        unpress(old);
        pointerKeys.put(pid, k);
        switch (k.id) {
            case "SHIFT":
            case "SYM1":
            case "SYM2":
            case "ABC":
            case "LANG":
            case "DEL":
                armed.remove(pid);
                break;
            case "SPACE":
            case "RET":
            case "TOGGLE":
            case "EMOJI":
                k.pressed = true;
                armed.add(pid);
                break;
            default:
                press(k);
                armed.add(pid);
                if (alternate(k.id) != null) {
                    scheduleHold(pid, new Runnable() {
                        @Override
                        public void run() {
                            showAlternate(pid);
                        }
                    });
                }
        }
    }

    private void ended(int pid, boolean cancelled) {
        if (pid == holdPointer) cancelHold();
        Key k = pointerKeys.get(pid);
        pointerKeys.remove(pid);
        boolean wasArmed = armed.remove(pid);
        if (pid == cursorPointer) {
            if (k != null) unpress(k);
            endCursorMode();
            return;
        }
        if (pid == altPointer) {
            altPointer = -1;
            popupKey = null;
            if (!cancelled && altText != null) {
                toggleNotice = null;
                typeCommitted(altText);
            }
            altText = null;
            if (k != null) unpress(k);
            return;
        }
        if (k == null) {
            if (pid == modePointer) modePointer = -1;
            return;
        }
        if (k.id.equals("DEL")) deleteEnded();
        if (!cancelled && wasArmed) {
            commit(k.id);
            if (pid == modePointer && isCharacter(k.id) && mode != 0 && mode != MODE_EMOJI) {
                mode = 0;
                buildKeys();
                updateAutoShift();
                updateSuggestions();
            }
        }
        if (pid == modePointer) modePointer = -1;
        unpress(k);
    }

    private void showAlternate(int pid) {
        Key k = pointerKeys.get(pid);
        if (k == null) return;
        String alt = alternate(k.id);
        if (alt == null) return;
        altPointer = pid;
        armed.remove(pid);
        altText = (mode == 0 && shift > 0) ? alt.toUpperCase(Locale.ROOT) : alt;
        popupKey = k;
        popupText = altText;
        popupHighlight = true;
    }

    private void beginCursorMode(int pid, float x) {
        Key k = pointerKeys.get(pid);
        if (k == null || !k.id.equals("SPACE")) return;
        cursorPointer = pid;
        armed.remove(pid);
        cursorX = x;
    }

    private void moveCursor(float x) {
        float step = dp(9);
        int n = (int) ((x - cursorX) / step);
        if (n == 0) return;
        int code = n > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        for (int i = 0; i < Math.abs(n); i++) sendDownUpKeyEvents(code);
        cursorX += n * step;
    }

    private void endCursorMode() {
        cursorPointer = -1;
        lastOriginal = null;
        lastReplacement = null;
        afterEdit();
    }

    // ---------------------------------------------------------------- key actions

    private void commit(String id) {
        if (!id.equals("TOGGLE")) toggleNotice = null;
        switch (id) {
            case "SPACE":
                spaceTapped();
                break;
            case "RET":
                enter();
                break;
            case "LANG":
                if (langCount > 1) switchLang();
                else nextKeyboard();
                return;
            case "EMOJI":
                openEmoji();
                return;
            case "TOGGLE":
                enabled = !enabled;
                kp.setReplace(enabled);
                lastOriginal = null;
                lastReplacement = null;
                toggleNotice = enabled ? "öğ ✓" : "öğ — off";
                break;
            case "SHIFT":
            case "DEL":
            case "SYM1":
            case "SYM2":
            case "ABC":
                return;
            default:
                typeCommitted((mode == 0 && shift > 0) ? id.toUpperCase(Locale.ROOT) : id);
                // Like the Apple keyboard: an apostrophe typed from the 123 layer brings the letters back (o' / g').
                if (mode != 0 && isApos(id.charAt(0))) {
                    mode = 0;
                    buildKeys();
                    updateAutoShift();
                    updateSuggestions();
                }
                return;
        }
        afterEdit();
    }

    private void switchLang() {
        List<String> on = kp.enabledLangs();
        int i = on.indexOf(lang);
        lang = on.get((i + 1) % on.size());
        kp.setLang(lang);
        lastOriginal = null;
        lastReplacement = null;
        mode = 0;
        buildKeys();
        updateAutoShift();
        updateSuggestions();
    }

    private void typeCommitted(String s) {
        typeText(s);
        if (mode == 0 && shift == 1) setShift(0);
        afterEdit();
    }

    private void afterEdit() {
        updateAutoShift();
        updateSuggestions();
        invalidateKeys();
    }

    private void shiftTapped() {
        long now = System.currentTimeMillis();
        int v;
        if (now - lastShiftTap < 350 && shift != 2) v = 2;
        else if (shift == 2) v = 0;
        else v = (shift == 0) ? 1 : 0;
        lastShiftTap = now;
        setShift(v);
    }

    private void setShift(int v) {
        if (v == shift) return;
        shift = v;
        invalidateKeys();
    }

    private void switchMode(String id) {
        mode = id.equals("SYM1") ? 1 : (id.equals("SYM2") ? 2 : 0);
        buildKeys();
        if (mode == 0) updateAutoShift();
        updateSuggestions();
    }

    private void spaceTapped() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long now = System.currentTimeMillis();
        String b = (now - lastSpaceTap < 450 && !hasSelection()) ? before(ic) : "";
        if (b.endsWith(" ") && b.length() >= 2 && Character.isLetterOrDigit(b.charAt(b.length() - 2))) {
            ic.beginBatchEdit();
            cachedDelete(ic, 1);
            cachedCommit(ic, ". ");
            ic.endBatchEdit();
            markPending();
            lastSpaceTap = 0;
            lastOriginal = null;
            lastReplacement = null;
        } else {
            typeText(" ");
            lastSpaceTap = now;
        }
        if (mode != 0) {
            mode = 0;
            buildKeys();
        }
    }

    private void deleteBegan() {
        toggleNotice = null;
        doDelete();
        afterEdit();
        deleteCount = 0;
        deleteInterval = 100;
        handler.removeCallbacks(deleteRepeat);
        handler.postDelayed(deleteRepeat, 450);
    }

    private void deleteEnded() {
        handler.removeCallbacks(deleteRepeat);
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void nextKeyboard() {
        boolean switched = false;
        if (Build.VERSION.SDK_INT >= 28) {
            switched = switchToNextInputMethod(false);
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getWindow() != null && getWindow().getWindow() != null) {
                IBinder token = getWindow().getWindow().getAttributes().token;
                switched = imm.switchToNextInputMethod(token, false);
            }
        }
        if (!switched) showPicker();
    }

    private void updateAutoShift() {
        if (shift == 2 || mode != 0) return;
        setShift(capsNeeded() ? 1 : 0);
    }

    /** Same rules as the app's own caps mode, but worked out from the remembered text (no round trip). */
    private boolean capsNeeded() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null) return false;
        int t = ei.inputType;
        if ((t & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        if ((t & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) return true;
        boolean words = (t & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0;
        boolean sentences = (t & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0;
        if (!words && !sentences) return false;
        if (hasSelection()) return false;
        String b = before(ic);
        if (b.isEmpty()) return cacheFromStart;
        char last = b.charAt(b.length() - 1);
        if (words) return Character.isWhitespace(last);
        if (last == '\n') return true;
        int i = b.length() - 1;
        while (i >= 0 && b.charAt(i) == ' ') i--;
        if (i < 0) return cacheFromStart;
        if (i == b.length() - 1) return false;
        char c = b.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }

    // ---------------------------------------------------------------- text cache
    // Asking the app for text is a slow round trip to the other app, and Telegram or WhatsApp can take
    // a while to answer. Like Gboard, the keyboard remembers the last characters before the cursor itself
    // and asks again only when the cursor is moved by something other than our own typing.

    private static final int CACHE_LEN = 64;
    private String cache = null;             // text just before the cursor, or null when unknown
    private boolean cacheFromStart = false;  // the cache begins at the start of the field
    private int cursorPos = -1;              // where our own edits leave the cursor
    private int selStart = -1;
    private int selEnd = -1;
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();

    private void resetCache(int pos) {
        cache = null;
        cacheFromStart = false;
        cursorPos = pos;
        pending.clear();
    }

    /** Something we can't predict happened (enter, editor action, key event): read again next time. */
    private void forgetCache() {
        cache = null;
        cacheFromStart = false;
        cursorPos = -1;
        pending.clear();
    }

    private boolean hasSelection() {
        return selStart >= 0 && selEnd >= 0 && selStart != selEnd;
    }

    private String before(InputConnection ic) {
        if (cache == null) {
            CharSequence cs = ic.getTextBeforeCursor(CACHE_LEN, 0);
            cache = cs == null ? "" : cs.toString();
            cacheFromStart = cache.length() < CACHE_LEN;
            if (cursorPos < 0) cursorPos = selStart;
        }
        return cache;
    }

    private void cachedCommit(InputConnection ic, String s) {
        ic.commitText(s, 1);
        if (cache != null) {
            cache = cache + s;
            if (cache.length() > CACHE_LEN) {
                cache = cache.substring(cache.length() - CACHE_LEN);
                cacheFromStart = false;
            }
        }
        if (cursorPos >= 0) cursorPos += s.length();
    }

    private void cachedDelete(InputConnection ic, int n) {
        ic.deleteSurroundingText(n, 0);
        if (cache != null) {
            if (cache.length() >= n) {
                cache = cache.substring(0, cache.length() - n);
                if (!cacheFromStart && cache.length() < 8) cache = null;
            } else {
                cache = null;
            }
        }
        if (cursorPos >= 0) cursorPos = Math.max(0, cursorPos - n);
    }

    /** Remembers where our edit leaves the cursor, so the app's report about it is recognised as ours. */
    private void markPending() {
        if (cursorPos >= 0) {
            pending.addLast(cursorPos);
            while (pending.size() > 32) pending.pollFirst();
        }
    }

    // ---------------------------------------------------------------- text

    private static boolean isApos(char c) {
        return c == '\'' || c == '‘' || c == '’' || c == 'ʻ' || c == 'ʼ';
    }

    private static boolean isEndChar(char c) {
        return " \n-()[]{}':;\"/\\,.?!".indexOf(c) >= 0 || c == '‘' || c == '’' || c == 'ʻ';
    }

    /** Length of the last character (emoji with joiners and flags count as one). */
    private static int lastCharLength(String b) {
        int end = b.length();
        if (end == 0) return 0;
        try {
            int start;
            if (Build.VERSION.SDK_INT >= 24) {
                android.icu.text.BreakIterator bi = android.icu.text.BreakIterator.getCharacterInstance();
                bi.setText(b);
                start = bi.preceding(end);
            } else {
                java.text.BreakIterator bi = java.text.BreakIterator.getCharacterInstance();
                bi.setText(b);
                start = bi.preceding(end);
            }
            if (start >= 0 && start < end) return end - start;
        } catch (Exception ignored) {
        }
        return (end >= 2 && Character.isSurrogatePair(b.charAt(end - 2), b.charAt(end - 1))) ? 2 : 1;
    }

    private static String replacementFor(char p, char c) {
        if (isApos(c)) {
            switch (p) {
                case 'o': return "ö";
                case 'O': return "Ö";
                case 'g': return "ğ";
                case 'G': return "Ğ";
                default: return null;
            }
        }
        if (c == 'h') {
            switch (p) {
                case 's': return "ş";
                case 'S': return "Ş";
                case 'c': return "ç";
                case 'C': return "Ç";
                default: return null;
            }
        }
        if (c == 'H') {
            switch (p) {
                case 'S': return "Ş";
                case 'C': return "Ç";
                default: return null;
            }
        }
        return null;
    }

    /** The shortcut right before the cursor (aa, Aa or AA as a whole word) and its phrase, or null. */
    private String[] phraseFor(String before) {
        if (!phrasesOn) return null;
        String[][] table = lang.equals("UZ") ? CYR_PHRASES : (hasToggle() ? LAT_PHRASES : null);
        int n = before.length();
        if (table == null || n < 2) return null;
        if (n >= 3 && Character.isLetterOrDigit(before.charAt(n - 3))) return null;
        char a = before.charAt(n - 2);
        char b = before.charAt(n - 1);
        boolean lower = Character.isLowerCase(a) && Character.isLowerCase(b);
        boolean cap = Character.isUpperCase(a);
        if (!lower && !cap) return null;
        String w = before.substring(n - 2).toLowerCase(Locale.ROOT);
        for (String[] t : table) {
            if (t[0].equals(w)) return new String[]{before.substring(n - 2), t[1]};
        }
        return null;
    }

    /** Expands a shortcut before the cursor. end = what was typed after the word (may be empty). */
    private boolean tryPhrase(InputConnection ic, String before, String end) {
        String[] ph = phraseFor(before);
        if (ph == null) return false;
        ic.beginBatchEdit();
        cachedDelete(ic, ph[0].length());
        cachedCommit(ic, ph[1] + end);
        ic.endBatchEdit();
        lastOriginal = ph[0] + end;
        lastReplacement = ph[1] + end;
        return true;
    }

    private void typeText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastOriginal = null;
        lastReplacement = null;
        if (hasSelection()) {
            ic.commitText(s, 1);
            forgetCache();
            return;
        }
        if (s.length() == 1) {
            char c = s.charAt(0);
            String before = before(ic);
            if (enabled && before.length() > 0) {
                char p = before.charAt(before.length() - 1);
                String rep = lang.equals("YA") ? replacementFor(p, c) : null;
                if (rep != null) {
                    ic.beginBatchEdit();
                    cachedDelete(ic, 1);
                    cachedCommit(ic, rep);
                    ic.endBatchEdit();
                    markPending();
                    lastOriginal = "" + p + c;
                    lastReplacement = rep;
                    return;
                }
                if (lang.equals("OZ") && isApos(c) && Character.isLetter(p)) {
                    String a = (p == 'o' || p == 'O' || p == 'g' || p == 'G') ? "ʻ" : "ʼ";
                    cachedCommit(ic, a);
                    markPending();
                    lastOriginal = s;
                    lastReplacement = a;
                    return;
                }
            }
            if (isEndChar(c)) {
                if (tryPhrase(ic, before, s)) {
                    markPending();
                    return;
                }
                learnBefore(before);
            }
        }
        cachedCommit(ic, s);
        markPending();
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastOriginal = null;
        lastReplacement = null;
        if (!hasSelection()) {
            String b = before(ic);
            if (tryPhrase(ic, b, "")) markPending();
            else learnBefore(b);
        }
        int action = imeAction();
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendKeyChar('\n');
        }
        forgetCache();
    }

    private void doDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (hasSelection()) {
            ic.commitText("", 1);
            forgetCache();
            lastOriginal = null;
            lastReplacement = null;
            return;
        }
        String b = before(ic);
        if (lastReplacement != null && lastOriginal != null) {
            String rep = lastReplacement;
            String orig = lastOriginal;
            lastOriginal = null;
            lastReplacement = null;
            if (b.endsWith(rep)) {
                ic.beginBatchEdit();
                cachedDelete(ic, rep.length());
                cachedCommit(ic, orig);
                ic.endBatchEdit();
                markPending();
                return;
            }
        }
        if (b.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            forgetCache();
            return;
        }
        cachedDelete(ic, lastCharLength(b));
        markPending();
    }
}
