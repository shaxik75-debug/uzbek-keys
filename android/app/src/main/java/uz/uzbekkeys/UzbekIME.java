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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Uzbek Keys keyboard, same look and behaviour as the iPhone version.
 * While typing: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (capitals too),
 * aa -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom", rr -> "Rahmat" (whole word, after space/punctuation/enter).
 * Backspace right after a replacement brings back what was typed; the space bar shows the last replacement.
 * The "öğ" key turns replacements on and off.
 * Key popups, fast typing with several fingers, long-press o/g/s/c for ö/ğ/ş/ç,
 * double space = ". ", hold space and slide to move the cursor, hold 123 and slide to a symbol.
 */
public class UzbekIME extends InputMethodService {

    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "'"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"SYM1", "NEXT", "TOGGLE", "SPACE", "RET"}
    };
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""},
            {"SYM2", ".", ",", "?", "!", "'", "DEL"},
            {"ABC", "NEXT", "TOGGLE", "SPACE", "RET"}
    };
    private static final String[][] SYMBOLS2 = {
            {"[", "]", "{", "}", "#", "%", "^", "*", "+", "="},
            {"_", "\\", "|", "~", "<", ">", "€", "£", "₽", "•"},
            {"SYM1", ".", ",", "?", "!", "'", "DEL"},
            {"ABC", "NEXT", "TOGGLE", "SPACE", "RET"}
    };

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
    private LinearLayout root;
    private KeyboardView kv;
    private TextView banner;

    private int mode = 0;          // 0 letters, 1 symbols, 2 more symbols
    private int shift = 0;         // 0 off, 1 next letter, 2 caps lock
    private long lastShiftTap = 0;
    private long lastSpaceTap = 0;
    private boolean enabled = true;
    private String lastOriginal = null;
    private String lastReplacement = null;
    private String toggleNotice = null;

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

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("uk", MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", true);
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
        updateAutoShift();
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
        popupKey = null;
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
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static boolean isCharacter(String id) {
        return id.length() == 1;
    }

    private static String alternate(String id) {
        switch (id) {
            case "o": return "ö";
            case "g": return "ğ";
            case "s": return "ş";
            case "c": return "ç";
            default: return null;
        }
    }

    private void invalidateKeys() {
        if (kv != null) kv.invalidate();
    }

    private void buildKeys() {
        rows.clear();
        popupKey = null;
        String[][] src = mode == 0 ? LETTERS : (mode == 1 ? SYMBOLS : SYMBOLS2);
        boolean offerNext = Build.VERSION.SDK_INT < 28 || shouldOfferSwitchingToNextInputMethod();
        for (String[] r : src) {
            List<Key> row = new ArrayList<>();
            for (String id : r) {
                if (id.equals("NEXT") && !offerNext) continue;
                row.add(new Key(id));
            }
            rows.add(row);
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

    // ---------------------------------------------------------------- labels

    private String label(String id) {
        switch (id) {
            case "SHIFT": return shift == 2 ? "⇪" : (shift == 1 ? "⬆" : "⇧");
            case "DEL": return "⌫";
            case "NEXT": return "🌐";
            case "TOGGLE": return "öğ";
            case "SPACE": return spaceHint();
            case "RET": return returnLabel();
            case "SYM1": return "123";
            case "SYM2": return "#+=";
            case "ABC": return "ABC";
            default:
                return (mode == 0 && shift > 0) ? id.toUpperCase(Locale.ROOT) : id;
        }
    }

    private String spaceHint() {
        if (toggleNotice != null) return toggleNotice;
        if (lastReplacement != null && lastOriginal != null) {
            return lastOriginal.trim() + " → " + lastReplacement.trim();
        }
        return "";
    }

    private int imeAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return EditorInfo.IME_ACTION_NONE;
        if ((ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return EditorInfo.IME_ACTION_NONE;
        if ((ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) return EditorInfo.IME_ACTION_NONE;
        return ei.imeOptions & EditorInfo.IME_MASK_ACTION;
    }

    private String returnLabel() {
        switch (imeAction()) {
            case EditorInfo.IME_ACTION_GO: return "go";
            case EditorInfo.IME_ACTION_SEARCH: return "search";
            case EditorInfo.IME_ACTION_SEND: return "send";
            case EditorInfo.IME_ACTION_DONE: return "done";
            case EditorInfo.IME_ACTION_NEXT: return "next";
            case EditorInfo.IME_ACTION_PREVIOUS: return "prev";
            default: return "↵";
        }
    }

    private boolean returnIsBlue() {
        int a = imeAction();
        return a == EditorInfo.IME_ACTION_GO || a == EditorInfo.IME_ACTION_SEARCH
                || a == EditorInfo.IME_ACTION_SEND || a == EditorInfo.IME_ACTION_DONE;
    }

    // ---------------------------------------------------------------- view

    private final class KeyboardView extends View {
        private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final Paint.FontMetrics fm = new Paint.FontMetrics();

        KeyboardView(Context c) {
            super(c);
            textPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setTextAlign(Paint.Align.RIGHT);
            subPaint.setTypeface(Typeface.DEFAULT_BOLD);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(0.7f));
            setHapticFeedbackEnabled(true);
        }

        private float keyHeight() {
            return dp(isLandscape() ? 33 : 42);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            boolean land = isLandscape();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            float h = dp(land ? 6 : 10) + dp(land ? 4 : 6) + 3 * dp(land ? 7 : 11) + 4 * keyHeight();
            setMeasuredDimension(width, (int) Math.ceil(h));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            layoutKeys(w, h);
        }

        void layoutKeys(int W, int H) {
            if (W <= 0 || H <= 0) return;
            boolean land = isLandscape();
            float side = dp(land ? 5 : 6);
            float gap = dp(6);
            float topPad = dp(land ? 6 : 10);
            float bottomPad = dp(land ? 4 : 6);
            float rowGap = dp(land ? 7 : 11);
            float keyH = (H - topPad - bottomPad - 3 * rowGap) / 4f;
            float unit = (W - 2 * side - 9 * gap) / 10f;
            gridTop = topPad;
            gridKeyH = keyH;
            gridGap = rowGap;
            for (int r = 0; r < rows.size(); r++) {
                List<Key> row = rows.get(r);
                float y = topPad + r * (keyH + rowGap);
                int n = row.size();
                if (r < 2) {
                    float x = (W - (n * unit + (n - 1) * gap)) / 2f;
                    for (Key k : row) {
                        k.r.set(x, y, x + unit, y + keyH);
                        x += unit + gap;
                    }
                } else if (r == 2) {
                    float wide = unit * 1.3f + dp(2);
                    int m = n - 2;
                    row.get(0).r.set(side, y, side + wide, y + keyH);
                    if (mode == 0) {
                        float x = (W - (m * unit + (m - 1) * gap)) / 2f;
                        for (int i = 1; i <= m; i++) {
                            row.get(i).r.set(x, y, x + unit, y + keyH);
                            x += unit + gap;
                        }
                    } else {
                        float inner = W - 2 * side - 2 * wide - 4 * gap;
                        float w = (inner - (m - 1) * gap) / m;
                        float x = side + wide + 2 * gap;
                        for (int i = 1; i <= m; i++) {
                            row.get(i).r.set(x, y, x + w, y + keyH);
                            x += w + gap;
                        }
                    }
                    row.get(n - 1).r.set(W - side - wide, y, W - side, y + keyH);
                } else {
                    float[] fixed = new float[n];
                    float used = (n - 1) * gap;
                    for (int i = 0; i < n; i++) {
                        String id = row.get(i).id;
                        fixed[i] = id.equals("SPACE") ? 0 : (id.equals("RET") ? unit * 2.7f + gap : unit * 1.3f + dp(2));
                        used += fixed[i];
                    }
                    float spaceW = Math.max(unit, W - 2 * side - used);
                    float x = side;
                    for (int i = 0; i < n; i++) {
                        Key k = row.get(i);
                        float w = k.id.equals("SPACE") ? spaceW : fixed[i];
                        k.r.set(x, y, x + w, y + keyH);
                        x += w + gap;
                    }
                }
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean dark = isDark();
            c.drawColor(dark ? BG_DARK : BG_LIGHT);
            float rad = dp(8.5f);
            for (List<Key> row : rows) {
                for (Key k : row) drawKey(c, k, dark, rad);
            }
            if (popupKey != null) drawPopup(c, dark);
        }

        private void drawKey(Canvas c, Key k, boolean dark, float rad) {
            int fg = dark ? Color.WHITE : Color.BLACK;
            int bg = dark ? KEY_DARK : KEY_LIGHT;
            boolean blue = k.id.equals("RET") && returnIsBlue();
            if (blue) {
                bg = k.pressed ? BLUE_PRESSED : BLUE;
                fg = Color.WHITE;
            } else if (k.pressed) {
                bg = dark ? PRESSED_DARK : PRESSED_LIGHT;
            }
            keyPaint.setColor(bg);
            c.drawRoundRect(k.r, rad, rad, keyPaint);
            if (cursorPointer != -1) return;          // labels hidden while moving the cursor

            String text = label(k.id);
            float size;
            int color = fg;
            Typeface face = Typeface.DEFAULT;
            switch (k.id) {
                case "SHIFT":
                case "DEL":
                    size = sp(22);
                    break;
                case "NEXT":
                    size = sp(20);
                    break;
                case "TOGGLE":
                    size = sp(19);
                    face = Typeface.DEFAULT_BOLD;
                    color = enabled ? GREEN : ((fg & 0x00FFFFFF) | 0x59000000);
                    break;
                case "SPACE":
                    size = sp(15);
                    color = (fg & 0x00FFFFFF) | 0x99000000;
                    break;
                case "RET":
                    size = text.length() == 1 ? sp(22) : sp(17);
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
            if (k.id.equals("SPACE")) {
                subPaint.setTextSize(sp(12));
                subPaint.setColor((fg & 0x00FFFFFF) | 0x73000000);
                c.drawText("uz", k.r.right - dp(9), k.r.bottom - dp(6), subPaint);
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
            strokePaint.setColor(dark ? 0x33000000 : 0x33000000);
            c.drawRoundRect(tmp, dp(10), dp(10), strokePaint);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(sp(34));
            textPaint.setColor(popupHighlight || dark ? Color.WHITE : Color.BLACK);
            textPaint.getFontMetrics(fm);
            c.drawText(popupText, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    began(e.getPointerId(i), e.getX(i), e.getY(i));
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) moved(e.getPointerId(i), e.getX(i), e.getY(i));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    ended(e.getPointerId(i), false);
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
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
        cancelHold();
        Key k = keyAt(x, y);
        if (k == null) return;
        pointerKeys.put(pid, k);
        if (kv != null) kv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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
            case "NEXT":
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
        if (old == null || old.id.equals("DEL") || old.id.equals("NEXT")) return;
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
            case "NEXT":
            case "DEL":
                armed.remove(pid);
                break;
            case "SPACE":
            case "RET":
            case "TOGGLE":
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
            if (pid == modePointer && isCharacter(k.id) && mode != 0) {
                mode = 0;
                buildKeys();
                updateAutoShift();
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
            case "NEXT":
                nextKeyboard();
                return;
            case "TOGGLE":
                enabled = !enabled;
                prefs.edit().putBoolean("enabled", enabled).apply();
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
                }
                return;
        }
        afterEdit();
    }

    private void typeCommitted(String s) {
        typeText(s);
        if (mode == 0 && shift == 1) setShift(0);
        afterEdit();
    }

    private void afterEdit() {
        updateAutoShift();
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

    private static boolean endsWithWord(String before, String w) {
        if (!before.endsWith(w)) return false;
        int i = before.length() - w.length() - 1;
        return i < 0 || !Character.isLetterOrDigit(before.charAt(i));
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

    /** Expands aa / vv before the cursor. end = what was typed after the word (may be empty). */
    private boolean tryPhrase(InputConnection ic, String before, String end) {
        String abbr = null;
        String phrase = null;
        if (endsWithWord(before, "aa")) {
            abbr = "aa";
            phrase = "Assalomu Alaykum";
        } else if (endsWithWord(before, "vv")) {
            abbr = "vv";
            phrase = "Va Alaykum Assalom";
        } else if (endsWithWord(before, "rr")) {
            abbr = "rr";
            phrase = "Rahmat";
        }
        if (abbr == null) return false;
        ic.beginBatchEdit();
        cachedDelete(ic, abbr.length());
        cachedCommit(ic, phrase + end);
        ic.endBatchEdit();
        lastOriginal = abbr + end;
        lastReplacement = phrase + end;
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
        if (enabled && s.length() == 1) {
            char c = s.charAt(0);
            String before = before(ic);
            if (before.length() > 0) {
                char p = before.charAt(before.length() - 1);
                String rep = replacementFor(p, c);
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
            }
            if (isEndChar(c) && tryPhrase(ic, before, s)) {
                markPending();
                return;
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
        if (enabled && !hasSelection() && tryPhrase(ic, before(ic), "")) markPending();
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
        int n = (b.length() >= 2 && Character.isSurrogatePair(b.charAt(b.length() - 2), b.charAt(b.length() - 1))) ? 2 : 1;
        cachedDelete(ic, n);
        markPending();
    }
}
