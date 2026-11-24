package uz.uzbekkeys;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

/** Setup screen: turn the keyboard on, keyboards, look, typing settings, try it, what's new, updates. by işax */
public class MainActivity extends Activity {

    private static final int GREEN = 0xFF29923F;
    private static final String AUTHOR = "by işax";

    private static final String[] PAINT_LAT = {"qwertyuiop", "asdfghjkl'", "zxcvbnm"};
    private static final String[] PAINT_UZC = {"йцукенгшўзх", "фқвапролджэ", "ячсмитғбюҳ"};
    private static final String[] PAINT_RU = {"йцукенгшщзх", "фывапролджэ", "ячсмитьбю"};
    private static final String[][] PAINT_SPECIAL = {
            {"SHIFT", "⇧"}, {"DEL", "⌫"}, {"SYM1", "123"}, {"LANG", "🌐"},
            {"EMOJI", "😊"}, {"TOGGLE", "öğ"}, {"SPACE", "probel"}, {"RET", "↵"}
    };

    private interface IntCb {
        void on(int v);
    }

    private KeyPrefs kp;
    private TextView status;
    private TextView updateText;
    private TextView newText;
    private TextView updateHeading;
    private Button downloadButton;
    private ScrollView scroll;
    private LinearLayout colorBox;
    private int paintLayout = 0;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(LinearLayout parent, String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setLineSpacing(0, 1.15f);
        parent.addView(t);
        return t;
    }

    private TextView heading(LinearLayout parent, String s) {
        TextView t = text(parent, s, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(GREEN);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private void steps(LinearLayout parent, String... items) {
        for (int i = 0; i < items.length; i++) {
            text(parent, (i + 1) + ".  " + items[i], 15);
        }
    }

    private Button button(LinearLayout parent, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return b;
    }

    private CheckBox check(LinearLayout parent, String s, boolean on, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(this);
        cb.setText(s);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cb.setChecked(on);
        cb.setOnCheckedChangeListener(l);
        cb.setPadding(dp(4), dp(6), 0, dp(6));
        parent.addView(cb);
        return cb;
    }

    /** A row of radio buttons; cb gets the index of the chosen one. */
    private void choice(LinearLayout parent, String title, String[] labels, int selected, final IntCb cb) {
        TextView t = text(parent, title, 15);
        t.setPadding(0, dp(12), 0, 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        final int[] ids = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            rb.setPadding(0, 0, dp(12), 0);
            ids[i] = View.generateViewId();
            rb.setId(ids[i]);
            g.addView(rb);
        }
        if (selected >= 0 && selected < ids.length) g.check(ids[selected]);
        g.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) cb.on(i);
            }
        });
        hs.addView(g);
        parent.addView(hs);
    }

    /** Colour circles; cb gets the chosen colour. */
    private void swatches(LinearLayout parent, int selected, final IntCb cb) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (final int c : KeyPrefs.SWATCHES) {
            View v = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(c);
            boolean on = c == selected;
            d.setStroke(dp(on ? 3 : 1), on ? 0xFF0A84FF : 0x55000000);
            v.setBackground(d);
            v.setOnClickListener(x -> cb.on(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(0, dp(4), dp(8), dp(4));
            row.addView(v, lp);
        }
        hs.addView(row);
        parent.addView(hs);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kp = new KeyPrefs(this);
        ScrollView sv = new ScrollView(this);
        scroll = sv;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        l.setPadding(pad, pad, pad, pad);
        sv.addView(l);

        TextView logo = text(l, "ö ğ ş ç", 40);
        logo.setTextColor(GREEN);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text(l, "Uzbek Keys " + Updater.localName(this), 24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView sub = text(l, "Özbek harflari uçun klaviatura\nКлавиатура для узбекских букв", 15);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView author = text(l, AUTHOR, 15);
        author.setGravity(Gravity.CENTER_HORIZONTAL);
        author.setTypeface(Typeface.DEFAULT_BOLD);
        author.setTextColor(GREEN);

        // ---- turn on
        heading(l, "Yoqiş / Как включить");
        status = text(l, "", 16);
        button(l, "1. Klaviaturani yoqiş / Включить клавиатуру",
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button(l, "2. Klaviaturani tanlaş / Выбрать клавиатуру", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        // ---- keyboards
        heading(l, "Klaviaturalar / Клавиатуры");
        text(l, "Keraklilarini belgilang. Bayroqli tugma ular orasida almaştiradi.\n"
                + "Отметьте нужные. Кнопка с флагом переключает между ними.", 14);
        for (int i = 0; i < KeyPrefs.LANG_IDS.length; i++) {
            final String id = KeyPrefs.LANG_IDS[i];
            check(l, KeyPrefs.LANG_FLAGS[i] + "  " + KeyPrefs.LANG_NAMES[i] + "   (" + KeyPrefs.LANG_DESC[i] + ")",
                    kp.isLangOn(id), (b, on) -> {
                        if (!kp.setLangOn(id, on)) {
                            b.setChecked(true);
                            Toast.makeText(this, "Hech bölmasa bitta klaviatura kerak / Нужна хотя бы одна клавиатура",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        // ---- look
        heading(l, "Körinişi / Внешний вид");
        choice(l, "Tugmalar rangi / Цвет клавиш",
                new String[]{"Apple", "Bir rang / Один цвет", "Kamalak / Радуга", "Har biri özgaça / Каждая своя"},
                kp.colorMode(), v -> {
                    kp.putInt("cmode", v);
                    refreshColors();
                });
        colorBox = new LinearLayout(this);
        colorBox.setOrientation(LinearLayout.VERTICAL);
        l.addView(colorBox);
        refreshColors();
        choice(l, "Tugmalar şakli / Форма клавиш",
                new String[]{"Apple", "Yumaloq burçak / Скруглённые", "Dumaloq / Круглые"},
                kp.shape(), v -> kp.putInt("shape", v));
        choice(l, "Tugmalar balandligi / Высота клавиш",
                new String[]{"Past / Низкая", "Örta / Средняя", "Baland / Высокая"},
                kp.height(), v -> kp.putInt("height", v));
        int w = 1;
        for (int i = 0; i < KeyPrefs.WEIGHTS.length; i++) {
            if (KeyPrefs.WEIGHTS[i] == kp.weight()) w = i;
        }
        choice(l, "Harflar qalinligi / Жирность клавиш",
                new String[]{"Ingiçka / Тонкий", "Oddiy / Обычный", "Örta / Средний", "Qalin / Жирный"},
                w, v -> kp.putInt("weight", KeyPrefs.WEIGHTS[v]));
        choice(l, "Mavzu / Тема",
                new String[]{"Telefondagidek / Как в телефоне", "Yoruğ / Светлая", "Qoronği / Тёмная"},
                kp.theme(), v -> kp.putInt("theme", v));

        // ---- typing
        heading(l, "Yoziş / Ввод");
        check(l, "Harflarni almaştiriş (o' → ö, sh → ş) / Замена букв", kp.replace(), (b, on) -> kp.setReplace(on));
        check(l, "Tezkor iboralar / Быстрые фразы\naa, Aa, AA → Assalomu Alaykum · vv → Va Alaykum Assalom · rr → Rahmat",
                kp.phrases(), (b, on) -> kp.putBool("phrases", on));
        check(l, "Söz takliflari / Подсказки слов", kp.suggest(), (b, on) -> kp.putBool("suggest", on));
        final TextView volText = text(l, "Ovoz balandligi / Громкость: " + kp.volume() + "%", 15);
        volText.setPadding(0, dp(12), 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(kp.volume());
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                volText.setText("Ovoz balandligi / Громкость: " + v + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                int v = s.getProgress();
                kp.putInt("volume", v);
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null && v > 0) am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, v / 100f);
            }
        });
        l.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        button(l, "Söz xotirasini tozalaş / Очистить память слов", v -> {
            kp.clearWordMemory(this);
            Toast.makeText(this, "Tozalandi / Очищено", Toast.LENGTH_SHORT).show();
        });

        // ---- how to use
        heading(l, "Qanday foydalaniş");
        steps(l,
                "Odatdagidek yozing — harflar özi almaşadi:\no' → ö    g' → ğ    sh → ş    ch → ç",
                "aa + probel → Assalomu Alaykum\nvv + probel → Va Alaykum Assalom\nrr + probel → Rahmat\n(Aa va AA ham işlaydi)",
                "Almaşiş kerak bölmasa (masalan, inglizça «sh») — darhol ⌫ ni bosing.",
                "Bayroqli tugma (🇺🇿 YA) — klaviaturani almaştiradi. Bosib tursangiz — telefondagi boşqa klaviaturalar.",
                "😊 — smayliklar. «öğ» tugmasi — almaşişni yoqadi / öçiradi.",
                "Klaviatura köp yozgan sözlaringizni eslab qoladi va tepada taklif qiladi.",
                "o, g, s, c ni bosib turing — ö, ğ, ş, ç.",
                "Ikki marta probel — nuqta. Probelni bosib turib suring — kursor yuradi.");

        heading(l, "Как пользоваться");
        steps(l,
                "Пишите как обычно — буквы меняются сами:\no' → ö    g' → ğ    sh → ş    ch → ç",
                "aa + пробел → Assalomu Alaykum\nvv + пробел → Va Alaykum Assalom\nrr + пробел → Rahmat\n(работают и Aa, AA)",
                "Если замена не нужна (например, английское «sh») — сразу нажмите ⌫.",
                "Кнопка с флагом (🇺🇿 YA) — сменить клавиатуру. Если удерживать — другие клавиатуры телефона.",
                "😊 — смайлики. Кнопка «öğ» — включить / выключить замену.",
                "Клавиатура запоминает ваши частые слова и подсказывает их сверху.",
                "Удерживайте o, g, s, c — ö, ğ, ş, ç.",
                "Два пробела — точка. Удерживайте пробел и ведите пальцем — курсор.");

        heading(l, "Sinab köring / Проверка");
        EditText test = new EditText(this);
        test.setHint("Bu yerda yozing / Пишите здесь");
        test.setMinLines(3);
        l.addView(test, new LinearLayout.LayoutParams(-1, -2));

        // ---- updates
        updateHeading = heading(l, "Yangilaniş / Обновления");
        updateText = text(l, "", 14);
        newText = text(l, "", 14);
        newText.setVisibility(View.GONE);
        button(l, "Yangilanişni tekşiriş / Проверить обновления", v -> checkUpdate());
        downloadButton = button(l, "Yuklab oliş / Скачать обновление", v -> Updater.openDownload(MainActivity.this));
        downloadButton.setVisibility(View.GONE);

        String mine = Updater.localWhatsNew(this);
        if (mine.length() > 0) {
            heading(l, "Yangiliklar / Что нового (" + Updater.localName(this) + ")");
            text(l, mine, 14);
        }

        TextView footer = text(l, "Uzbek Keys " + Updater.localName(this) + "  ·  " + AUTHOR, 13);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(24), 0, dp(8));

        setContentView(sv);
        checkUpdate();
    }

    // ---------------------------------------------------------------- key colours

    private void refreshColors() {
        colorBox.removeAllViews();
        int mode = kp.colorMode();
        if (mode == KeyPrefs.COLOR_APPLE) {
            text(colorBox, "Apple kabi / Как у Apple.", 14);
        } else if (mode == KeyPrefs.COLOR_RAINBOW) {
            text(colorBox, "Har bir tugma özgaça rangda / Все клавиши разного цвета.", 14);
        } else if (mode == KeyPrefs.COLOR_ONE) {
            text(colorBox, "Harflar / Буквы", 14);
            swatches(colorBox, kp.oneColor(), v -> {
                kp.putInt("one", v);
                refreshColors();
            });
            text(colorBox, "Xizmat tugmalari / Служебные клавиши (⇧ ⌫ 123 ↵)", 14);
            swatches(colorBox, kp.oneSpecial(), v -> {
                kp.putInt("oneSp", v);
                refreshColors();
            });
        } else {
            text(colorBox, "1. Rangni tanlang / Выберите цвет", 14);
            swatches(colorBox, kp.brush(), v -> {
                kp.putInt("brush", v);
                refreshColors();
            });
            text(colorBox, "2. Tugmaga bosing — u böyaladi / Нажмите на клавишу — она окрасится", 14);
            choice(colorBox, "Klaviatura / Раскладка", new String[]{"Lotin / Латиница", "Ўзбекча", "Русский"},
                    paintLayout, v -> {
                        paintLayout = v;
                        refreshColors();
                    });
            paintGrid(colorBox);
            button(colorBox, "Ranglarni tiklaş / Сбросить цвета", v -> {
                kp.clearCustomColors();
                refreshColors();
            });
        }
    }

    private void paintGrid(LinearLayout parent) {
        String[] rows = paintLayout == 1 ? PAINT_UZC : (paintLayout == 2 ? PAINT_RU : PAINT_LAT);
        HashMap<String, Integer> custom = kp.customColors();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFFD3D6DC);
        box.setPadding(dp(3), dp(6), dp(3), dp(6));
        for (String r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < r.length(); i++) {
                String ch = String.valueOf(r.charAt(i));
                row.addView(paintKey(ch, ch, custom), keyLp(1));
            }
            box.addView(row);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] s : PAINT_SPECIAL) {
            row.addView(paintKey(s[0], s[1], custom), keyLp(s[0].equals("SPACE") ? 2.2f : 1));
        }
        box.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        parent.addView(box, lp);
    }

    private LinearLayout.LayoutParams keyLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private TextView paintKey(final String id, String label, HashMap<String, Integer> custom) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setSingleLine(true);
        Integer c = custom.get(id);
        int bg = c != null ? c : 0xFFFFFFFF;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(5));
        d.setColor(bg);
        t.setBackground(d);
        t.setTextColor(KeyPrefs.textOn(bg));
        t.setPadding(0, dp(9), 0, dp(9));
        t.setOnClickListener(v -> {
            kp.setCustomColor(id, kp.brush());
            refreshColors();
        });
        return t;
    }

    // ---------------------------------------------------------------- status and updates

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshStatus();
    }

    private void refreshStatus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean on = false;
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getPackageName().equals(getPackageName())) on = true;
            }
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean chosen = current != null && current.startsWith(getPackageName() + "/");
        String s;
        if (!on) s = "⚠ Klaviatura yoqilmagan / Клавиатура не включена — нажмите 1";
        else if (!chosen) s = "⚠ Klaviatura tanlanmagan / Клавиатура не выбрана — нажмите 2";
        else s = "✅ Hammasi tayyor / Всё готово";
        status.setText(s);
    }

    /** Shows what the new build adds or fixes, above the download button. */
    private void showRemoteWhatsNew() {
        Updater.fetchWhatsNew(this, t -> {
            if (t == null || t.length() == 0) {
                newText.setVisibility(View.GONE);
                return;
            }
            newText.setText("Yangi versiyada / В новой версии:\n" + t);
            newText.setVisibility(View.VISIBLE);
        });
    }

    private void checkUpdate() {
        updateText.setText("Tekşirilmoqda… / Проверяю…");
        Updater.check(this, (remote, local) -> {
            if (remote < 0) {
                updateText.setText("Internet yöq / Нет связи с сервером обновлений");
                downloadButton.setVisibility(View.GONE);
                newText.setVisibility(View.GONE);
            } else if (remote > local) {
                updateText.setText("Yangi versiya: " + remote + " (sizda " + local + ") / Есть новая версия");
                downloadButton.setVisibility(View.VISIBLE);
                showRemoteWhatsNew();
                scroll.post(() -> scroll.smoothScrollTo(0, updateHeading.getTop()));
            } else {
                updateText.setText("Eng söngi versiya / Последняя версия (" + local + ")");
                downloadButton.setVisibility(View.GONE);
                newText.setVisibility(View.GONE);
            }
        });
    }
}
