package uz.uzbekkeys;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Setup screen: turn the keyboard on, choose it, try it, check for updates. */
public class MainActivity extends Activity {

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
        t.setPadding(0, dp(6), 0, dp(6));
        parent.addView(t);
        return t;
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

        text(l, "Uzbek Keys " + Updater.localName(this), 24);
        text(l, "o' → ö    g' → ğ    sh → ş    ch → ç\n"
                + "aa → Assalomu Alaykum\n"
                + "vv → Va Alaykum Assalom", 18);
        text(l, "Tugma «öğ» — almashtirishni yoqish/o'chirish.\n"
                + "⌫ darhol bosilsa — almashtirish bekor bo'ladi.\n"
                + "🌐 — boshqa klaviaturaga o'tish (uzoq bosish — ro'yxat).\n\n"
                + "Кнопка «öğ» — включить/выключить замену.\n"
                + "⌫ сразу после замены — вернуть как было.\n"
                + "🌐 — другая клавиатура (долгое нажатие — список).", 14);

        status = text(l, "", 16);

        button(l, "1. Klaviaturani yoqish / Включить клавиатуру", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        button(l, "2. Klaviaturani tanlash / Выбрать клавиатуру", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        EditText test = new EditText(this);
        test.setHint("Sinab ko'ring / Попробуйте здесь");
        test.setMinLines(3);
        l.addView(test, new LinearLayout.LayoutParams(-1, -2));

        updateText = text(l, "", 14);
        button(l, "Yangilanishni tekshirish / Проверить обновления", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdate();
            }
        });
        downloadButton = button(l, "Yuklab olish / Скачать обновление", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Updater.openDownload(MainActivity.this);
            }
        });
        downloadButton.setVisibility(View.GONE);

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
        updateText.setText("Tekshirilmoqda… / Проверяю…");
        Updater.check(this, new Updater.Callback() {
            @Override
            public void onResult(int remote, int local) {
                if (remote < 0) {
                    updateText.setText("Internet yo'q / Нет связи с сервером обновлений");
                    downloadButton.setVisibility(View.GONE);
                } else if (remote > local) {
                    updateText.setText("Yangi versiya: " + remote + " (sizda " + local + ") / Есть новая версия");
                    downloadButton.setVisibility(View.VISIBLE);
                } else {
                    updateText.setText("Eng so'nggi versiya / Последняя версия (" + local + ")");
                    downloadButton.setVisibility(View.GONE);
                }
            }
        });
    }
}
