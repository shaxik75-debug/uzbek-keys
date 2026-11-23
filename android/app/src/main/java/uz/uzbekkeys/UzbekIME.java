package uz.uzbekkeys;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Uzbek Keys keyboard.
 * While typing: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (capitals too),
 * aa -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom" (whole word, after space/punctuation/enter).
 * Backspace right after a replacement brings back what was typed.
 * The "öğ" key switches replacements on and off.
 */
public class UzbekIME extends InputMethodService {

    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "'"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"123", "LANG", "TOGGLE", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "\"", "@", "&", "?"},
            {"MORE", "_", ",", "!", "'", "%", "+", "*", "DEL"},
            {"ABC", "LANG", "TOGGLE", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYMBOLS2 = {
            {"[", "]", "{", "}", "#", "^", "=", "<", ">", "~"},
            {"\\", "|", "$", "€", "£", "₽", "•", "№", "°", "…"},
            {"123", "«", "»", "‘", "’", "ʻ", "×", "÷", "DEL"},
            {"ABC", "LANG", "TOGGLE", "SPACE", ".", "ENTER"}
    };

    private static final int COLOR_BG = 0xFF202124;
    private static final int COLOR_KEY = 0xFF5F6368;
    private static final int COLOR_SPECIAL = 0xFF3C4043;
    private static final int COLOR_ON = 0xFF2E7D32;
    private static final int COLOR_SHIFT_ON = 0xFFE8EAED;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> letterKeys = new ArrayList<>();

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout keysBox;
    private TextView banner;
    private TextView shiftKey;
    private TextView toggleKey;

    private int mode = 0;      // 0 letters, 1 symbols, 2 more symbols
    private int shift = 0;     // 0 off, 1 next letter, 2 caps lock
    private long lastShiftTap = 0;
    private boolean enabled = true;

    private String lastOriginal = null;
    private String lastReplacement = null;

    private boolean longFired = false;
    private final Runnable deleteRepeat = new Runnable() {
        @Override
        public void run() {
            doDelete();
            handler.postDelayed(this, 60);
        }
    };

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
        root.setBackgroundColor(COLOR_BG);
        int pad = (int) dp(3);
        root.setPadding(pad, pad, pad, pad);

        banner = new TextView(this);
        banner.setTextColor(Color.WHITE);
        banner.setBackgroundColor(COLOR_ON);
        banner.setGravity(Gravity.CENTER);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int bp = (int) dp(8);
        banner.setPadding(bp, bp, bp, bp);
        banner.setText("Yangi versiya bor — yuklab olish / Есть обновление — скачать");
        banner.setVisibility(View.GONE);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Updater.openDownload(UzbekIME.this);
                requestHideSelf(0);
            }
        });
        root.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(-1, -2));

        build();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        lastOriginal = null;
        lastReplacement = null;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int newMode = (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) ? 1 : 0;
        if (shift == 2) shift = 0;
        if (newMode != mode) {
            mode = newMode;
            build();
        }
        updateAutoShift();
        refresh();
        Updater.maybeCheck(this, new Updater.Callback() {
            @Override
            public void onResult(int remote, int local) {
                if (banner != null) banner.setVisibility(remote > local ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        updateAutoShift();
    }

    // ---------------------------------------------------------------- layout

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void build() {
        if (keysBox == null) return;
        keysBox.removeAllViews();
        letterKeys.clear();
        shiftKey = null;
        toggleKey = null;
        String[][] rows = mode == 0 ? LETTERS : (mode == 1 ? SYMBOLS : SYMBOLS2);
        for (String[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            r.setMotionEventSplittingEnabled(true);
            for (String k : row) r.addView(makeKey(k), keyParams(k));
            keysBox.addView(r, new LinearLayout.LayoutParams(-1, (int) dp(54)));
        }
        refresh();
    }

    private static boolean isSpecial(String k) {
        return k.length() > 1;
    }

    private LinearLayout.LayoutParams keyParams(String k) {
        float w = 1f;
        if (k.equals("SPACE")) w = 4f;
        else if (k.equals("SHIFT") || k.equals("DEL") || k.equals("ENTER")
                || k.equals("123") || k.equals("ABC") || k.equals("MORE")) w = 1.5f;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, w);
        int m = (int) dp(3);
        p.setMargins(m, m, m, m);
        return p;
    }

    private GradientDrawable bg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(6));
        d.setColor(color);
        return d;
    }

    private TextView makeKey(final String k) {
        final TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, isSpecial(k) ? 16 : 22);
        t.setBackground(bg(isSpecial(k) && !k.equals("SPACE") ? COLOR_SPECIAL : COLOR_KEY));
        t.setTag(k);
        t.setText(label(k));
        if (k.length() == 1 && Character.isLetter(k.charAt(0))) letterKeys.add(t);
        if (k.equals("SHIFT")) shiftKey = t;
        if (k.equals("TOGGLE")) toggleKey = t;

        t.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setAlpha(0.55f);
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        longFired = false;
                        if (k.equals("DEL")) {
                            doDelete();
                            handler.postDelayed(deleteRepeat, 400);
                        } else if (k.equals("LANG") || k.equals("SPACE")) {
                            handler.postDelayed(showPicker, 500);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setAlpha(1f);
                        handler.removeCallbacks(deleteRepeat);
                        handler.removeCallbacks(showPicker);
                        if (!k.equals("DEL") && !longFired) onKey(k);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(1f);
                        handler.removeCallbacks(deleteRepeat);
                        handler.removeCallbacks(showPicker);
                        return true;
                    default:
                        return true;
                }
            }
        });
        return t;
    }

    private final Runnable showPicker = new Runnable() {
        @Override
        public void run() {
            longFired = true;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }
    };

    private String label(String k) {
        switch (k) {
            case "SHIFT": return shift == 2 ? "⇪" : "⇧";
            case "DEL": return "⌫";
            case "ENTER": return "⏎";
            case "SPACE": return "O'zbek";
            case "LANG": return "🌐";
            case "TOGGLE": return enabled ? "öğ" : "off";
            case "MORE": return "#+=";
            case "123": return "123";
            case "ABC": return "ABC";
            default: return k;
        }
    }

    private void refresh() {
        boolean upper = shift > 0;
        for (TextView t : letterKeys) {
            String k = (String) t.getTag();
            t.setText(upper ? k.toUpperCase() : k);
        }
        if (shiftKey != null) {
            shiftKey.setText(label("SHIFT"));
            shiftKey.setBackground(bg(shift > 0 ? COLOR_SHIFT_ON : COLOR_SPECIAL));
            shiftKey.setTextColor(shift > 0 ? Color.BLACK : Color.WHITE);
        }
        if (toggleKey != null) {
            toggleKey.setText(label("TOGGLE"));
            toggleKey.setBackground(bg(enabled ? COLOR_ON : COLOR_SPECIAL));
        }
    }

    // ---------------------------------------------------------------- keys

    private void onKey(String k) {
        switch (k) {
            case "SHIFT": {
                long now = System.currentTimeMillis();
                if (shift == 2) shift = 0;
                else if (shift == 1 && now - lastShiftTap < 400) shift = 2;
                else shift = (shift == 0) ? 1 : 0;
                lastShiftTap = now;
                refresh();
                return;
            }
            case "123": mode = 1; build(); return;
            case "MORE": mode = 2; build(); return;
            case "ABC": mode = 0; build(); updateAutoShift(); return;
            case "LANG": nextKeyboard(); return;
            case "TOGGLE":
                enabled = !enabled;
                prefs.edit().putBoolean("enabled", enabled).apply();
                lastOriginal = null;
                lastReplacement = null;
                refresh();
                return;
            case "SPACE": typeText(" "); return;
            case "ENTER": enter(); return;
            default:
                break;
        }
        String s = k;
        if (mode == 0 && shift > 0 && Character.isLetter(k.charAt(0))) s = k.toUpperCase();
        typeText(s);
        if (mode == 0 && shift == 1) {
            shift = 0;
            refresh();
        }
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
        if (!switched) showPicker.run();
    }

    private void updateAutoShift() {
        if (shift == 2) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        boolean caps = false;
        if (ic != null && ei != null && (ei.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            caps = ic.getCursorCapsMode(ei.inputType) != 0;
        }
        int ns = caps ? 1 : 0;
        if (ns != shift) {
            shift = ns;
            refresh();
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

    private boolean hasSelection(InputConnection ic) {
        CharSequence sel = ic.getSelectedText(0);
        return sel != null && sel.length() > 0;
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
        }
        if (abbr == null) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(abbr.length(), 0);
        ic.commitText(phrase + end, 1);
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
        if (enabled && s.length() == 1 && !hasSelection(ic)) {
            char c = s.charAt(0);
            CharSequence cs = ic.getTextBeforeCursor(40, 0);
            String before = cs == null ? "" : cs.toString();
            if (before.length() > 0) {
                char p = before.charAt(before.length() - 1);
                String rep = replacementFor(p, c);
                if (rep != null) {
                    ic.beginBatchEdit();
                    ic.deleteSurroundingText(1, 0);
                    ic.commitText(rep, 1);
                    ic.endBatchEdit();
                    lastOriginal = "" + p + c;
                    lastReplacement = rep;
                    return;
                }
            }
            if (isEndChar(c) && tryPhrase(ic, before, s)) return;
        }
        ic.commitText(s, 1);
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastOriginal = null;
        lastReplacement = null;
        if (enabled && !hasSelection(ic)) {
            CharSequence cs = ic.getTextBeforeCursor(40, 0);
            tryPhrase(ic, cs == null ? "" : cs.toString(), "");
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei == null ? EditorInfo.IME_ACTION_NONE : (ei.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        boolean multiLine = ei != null && (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (!multiLine && !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendKeyChar('\n');
        }
    }

    private void doDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (hasSelection(ic)) {
            ic.commitText("", 1);
            lastOriginal = null;
            lastReplacement = null;
            return;
        }
        if (lastReplacement != null && lastOriginal != null) {
            String rep = lastReplacement;
            String orig = lastOriginal;
            lastOriginal = null;
            lastReplacement = null;
            CharSequence b = ic.getTextBeforeCursor(rep.length(), 0);
            if (b != null && b.toString().equals(rep)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(rep.length(), 0);
                ic.commitText(orig, 1);
                ic.endBatchEdit();
                return;
            }
        }
        CharSequence b = ic.getTextBeforeCursor(2, 0);
        if (b == null || b.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }
        int n = (b.length() == 2 && Character.isSurrogatePair(b.charAt(0), b.charAt(1))) ? 2 : 1;
        ic.deleteSurroundingText(n, 0);
    }
}
