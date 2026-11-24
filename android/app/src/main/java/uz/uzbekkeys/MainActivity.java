package uz.uzbekkeys;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Setup screen: instructions, turn the keyboard on, choose it, try it, check for updates. by işax */
public class MainActivity extends Activity {

    private static final int GREEN = 0xFF29923F;
    private static final String AUTHOR = "by işax";

    private TextView status;
    private TextView updateText;
    private Button downloadButton;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
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

        heading(l, "Yoqiş / Как включить");
        status = text(l, "", 16);
        button(l, "1. Klaviaturani yoqiş / Включить клавиатуру", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        button(l, "2. Klaviaturani tanlaş / Выбрать клавиатуру", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        heading(l, "Qanday foydalaniş");
        steps(l,
                "Odatdagidek yozing — harflar özi almaşadi:\no' → ö    g' → ğ    sh → ş    ch → ç",
                "aa + probel → Assalomu Alaykum\nvv + probel → Va Alaykum Assalom",
                "Almaşiş kerak bölmasa (masalan, inglizça «sh») — darhol ⌫ ni bosing.",
                "«öğ» tugmasi — almaşişni yoqadi / öçiradi.",
                "o, g, s, c ni bosib turing — ö, ğ, ş, ç.",
                "Ikki marta probel — nuqta. Probelni bosib turib suring — kursor yuradi.");

        heading(l, "Как пользоваться");
        steps(l,
                "Пишите как обычно — буквы меняются сами:\no' → ö    g' → ğ    sh → ş    ch → ç",
                "aa + пробел → Assalomu Alaykum\nvv + пробел → Va Alaykum Assalom",
                "Если замена не нужна (например, английское «sh») — сразу нажмите ⌫.",
                "Кнопка «öğ» — включить / выключить замену.",
                "Удерживайте o, g, s, c — ö, ğ, ş, ç.",
                "Два пробела — точка. Удерживайте пробел и ведите пальцем — курсор.");

        heading(l, "Sinab köring / Проверка");
        EditText test = new EditText(this);
        test.setHint("Bu yerda yozing / Пишите здесь");
        test.setMinLines(3);
        l.addView(test, new LinearLayout.LayoutParams(-1, -2));

        heading(l, "Yangilaniş / Обновления");
        updateText = text(l, "", 14);
        button(l, "Yangilanişni tekşiriş / Проверить обновления", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdate();
            }
        });
        downloadButton = button(l, "Yuklab oliş / Скачать обновление", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Updater.openDownload(MainActivity.this);
            }
        });
        downloadButton.setVisibility(View.GONE);

        TextView footer = text(l, "Uzbek Keys " + Updater.localName(this) + "  ·  " + AUTHOR, 13);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(24), 0, dp(8));

        setContentView(sv);
        checkUpdate();
    }

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

    private void checkUpdate() {
        updateText.setText("Tekşirilmoqda… / Проверяю…");
        Updater.check(this, new Updater.Callback() {
            @Override
            public void onResult(int remote, int local) {
                if (remote < 0) {
                    updateText.setText("Internet yöq / Нет связи с сервером обновлений");
                    downloadButton.setVisibility(View.GONE);
                } else if (remote > local) {
                    updateText.setText("Yangi versiya: " + remote + " (sizda " + local + ") / Есть новая версия");
                    downloadButton.setVisibility(View.VISIBLE);
                } else {
                    updateText.setText("Eng söngi versiya / Последняя версия (" + local + ")");
                    downloadButton.setVisibility(View.GONE);
                }
            }
        });
    }
}
