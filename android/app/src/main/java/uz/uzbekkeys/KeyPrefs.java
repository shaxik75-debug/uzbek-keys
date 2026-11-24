package uz.uzbekkeys;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keyboard settings shared by the keyboard and the app (preferences "uk"). by işax */
public final class KeyPrefs {

    public static final String[] LANG_IDS = {"YA", "OZ", "UZ", "RU", "EN"};
    public static final String[] LANG_FLAGS = {"🇺🇿", "🇺🇿", "🇺🇿", "🇷🇺", "🇺🇸"};
    public static final String[] LANG_SHORT = {"YA", "OʻZ", "ЎЗ", "RU", "EN"};
    public static final String[] LANG_NAMES = {"Yangi lotin", "Oʻzbekcha", "Ўзбекча", "Русский", "English"};
    public static final String[] LANG_DESC = {"ö ğ ş ç", "oʻ gʻ", "кириллица", "ЙЦУКЕН", "QWERTY"};

    public static final int COLOR_APPLE = 0;
    public static final int COLOR_ONE = 1;
    public static final int COLOR_RAINBOW = 2;
    public static final int COLOR_EACH = 3;

    public static final int[] WEIGHTS = {300, 400, 500, 700};

    public static final int[] SWATCHES = {
            0xFFFFFFFF, 0xFFD1D3D9, 0xFF8E8E93, 0xFF3A3A3C, 0xFF000000,
            0xFFFF6B6B, 0xFFFF9F43, 0xFFFFD93D, 0xFF6BCB77, 0xFF4ECDC4,
            0xFF4D96FF, 0xFF9B5DE5, 0xFFF15BB5, 0xFFA47148
    };

    private static final String DEFAULT_LANGS = "YA,RU,EN";

    private final SharedPreferences p;

    public KeyPrefs(Context c) {
        p = c.getSharedPreferences("uk", Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return p;
    }

    public static int langIndex(String id) {
        for (int i = 0; i < LANG_IDS.length; i++) {
            if (LANG_IDS[i].equals(id)) return i;
        }
        return 0;
    }

    // ---------------------------------------------------------------- keyboards (languages)

    public List<String> enabledLangs() {
        String s = "," + p.getString("langs", DEFAULT_LANGS) + ",";
        List<String> out = new ArrayList<>();
        for (String id : LANG_IDS) {
            if (s.contains("," + id + ",")) out.add(id);
        }
        if (out.isEmpty()) out.add("YA");
        return out;
    }

    public boolean isLangOn(String id) {
        return enabledLangs().contains(id);
    }

    /** Returns false when this would switch off the last keyboard. */
    public boolean setLangOn(String id, boolean on) {
        List<String> list = enabledLangs();
        if (on) {
            if (!list.contains(id)) list.add(id);
        } else {
            if (list.contains(id) && list.size() <= 1) return false;
            list.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        for (String l : LANG_IDS) {
            if (!list.contains(l)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(l);
        }
        p.edit().putString("langs", sb.toString()).apply();
        return true;
    }

    public String lang() {
        List<String> on = enabledLangs();
        String l = p.getString("lang", "YA");
        return on.contains(l) ? l : on.get(0);
    }

    public void setLang(String id) {
        p.edit().putString("lang", id).apply();
    }

    // ---------------------------------------------------------------- switches and choices

    public boolean replace() {
        return p.getBoolean("enabled", true);
    }

    public void setReplace(boolean v) {
        p.edit().putBoolean("enabled", v).apply();
    }

    public boolean phrases() {
        return p.getBoolean("phrases", true);
    }

    public boolean suggest() {
        return p.getBoolean("suggest", true);
    }

    public int shape() {
        return clamp(p.getInt("shape", 0), 0, 2);
    }

    public int height() {
        return clamp(p.getInt("height", 1), 0, 2);
    }

    public int weight() {
        return p.getInt("weight", 400);
    }

    public int theme() {
        return clamp(p.getInt("theme", 0), 0, 2);
    }

    public int volume() {
        return clamp(p.getInt("volume", 40), 0, 100);
    }

    public int colorMode() {
        return clamp(p.getInt("cmode", COLOR_APPLE), 0, 3);
    }

    public int oneColor() {
        return p.getInt("one", 0xFF4D96FF);
    }

    public int oneSpecial() {
        return p.getInt("oneSp", 0xFF3A3A3C);
    }

    public int brush() {
        return p.getInt("brush", 0xFFFF6B6B);
    }

    public void putInt(String key, int v) {
        p.edit().putInt(key, v).apply();
    }

    public void putBool(String key, boolean v) {
        p.edit().putBoolean(key, v).apply();
    }

    // ---------------------------------------------------------------- own colour for each key

    public HashMap<String, Integer> customColors() {
        HashMap<String, Integer> m = new HashMap<>();
        String s = p.getString("custom", "");
        if (s.isEmpty()) return m;
        for (String part : s.split(";")) {
            int i = part.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                m.put(part.substring(0, i), (int) Long.parseLong(part.substring(i + 1), 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    public void setCustomColor(String id, int color) {
        HashMap<String, Integer> m = customColors();
        m.put(id, color);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(Integer.toHexString(e.getValue()));
        }
        p.edit().putString("custom", sb.toString()).apply();
    }

    public void clearCustomColors() {
        p.edit().remove("custom").apply();
    }

    // ---------------------------------------------------------------- emoji and word memory

    public String[] recentEmoji() {
        String s = p.getString("recent", "").trim();
        return s.isEmpty() ? new String[0] : s.split(" +");
    }

    public void addRecentEmoji(String e) {
        StringBuilder sb = new StringBuilder(e);
        int n = 1;
        for (String x : recentEmoji()) {
            if (x.equals(e) || n >= 32) continue;
            sb.append(' ').append(x);
            n++;
        }
        p.edit().putString("recent", sb.toString()).apply();
    }

    public void clearWordMemory(Context c) {
        WordMemory.deleteAll(c);
        p.edit().putLong("memClear", System.currentTimeMillis()).apply();
    }

    // ---------------------------------------------------------------- colour helpers

    /** Black text on light keys, white text on dark keys. */
    public static int textOn(int color) {
        double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return l > 0.6 ? Color.BLACK : Color.WHITE;
    }

    public static int darker(int color, float f) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * f),
                (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

/** Remembers the words typed with each keyboard and suggests the most frequent ones. */
final class WordMemory {
    private final File dir;
    private final HashMap<String, HashMap<String, Integer>> dicts = new HashMap<>();
    private final HashSet<String> dirty = new HashSet<>();
    private int pending = 0;

    WordMemory(Context c) {
        dir = c.getApplicationContext().getFilesDir();
    }

    private static File file(File dir, String lang) {
        return new File(dir, "words_" + lang.toLowerCase(Locale.ROOT) + ".txt");
    }

    static boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'' || c == 'ʻ' || c == 'ʼ' || c == '‘' || c == '’';
    }

    private HashMap<String, Integer> dict(String lang) {
        HashMap<String, Integer> d = dicts.get(lang);
        if (d != null) return d;
        d = new HashMap<>();
        File f = file(dir, lang);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int t = line.indexOf('\t');
                    if (t <= 0) continue;
                    try {
                        d.put(line.substring(0, t), Integer.parseInt(line.substring(t + 1).trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        dicts.put(lang, d);
        return d;
    }

    void learn(String lang, String word) {
        if (word == null) return;
        String w = word.toLowerCase(Locale.ROOT);
        if (w.length() < 2 || w.length() > 30) return;
        for (int i = 0; i < w.length(); i++) {
            if (!isWordChar(w.charAt(i))) return;
        }
        HashMap<String, Integer> d = dict(lang);
        Integer n = d.get(w);
        d.put(w, n == null ? 1 : n + 1);
        if (d.size() > 5000) prune(d);
        dirty.add(lang);
        if (++pending >= 10) save();
    }

    private static void prune(final HashMap<String, Integer> d) {
        List<String> words = new ArrayList<>(d.keySet());
        Collections.sort(words, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return d.get(a) - d.get(b);
            }
        });
        for (int i = 0; i < 1000 && i < words.size(); i++) d.remove(words.get(i));
    }

    List<String> suggest(String lang, String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (prefix.isEmpty()) return out;
        final HashMap<String, Integer> d = dict(lang);
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String w : d.keySet()) {
            if (w.length() > p.length() && w.startsWith(p)) out.add(w);
        }
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int c = d.get(b) - d.get(a);
                return c != 0 ? c : a.length() - b.length();
            }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    void save() {
        for (String lang : dirty) {
            HashMap<String, Integer> d = dicts.get(lang);
            if (d == null) continue;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file(dir, lang)), "UTF-8")) {
                for (Map.Entry<String, Integer> e : d.entrySet()) {
                    w.write(e.getKey() + "\t" + e.getValue() + "\n");
                }
            } catch (IOException ignored) {
            }
        }
        dirty.clear();
        pending = 0;
    }

    void clearCache() {
        dicts.clear();
        dirty.clear();
        pending = 0;
    }

    static void deleteAll(Context c) {
        File dir = c.getApplicationContext().getFilesDir();
        for (String id : KeyPrefs.LANG_IDS) {
            //noinspection ResultOfMethodCallIgnored
            file(dir, id).delete();
        }
    }
}

/** Emoji panel: categories, icons and lists (space separated). */
final class Emojis {
    static final String[] ICONS = {"🕘", "😀", "👋", "❤️", "🐱", "🍎", "⚽", "🚗", "💡", "🇺🇿"};
    static final String[] TITLES = {
            "OXIRGILAR / НЕДАВНИЕ", "SMAYLIKLAR / СМАЙЛИКИ", "QÖLLAR VA ODAMLAR / ЖЕСТЫ И ЛЮДИ",
            "YURAKLAR VA BELGILAR / СЕРДЕЧКИ И ЗНАКИ", "TABIAT / ПРИРОДА", "OVQAT / ЕДА",
            "FAOLIYAT / ЗАНЯТИЯ", "SAYOHAT / ПУТЕШЕСТВИЯ", "BUYUMLAR / ПРЕДМЕТЫ", "BAYROQLAR / ФЛАГИ"
    };
    private static final String[] LISTS = {
            "",
            "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👻 💀 🤡 💩 👽 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
            "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫶 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 👨 👩 🧔 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 👷 💂 🧕 👰 🤰 🤱 👼 🎅 🤶 🧙 🧚 🧞 🚶 🏃 💃 🕺 👫 👭 👬 💏 💑 👪",
            "❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💯 💢 💥 💫 💦 💨 💬 💭 💤 🔥 ✨ ⭐ 🌟 ✅ ☑️ ✔️ ❌ ❎ ⚠️ ❗ ❓ ‼️ ⁉️ 🆗 🆕 🆒 🆓 🔝 🔴 🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🔶 🔷 🔸 🔹 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ 🔀 🔁 🔂 ➕ ➖ ➗ ✖️ 💲 ♻️ ⚜️ 🔱 📛 🔰 ⭕ ☪️ ☮️ ☯️ 🕉️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓",
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🦎 🐙 🐠 🐟 🐬 🐳 🦈 🐊 🐅 🐆 🦓 🐘 🐪 🐫 🦒 🐄 🐎 🐑 🐐 🦌 🐕 🐈 🐓 🦃 🕊️ 🐇 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌙 ⭐ ☀️ 🌤️ ⛅ 🌧️ ⛈️ 🌩️ ❄️ ☃️ ⛄ 🌈 ☔ 🌊",
            "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 🧈 🥞 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍚 🍙 🍘 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 ☕ 🍵 🧃 🥤 🧋 🧊 🍽️ 🥄 🍴",
            "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚴 🚵 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩 🎉 🎊 🎁 🎈 🎀",
            "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🛵 🏍️ 🛺 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚃 🚋 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 💺 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🕋",
            "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📠 📺 📻 🎙️ ⏱️ ⏰ 🕰️ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🔧 🔨 🛠️ 🔩 ⚙️ 🧲 🧰 🔮 📿 💈 🔭 🔬 💊 💉 🩹 🩺 🧬 🧪 🌡️ 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🪥 🧽 🧴 🛎️ 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 ✉️ 📩 📨 📧 💌 📦 🏷️ 📫 📮 📜 📃 📄 📑 🧾 📊 📈 📉 🗒️ 🗓️ 📆 📅 🗑️ 📇 🗃️ 📋 📁 📂 🗂️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 📐 📏 🧮 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔐 🔒 🔓",
            "🇺🇿 🇷🇺 🇰🇿 🇰🇬 🇹🇯 🇹🇲 🇦🇿 🇹🇷 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇪🇬 🇺🇸 🇬🇧 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇵🇱 🇨🇿 🇸🇪 🇳🇴 🇫🇮 🇩🇰 🇬🇷 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇵🇰 🇦🇫 🇮🇷 🇮🇶 🇮🇩 🇲🇾 🇹🇭 🇻🇳 🇺🇦 🇧🇾 🇬🇪 🇦🇲 🇲🇩 🇱🇹 🇱🇻 🇪🇪 🇨🇦 🇲🇽 🇧🇷 🇦🇷 🇦🇺 🇳🇿 🇿🇦 🇳🇬 🏳️ 🏴 🏁 🚩 🎌"
    };
    private static final String[][] SPLIT = new String[LISTS.length][];

    private Emojis() {
    }

    static String[] list(int i) {
        if (i < 0 || i >= LISTS.length) return new String[0];
        if (SPLIT[i] == null) SPLIT[i] = LISTS[i].isEmpty() ? new String[0] : LISTS[i].trim().split(" +");
        return SPLIT[i];
    }
}
