import UIKit
import AudioToolbox
import AVFoundation

/// Uzbek Keys keyboard for iPhone, laid out and behaving like the Apple keyboard (iOS 26 look). by işax
/// Keyboards: Yangi lotin (o' -> ö, g' -> ğ, sh -> ş, ch -> ç), Oʻzbekcha (o' -> oʻ, g' -> gʻ),
/// Ўзбекча (Cyrillic), Русский, English; the flag key switches between the ones turned on in ⚙.
/// aa / Aa / AA -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom", rr -> "Rahmat" (Cyrillic: аа, вв, рр).
/// Backspace right after a replacement brings back what was typed; the space bar shows the last replacement.
/// Word suggestions, emoji panel, settings (⚙): keyboards, key colour / shape / height / weight, volume, theme.
/// Apple-like behaviour: key popups, fast typing with several fingers, long-press alternates,
/// double space = ". ", hold space and slide to move the cursor, hold 123 and slide to a symbol.

// MARK: - Settings (kept inside the keyboard; changed with the ⚙ button)

final class KeyPrefs {
    static let langIds = ["YA", "OZ", "UZ", "RU", "EN"]
    static let flags = ["🇺🇿", "🇺🇿", "🇺🇿", "🇷🇺", "🇺🇸"]
    static let shorts = ["YA", "OʻZ", "ЎЗ", "RU", "EN"]
    static let names = ["Yangi lotin", "Oʻzbekcha", "Ўзбекча", "Русский", "English"]
    static let descs = ["ö ğ ş ç", "oʻ gʻ", "кириллица", "ЙЦУКЕН", "QWERTY"]
    static let swatches: [Int] = [0xFFFFFF, 0xD1D3D9, 0x8E8E93, 0x3A3A3C, 0x000000, 0xFF6B6B, 0xFF9F43,
                                  0xFFD93D, 0x6BCB77, 0x4ECDC4, 0x4D96FF, 0x9B5DE5, 0xF15BB5, 0xA47148]
    static let weights: [UIFont.Weight] = [.light, .regular, .medium, .bold]

    static func index(_ id: String) -> Int { langIds.firstIndex(of: id) ?? 0 }

    private let d = UserDefaults.standard

    private func bool(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) as? Bool ?? def }
    private func int(_ k: String, _ def: Int) -> Int { d.object(forKey: k) as? Int ?? def }
    private func clamp(_ v: Int, _ lo: Int, _ hi: Int) -> Int { min(max(v, lo), hi) }

    var enabledLangs: [String] {
        let s = d.string(forKey: "langs") ?? "YA,RU,EN"
        let set = Set(s.split(separator: ",").map { String($0) })
        let out = KeyPrefs.langIds.filter { set.contains($0) }
        return out.isEmpty ? ["YA"] : out
    }

    /// Returns false when this would switch off the last keyboard.
    func setLang(_ id: String, on: Bool) -> Bool {
        var list = enabledLangs
        if on {
            if !list.contains(id) { list.append(id) }
        } else {
            if list.contains(id) && list.count <= 1 { return false }
            list.removeAll { $0 == id }
        }
        let ordered = KeyPrefs.langIds.filter { list.contains($0) }
        d.set(ordered.joined(separator: ","), forKey: "langs")
        return true
    }

    var lang: String {
        get {
            let l = d.string(forKey: "lang") ?? "YA"
            let on = enabledLangs
            return on.contains(l) ? l : on[0]
        }
        set { d.set(newValue, forKey: "lang") }
    }

    var replace: Bool { get { bool("enabled", true) } set { d.set(newValue, forKey: "enabled") } }
    var phrases: Bool { get { bool("phrases", true) } set { d.set(newValue, forKey: "phrases") } }
    var suggest: Bool { get { bool("suggest", true) } set { d.set(newValue, forKey: "suggest") } }
    var shape: Int { get { clamp(int("shape", 0), 0, 2) } set { d.set(newValue, forKey: "shape") } }
    var height: Int { get { clamp(int("height", 1), 0, 2) } set { d.set(newValue, forKey: "height") } }
    var weight: Int { get { clamp(int("weight", 1), 0, 3) } set { d.set(newValue, forKey: "weight") } }
    var theme: Int { get { clamp(int("theme", 0), 0, 2) } set { d.set(newValue, forKey: "theme") } }
    var volume: Int { get { clamp(int("volume", 40), 0, 100) } set { d.set(newValue, forKey: "volume") } }
    var colorMode: Int { get { clamp(int("cmode", 0), 0, 3) } set { d.set(newValue, forKey: "cmode") } }
    var oneColor: Int { get { int("one", 0x4D96FF) } set { d.set(newValue, forKey: "one") } }
    var oneSpecial: Int { get { int("oneSp", 0x3A3A3C) } set { d.set(newValue, forKey: "oneSp") } }
    var brush: Int { get { int("brush", 0xFF6B6B) } set { d.set(newValue, forKey: "brush") } }
    var custom: [String: Int] {
        get { d.dictionary(forKey: "custom") as? [String: Int] ?? [:] }
        set { d.set(newValue, forKey: "custom") }
    }
    var recent: [String] {
        get { d.stringArray(forKey: "recent") ?? [] }
        set { d.set(newValue, forKey: "recent") }
    }

    func addRecent(_ e: String) {
        var r = recent.filter { $0 != e }
        r.insert(e, at: 0)
        recent = Array(r.prefix(32))
    }
}

func rgb(_ v: Int) -> UIColor {
    UIColor(red: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255,
            blue: CGFloat(v & 255) / 255, alpha: 1)
}

/// Black text on light keys, white text on dark keys.
func textOn(_ c: UIColor) -> UIColor {
    var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
    guard c.getRed(&r, green: &g, blue: &b, alpha: &a) else { return .black }
    return (0.299 * r + 0.587 * g + 0.114 * b) > 0.6 ? .black : .white
}

func darker(_ c: UIColor) -> UIColor {
    var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
    guard c.getHue(&h, saturation: &s, brightness: &b, alpha: &a) else { return c }
    return UIColor(hue: h, saturation: s, brightness: b * 0.8, alpha: a)
}

// MARK: - Word memory

/// Remembers the words typed with each keyboard and suggests the most frequent ones.
final class WordMemory {
    private var dicts: [String: [String: Int]] = [:]
    private var dirty = Set<String>()
    private var pending = 0

    static func isWordChar(_ c: Character) -> Bool {
        c.isLetter || c == "'" || c == "ʻ" || c == "ʼ" || c == "‘" || c == "’"
    }

    private func dict(_ lang: String) -> [String: Int] {
        if let x = dicts[lang] { return x }
        let x = UserDefaults.standard.dictionary(forKey: "words_" + lang) as? [String: Int] ?? [:]
        dicts[lang] = x
        return x
    }

    func learn(_ lang: String, _ word: String) {
        let w = word.lowercased()
        guard w.count >= 2, w.count <= 30, w.allSatisfy({ WordMemory.isWordChar($0) }) else { return }
        var d = dict(lang)
        d[w, default: 0] += 1
        if d.count > 5000 {
            for (k, _) in d.sorted(by: { $0.value < $1.value }).prefix(1000) { d[k] = nil }
        }
        dicts[lang] = d
        dirty.insert(lang)
        pending += 1
        if pending >= 10 { save() }
    }

    func suggest(_ lang: String, _ prefix: String, max n: Int) -> [String] {
        let p = prefix.lowercased()
        guard !p.isEmpty else { return [] }
        let d = dict(lang)
        let found = d.filter { $0.key.count > p.count && $0.key.hasPrefix(p) }
        let sorted = found.sorted { a, b in a.value != b.value ? a.value > b.value : a.key.count < b.key.count }
        return sorted.prefix(n).map { $0.key }
    }

    func save() {
        for l in dirty { UserDefaults.standard.set(dicts[l], forKey: "words_" + l) }
        dirty.removeAll()
        pending = 0
    }

    func clear() {
        for l in KeyPrefs.langIds { UserDefaults.standard.removeObject(forKey: "words_" + l) }
        dicts = [:]
        dirty.removeAll()
        pending = 0
    }
}

// MARK: - Key click sound with adjustable volume

final class ClickSound {
    private var players: [AVAudioPlayer] = []
    private var next = 0

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        let data = ClickSound.wav()
        for _ in 0..<4 {
            if let p = try? AVAudioPlayer(data: data) {
                p.prepareToPlay()
                players.append(p)
            }
        }
    }

    func play(volume: Float) {
        if players.isEmpty {
            AudioServicesPlaySystemSound(1104)
            return
        }
        let p = players[next]
        next = (next + 1) % players.count
        p.volume = volume
        p.currentTime = 0
        p.play()
    }

    /// A short "tick" made in code (16-bit mono WAV).
    private static func wav() -> Data {
        let rate = 44100
        let count = rate / 50
        var pcm = Data(capacity: count * 2)
        for i in 0..<count {
            let t = Double(i) / Double(rate)
            let env = exp(-t * 350)
            let v = (sin(2 * Double.pi * 1900 * t) * 0.55 + sin(2 * Double.pi * 3300 * t) * 0.2) * env
            var s = Int16(max(-1, min(1, v)) * 32767).littleEndian
            withUnsafeBytes(of: &s) { pcm.append(contentsOf: $0) }
        }
        var d = Data()
        func u32(_ x: UInt32) {
            var v = x.littleEndian
            withUnsafeBytes(of: &v) { d.append(contentsOf: $0) }
        }
        func u16(_ x: UInt16) {
            var v = x.littleEndian
            withUnsafeBytes(of: &v) { d.append(contentsOf: $0) }
        }
        d.append(contentsOf: Array("RIFF".utf8))
        u32(UInt32(36 + pcm.count))
        d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8))
        u32(16)
        u16(1)
        u16(1)
        u32(UInt32(rate))
        u32(UInt32(rate * 2))
        u16(2)
        u16(16)
        d.append(contentsOf: Array("data".utf8))
        u32(UInt32(pcm.count))
        d.append(pcm)
        return d
    }
}

// MARK: - Emoji

enum Emojis {
    static let icons = ["🕘", "😀", "👋", "❤️", "🐱", "🍎", "⚽", "🚗", "💡", "🇺🇿"]
    static let titles = [
        "OXIRGILAR / НЕДАВНИЕ", "SMAYLIKLAR / СМАЙЛИКИ", "QÖLLAR VA ODAMLAR / ЖЕСТЫ И ЛЮДИ",
        "YURAKLAR VA BELGILAR / СЕРДЕЧКИ И ЗНАКИ", "TABIAT / ПРИРОДА", "OVQAT / ЕДА",
        "FAOLIYAT / ЗАНЯТИЯ", "SAYOHAT / ПУТЕШЕСТВИЯ", "BUYUMLAR / ПРЕДМЕТЫ", "BAYROQLAR / ФЛАГИ"
    ]
    private static let lists = [
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
    ]

    static func list(_ i: Int) -> [String] {
        guard i >= 0, i < lists.count else { return [] }
        return lists[i].split(separator: " ").map { String($0) }
    }
}

// MARK: - Views

/// One key. Touches are handled by TouchView.
final class KeyView: UIView {
    let id: String
    var index = 0
    var cornerShape = 0 { didSet { setNeedsLayout() } }
    let label = UILabel()
    let subLabel = UILabel()
    let icon = UIImageView()
    var baseColor: UIColor = .white { didSet { applyColor() } }
    var pressedColor: UIColor = .lightGray { didSet { applyColor() } }
    var isPressed = false { didSet { applyColor() } }

    init(id: String) {
        self.id = id
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        layer.cornerRadius = 8.5
        layer.shadowOffset = CGSize(width: 0, height: 1)
        layer.shadowRadius = 0
        label.textAlignment = .center
        label.adjustsFontSizeToFitWidth = true
        label.minimumScaleFactor = 0.5
        label.baselineAdjustment = .alignCenters
        subLabel.textAlignment = .right
        subLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        icon.contentMode = .center
        addSubview(label)
        addSubview(subLabel)
        addSubview(icon)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    private func applyColor() {
        backgroundColor = isPressed ? pressedColor : baseColor
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let r: CGFloat = cornerShape == 2 ? min(bounds.width, bounds.height) / 2 : (cornerShape == 1 ? 13 : 8.5)
        layer.cornerRadius = r
        label.frame = bounds.insetBy(dx: 4, dy: 0)
        subLabel.frame = CGRect(x: 0, y: bounds.height - 17, width: bounds.width - 9, height: 14)
        icon.frame = bounds
        layer.shadowPath = UIBezierPath(roundedRect: bounds, cornerRadius: r).cgPath
    }
}

/// Receives every touch of the keyboard (multi-touch); the keys themselves ignore touches.
final class TouchView: UIView {
    var onBegan: ((UITouch, UIEvent?) -> Void)?
    var onMoved: ((UITouch, UIEvent?) -> Void)?
    var onEnded: ((UITouch, UIEvent?, Bool) -> Void)?

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for t in touches { onBegan?(t, event) }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for t in touches { onMoved?(t, event) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        for t in touches { onEnded?(t, event, false) }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        for t in touches { onEnded?(t, event, true) }
    }
}

// MARK: - Keyboard

final class KeyboardViewController: UIInputViewController {

    private enum Mode { case letters, symbols, symbols2, emoji }

    private let latUz = ["qwertyuiop", "asdfghjkl'", "zxcvbnm"]
    private let latEn = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    private let cyrUz = ["йцукенгшўзх", "фқвапролджэ", "ячсмитғбюҳ"]
    private let cyrRu = ["йцукенгшщзх", "фывапролджэ", "ячсмитьбю"]
    private let symbolsRows: [[String]] = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["-", "/", ":", ";", "(", ")", "$", "&", "@", "\""],
        [".", ",", "?", "!", "'"]
    ]
    private let symbols2Rows: [[String]] = [
        ["[", "]", "{", "}", "#", "%", "^", "*", "+", "="],
        ["_", "\\", "|", "~", "<", ">", "€", "£", "₽", "•"],
        [".", ",", "?", "!", "'"]
    ]
    private let latPhrases = [("aa", "Assalomu Alaykum"), ("vv", "Va Alaykum Assalom"), ("rr", "Rahmat")]
    private let cyrPhrases = [("аа", "Ассалому алайкум"), ("вв", "Ва алайкум ассалом"), ("рр", "Раҳмат")]
    private let alternatesByLang: [String: [String: String]] = [
        "YA": ["o": "ö", "g": "ğ", "s": "ş", "c": "ç"],
        "OZ": ["o": "oʻ", "g": "gʻ"],
        "UZ": ["е": "ё", "х": "ъ", "ш": "щ", "и": "ы", "б": "ь"],
        "RU": ["е": "ё", "ь": "ъ"]
    ]

    // MARK: State

    private let prefs = KeyPrefs()
    private let memory = WordMemory()
    private var mode: Mode = .letters
    private var shift = 0                       // 0 off, 1 next letter, 2 caps lock
    private var lastShiftTap = Date.distantPast
    private var lastSpaceTap = Date.distantPast
    private var lastOriginal: String?
    private var lastReplacement: String?
    private var toggleNotice: String?

    // Settings, read from prefs.
    private var replacementsOn = true
    private var lang = "YA"
    private var langCount = 1
    private var phrasesOn = true
    private var suggestOn = true
    private var shapeIdx = 0
    private var heightIdx = 1
    private var weightIdx = 1
    private var themeMode = 0
    private var volume = 40
    private var colorMode = 0
    private var oneColor = 0x4D96FF
    private var oneSpecial = 0x3A3A3C
    private var custom: [String: Int] = [:]

    private var suggestions: [String] = []
    private var painting = false
    private var emojiCat = 1
    private var emojiItems: [String] = []
    private var keyCount = 1

    private var alternates: [String: String] { alternatesByLang[lang] ?? [:] }
    private var hasToggle: Bool { lang == "YA" || lang == "OZ" }
    private var isCyrillic: Bool { lang == "UZ" || lang == "RU" }
    private var letterRows: [String] {
        switch lang {
        case "UZ": return cyrUz
        case "RU": return cyrRu
        case "EN": return latEn
        default: return latUz
        }
    }

    // MARK: Views

    private let keysView = TouchView()
    private let bar = UIView()
    private let gearButton = UIButton(type: .system)
    private var sugButtons: [UIButton] = []
    private var sugSeparators: [UIView] = []
    private let paintLabel = UILabel()
    private let paintDone = UIButton(type: .system)
    private let emojiView = UIView()
    private let emojiTitle = UILabel()
    private let emojiHint = UILabel()
    private let emojiScroll = UIScrollView()
    private var emojiLabels: [UILabel] = []
    private var emojiTabButtons: [UIButton] = []
    private let settingsView = UIView()
    private let settingsTitle = UILabel()
    private let settingsDone = UIButton(type: .system)
    private let settingsScroll = UIScrollView()
    private let settingsStack = UIStackView()
    private let colorStack = UIStackView()
    private let settingsMessage = UILabel()
    private let popup = UIView()
    private let popupLabel = UILabel()
    private weak var popupKey: KeyView?
    private var rows: [[KeyView]] = []
    private var heightConstraint: NSLayoutConstraint?
    private let haptic = UIImpactFeedbackGenerator(style: .light)
    private var clickSound: ClickSound?

    // Grid metrics, kept for hit testing.
    private var gridTop: CGFloat = 10
    private var gridKeyH: CGFloat = 42
    private var gridGap: CGFloat = 11

    // MARK: Touch tracking

    private var touchKeys: [UITouch: KeyView] = [:]
    private var armed: Set<UITouch> = []        // touches that type their key when released
    private var modeTouch: UITouch?
    private var altTouch: UITouch?
    private var altText: String?
    private var cursorTouch: UITouch?
    private var cursorX: CGFloat = 0
    private var holdTimer: Timer?
    private var holdTouch: UITouch?
    private var deleteTimer: Timer?
    private var deleteCount = 0

    // MARK: Appearance

    private var isDark = false
    private var appearanceReady = false
    private var keyColor: UIColor = .white
    private var specialColor: UIColor = .white
    private var pressedSpecialColor: UIColor = .lightGray
    private var textColor: UIColor = .black
    private var shadowCG: CGColor = UIColor.black.cgColor
    private var shadowOpacity: Float = 0
    private var symbolCache: [String: UIImage] = [:]
    private var fullAccess = false
    private let soundQueue = DispatchQueue(label: "uz.uzbekkeys.sound", qos: .userInteractive)

    private var isLandscape: Bool {
        let s = UIScreen.main.bounds.size
        return s.width > s.height
    }
    /// iOS 26 draws every key in the same colour and without a shadow.
    private let glassStyle: Bool = {
        if #available(iOS 26.0, *) { return true }
        return false
    }()

    private var barHeight: CGFloat { isLandscape ? 32 : 40 }
    private var keyboardHeight: CGFloat {
        let keys: CGFloat = isLandscape ? 162 : [196, 216, 246][heightIdx]
        return barHeight + keys
    }

    private func loadSettings() {
        langCount = prefs.enabledLangs.count
        lang = prefs.lang
        replacementsOn = prefs.replace
        phrasesOn = prefs.phrases
        suggestOn = prefs.suggest
        shapeIdx = prefs.shape
        heightIdx = prefs.height
        weightIdx = prefs.weight
        themeMode = prefs.theme
        volume = prefs.volume
        colorMode = prefs.colorMode
        oneColor = prefs.oneColor
        oneSpecial = prefs.oneSpecial
        custom = prefs.custom
    }

    /// Returns true when the colours changed (the keys then need restyling).
    @discardableResult
    private func refreshAppearance(force: Bool = false) -> Bool {
        let d: Bool
        switch themeMode {
        case 1: d = false
        case 2: d = true
        default: d = textDocumentProxy.keyboardAppearance == .dark || traitCollection.userInterfaceStyle == .dark
        }
        if appearanceReady && d == isDark && !force { return false }
        appearanceReady = true
        isDark = d
        keyColor = d ? UIColor(white: 0.43, alpha: 1) : .white
        if glassStyle {
            specialColor = keyColor
            pressedSpecialColor = d ? UIColor(white: 0.62, alpha: 1) : UIColor(white: 0.80, alpha: 1)
        } else {
            specialColor = d ? UIColor(white: 0.27, alpha: 1) : UIColor(red: 0.67, green: 0.69, blue: 0.73, alpha: 1)
            pressedSpecialColor = keyColor
        }
        textColor = d ? .white : .black
        shadowCG = UIColor(white: 0, alpha: d ? 0.45 : 0.3).cgColor
        shadowOpacity = glassStyle ? 0 : 1
        if themeMode == 0 {
            view.backgroundColor = .clear
        } else {
            view.backgroundColor = d ? UIColor(white: 0.17, alpha: 1) : UIColor(red: 0.82, green: 0.84, blue: 0.86, alpha: 1)
        }
        styleBar()
        return true
    }

    // MARK: Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        loadSettings()

        keysView.isMultipleTouchEnabled = true
        keysView.backgroundColor = .clear
        keysView.onBegan = { [weak self] t, e in self?.touchBegan(t, e) }
        keysView.onMoved = { [weak self] t, e in self?.touchMoved(t, e) }
        keysView.onEnded = { [weak self] t, e, c in self?.touchEnded(t, e, cancelled: c) }
        view.addSubview(keysView)

        setupBar()
        setupEmoji()
        setupSettings()

        popup.isUserInteractionEnabled = false
        popup.isHidden = true
        popup.layer.cornerRadius = 10
        popup.layer.shadowColor = UIColor.black.cgColor
        popup.layer.shadowOffset = CGSize(width: 0, height: 1)
        popup.layer.shadowRadius = 4
        popup.layer.shadowOpacity = 0.3
        popupLabel.textAlignment = .center
        popupLabel.font = .systemFont(ofSize: 34, weight: .light)
        popupLabel.adjustsFontSizeToFitWidth = true
        popupLabel.minimumScaleFactor = 0.5
        popup.addSubview(popupLabel)
        view.addSubview(popup)

        let h = view.heightAnchor.constraint(equalToConstant: keyboardHeight)
        h.priority = UILayoutPriority(999)
        h.isActive = true
        heightConstraint = h

        refreshAppearance(force: true)
        buildKeys()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        loadSettings()
        lastOriginal = nil
        lastReplacement = nil
        toggleNotice = nil
        painting = false
        if shift == 2 { shift = 0 }
        fullAccess = hasFullAccess
        if fullAccess { haptic.prepare() }
        mode = .letters
        emojiView.isHidden = true
        settingsView.isHidden = true
        keysView.isHidden = false
        bar.isHidden = false
        heightConstraint?.constant = keyboardHeight
        refreshAppearance(force: true)
        buildKeys()
        updateAutoShift()
        updateSuggestions()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        memory.save()
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        heightConstraint?.constant = keyboardHeight
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        layoutAll()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        if refreshAppearance() { restyleKeys() } else { restyleDynamicKeys() }
        updateAutoShift()
        updateSuggestions()
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if refreshAppearance() { restyleKeys() }
    }

    // MARK: Suggestions bar

    private func setupBar() {
        bar.backgroundColor = .clear
        view.addSubview(bar)
        gearButton.setImage(UIImage(systemName: "gearshape"), for: .normal)
        gearButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        bar.addSubview(gearButton)
        for i in 0..<3 {
            let b = UIButton(type: .system)
            b.titleLabel?.font = .systemFont(ofSize: 16)
            b.titleLabel?.lineBreakMode = .byTruncatingTail
            b.tag = i
            b.addTarget(self, action: #selector(suggestionTapped(_:)), for: .touchUpInside)
            bar.addSubview(b)
            sugButtons.append(b)
            if i > 0 {
                let s = UIView()
                bar.addSubview(s)
                sugSeparators.append(s)
            }
        }
        paintLabel.font = .systemFont(ofSize: 13)
        paintLabel.numberOfLines = 2
        paintLabel.adjustsFontSizeToFitWidth = true
        paintLabel.minimumScaleFactor = 0.6
        paintLabel.text = "Tugmalarga bosing — böyaladi\nНажимайте на клавиши — они окрасятся"
        paintDone.setTitle("Tayyor / Готово", for: .normal)
        paintDone.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        paintDone.addTarget(self, action: #selector(stopPainting), for: .touchUpInside)
        bar.addSubview(paintLabel)
        bar.addSubview(paintDone)
    }

    private func layoutBar() {
        let W = bar.bounds.width
        let H = bar.bounds.height
        gearButton.frame = CGRect(x: 0, y: 0, width: 44, height: H)
        let cw = (W - 44) / 3
        for (i, b) in sugButtons.enumerated() {
            b.frame = CGRect(x: 44 + CGFloat(i) * cw + 4, y: 0, width: cw - 8, height: H)
        }
        for (i, s) in sugSeparators.enumerated() {
            s.frame = CGRect(x: 44 + CGFloat(i + 1) * cw, y: H * 0.28, width: 1, height: H * 0.44)
        }
        paintLabel.frame = CGRect(x: 12, y: 0, width: W - 150, height: H)
        paintDone.frame = CGRect(x: W - 136, y: 0, width: 128, height: H)
    }

    private func styleBar() {
        gearButton.tintColor = textColor.withAlphaComponent(0.6)
        for b in sugButtons { b.setTitleColor(textColor, for: .normal) }
        for s in sugSeparators { s.backgroundColor = textColor.withAlphaComponent(0.2) }
        paintLabel.textColor = textColor
        updateBar()
    }

    private func suggestionForSlot(_ slot: Int) -> String? {
        let idx = slot == 1 ? 0 : (slot == 0 ? 1 : 2)        // best guess in the middle, like Apple
        return idx < suggestions.count ? suggestions[idx] : nil
    }

    private func updateBar() {
        gearButton.isHidden = painting
        paintLabel.isHidden = !painting
        paintDone.isHidden = !painting
        for (i, b) in sugButtons.enumerated() {
            b.isHidden = painting
            b.setTitle(suggestionForSlot(i), for: .normal)
        }
        for s in sugSeparators { s.isHidden = painting || !suggestOn }
    }

    @objc private func suggestionTapped(_ b: UIButton) {
        guard let word = suggestionForSlot(b.tag) else { return }
        let w = trailingWord(textDocumentProxy.documentContextBeforeInput ?? "")
        for _ in 0..<w.count { textDocumentProxy.deleteBackward() }
        textDocumentProxy.insertText(word + " ")
        memory.learn(lang, word)
        lastOriginal = nil
        lastReplacement = nil
        toggleNotice = nil
        clickFeedback()
        if shift == 1 { setShift(0) }
        afterEdit()
    }

    private func trailingWord(_ b: String) -> String {
        var chars: [Character] = []
        for c in b.reversed() {
            if WordMemory.isWordChar(c) { chars.append(c) } else { break }
        }
        var w = String(chars.reversed())
        while let f = w.first, !f.isLetter { w.removeFirst() }
        return w
    }

    private func updateSuggestions() {
        var out: [String] = []
        if suggestOn && mode == .letters && !hasSelection() {
            let w = trailingWord(textDocumentProxy.documentContextBeforeInput ?? "")
            if let f = w.first {
                let cap = f.isUppercase
                out = memory.suggest(lang, w, max: 3).map { s in
                    cap ? String(s.prefix(1)).uppercased() + String(s.dropFirst()) : s
                }
            }
        }
        suggestions = out
        updateBar()
    }

    private func learnBefore(_ b: String) {
        guard suggestOn else { return }
        let w = trailingWord(b)
        guard w.count >= 2, phraseFor(w) == nil else { return }
        memory.learn(lang, w)
    }

    // MARK: Emoji

    private func setupEmoji() {
        emojiView.isHidden = true
        view.addSubview(emojiView)
        emojiTitle.font = .systemFont(ofSize: 12, weight: .semibold)
        emojiView.addSubview(emojiTitle)
        emojiHint.font = .systemFont(ofSize: 14)
        emojiHint.textAlignment = .center
        emojiHint.numberOfLines = 2
        emojiHint.text = "Bu yerda oxirgi smayliklar böladi\nЗдесь будут недавние смайлики"
        emojiView.addSubview(emojiHint)
        emojiScroll.showsVerticalScrollIndicator = false
        emojiScroll.alwaysBounceVertical = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(emojiTapped(_:)))
        emojiScroll.addGestureRecognizer(tap)
        emojiView.addSubview(emojiScroll)

        let abc = UIButton(type: .system)
        abc.titleLabel?.font = .systemFont(ofSize: 16)
        abc.tag = -1
        abc.addTarget(self, action: #selector(emojiTabTapped(_:)), for: .touchUpInside)
        emojiTabButtons.append(abc)
        for (i, icon) in Emojis.icons.enumerated() {
            let b = UIButton(type: .custom)
            b.setTitle(icon, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 19)
            b.layer.cornerRadius = 8
            b.tag = i
            b.addTarget(self, action: #selector(emojiTabTapped(_:)), for: .touchUpInside)
            emojiTabButtons.append(b)
        }
        let del = UIButton(type: .system)
        del.setImage(UIImage(systemName: "delete.left"), for: .normal)
        del.tag = -2
        del.addTarget(self, action: #selector(emojiDeleteDown), for: .touchDown)
        del.addTarget(self, action: #selector(emojiDeleteUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        emojiTabButtons.append(del)
        for b in emojiTabButtons { emojiView.addSubview(b) }
    }

    private func openEmoji() {
        mode = .emoji
        hidePopup()
        touchKeys.removeAll()
        armed.removeAll()
        keysView.isHidden = true
        bar.isHidden = true
        emojiView.isHidden = false
        showEmojiCategory(prefs.recent.isEmpty ? 1 : 0)
    }

    private func closeEmoji() {
        mode = .letters
        emojiView.isHidden = true
        keysView.isHidden = false
        bar.isHidden = false
        buildKeys()
        updateAutoShift()
        updateSuggestions()
    }

    private func showEmojiCategory(_ cat: Int) {
        emojiCat = cat
        emojiItems = cat == 0 ? prefs.recent : Emojis.list(cat)
        for l in emojiLabels { l.removeFromSuperview() }
        emojiLabels = emojiItems.map { e in
            let l = UILabel()
            l.text = e
            l.font = .systemFont(ofSize: 30)
            l.textAlignment = .center
            emojiScroll.addSubview(l)
            return l
        }
        emojiHint.isHidden = !emojiItems.isEmpty
        emojiTitle.text = Emojis.titles[cat]
        emojiScroll.contentOffset = .zero
        styleEmoji()
        layoutEmoji()
    }

    private func styleEmoji() {
        emojiTitle.textColor = textColor.withAlphaComponent(0.55)
        emojiHint.textColor = textColor.withAlphaComponent(0.55)
        for b in emojiTabButtons {
            if b.tag == -1 {
                b.setTitle(isCyrillic ? "АБВ" : "ABC", for: .normal)
                b.setTitleColor(textColor, for: .normal)
            } else if b.tag == -2 {
                b.tintColor = textColor
            } else {
                b.backgroundColor = b.tag == emojiCat ? textColor.withAlphaComponent(0.15) : .clear
            }
        }
    }

    private func layoutEmoji() {
        let W = emojiView.bounds.width
        let H = emojiView.bounds.height
        guard W > 0, H > 0 else { return }
        let tabsH: CGFloat = 44
        let capH: CGFloat = 26
        emojiTitle.frame = CGRect(x: 12, y: 6, width: W - 24, height: 16)
        emojiScroll.frame = CGRect(x: 0, y: capH, width: W, height: H - capH - tabsH)
        emojiHint.frame = emojiScroll.frame
        let cw = W / 8
        let ch: CGFloat = 44
        for (i, l) in emojiLabels.enumerated() {
            l.frame = CGRect(x: CGFloat(i % 8) * cw, y: CGFloat(i / 8) * ch, width: cw, height: ch)
        }
        let rowsN = (emojiLabels.count + 7) / 8
        emojiScroll.contentSize = CGSize(width: W, height: CGFloat(rowsN) * ch)
        let uw = W / CGFloat(Emojis.icons.count + 3)
        var x: CGFloat = 0
        for (i, b) in emojiTabButtons.enumerated() {
            let w = (i == 0 || i == emojiTabButtons.count - 1) ? uw * 1.5 : uw
            b.frame = CGRect(x: x, y: H - tabsH + 4, width: w, height: tabsH - 8)
            x += w
        }
    }

    @objc private func emojiTapped(_ g: UITapGestureRecognizer) {
        let p = g.location(in: emojiScroll)
        let cw = emojiScroll.bounds.width / 8
        guard cw > 0, p.x >= 0, p.y >= 0 else { return }
        let idx = Int(p.y / 44) * 8 + min(7, Int(p.x / cw))
        guard idx >= 0, idx < emojiItems.count else { return }
        clickFeedback()
        let e = emojiItems[idx]
        textDocumentProxy.insertText(e)
        prefs.addRecent(e)
        lastOriginal = nil
        lastReplacement = nil
    }

    @objc private func emojiTabTapped(_ b: UIButton) {
        clickFeedback()
        if b.tag == -1 {
            closeEmoji()
        } else if b.tag >= 0 {
            showEmojiCategory(b.tag)
        }
    }

    @objc private func emojiDeleteDown() {
        clickFeedback()
        deleteBegan()
    }

    @objc private func emojiDeleteUp() {
        deleteEnded()
    }

    // MARK: Settings (⚙)

    private func setupSettings() {
        settingsView.isHidden = true
        view.addSubview(settingsView)
        settingsTitle.text = "Sozlamalar / Настройки"
        settingsTitle.font = .systemFont(ofSize: 17, weight: .semibold)
        settingsView.addSubview(settingsTitle)
        settingsDone.setTitle("Tayyor / Готово", for: .normal)
        settingsDone.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        settingsDone.addTarget(self, action: #selector(closeSettings), for: .touchUpInside)
        settingsView.addSubview(settingsDone)
        settingsView.addSubview(settingsScroll)
        settingsStack.axis = .vertical
        settingsStack.spacing = 10
        settingsStack.translatesAutoresizingMaskIntoConstraints = false
        settingsScroll.addSubview(settingsStack)
        NSLayoutConstraint.activate([
            settingsStack.leadingAnchor.constraint(equalTo: settingsScroll.contentLayoutGuide.leadingAnchor, constant: 14),
            settingsStack.trailingAnchor.constraint(equalTo: settingsScroll.contentLayoutGuide.trailingAnchor, constant: -14),
            settingsStack.topAnchor.constraint(equalTo: settingsScroll.contentLayoutGuide.topAnchor, constant: 4),
            settingsStack.bottomAnchor.constraint(equalTo: settingsScroll.contentLayoutGuide.bottomAnchor, constant: -16),
            settingsStack.widthAnchor.constraint(equalTo: settingsScroll.frameLayoutGuide.widthAnchor, constant: -28)
        ])
        colorStack.axis = .vertical
        colorStack.spacing = 8
        settingsMessage.font = .systemFont(ofSize: 13)
        settingsMessage.textColor = .systemRed
        settingsMessage.numberOfLines = 0
    }

    private func layoutSettings() {
        let W = settingsView.bounds.width
        let H = settingsView.bounds.height
        settingsTitle.frame = CGRect(x: 14, y: 4, width: W - 170, height: 36)
        settingsDone.frame = CGRect(x: W - 154, y: 4, width: 146, height: 36)
        settingsScroll.frame = CGRect(x: 0, y: 42, width: W, height: max(0, H - 42))
    }

    private func sectionLabel(_ t: String) -> UILabel {
        let l = UILabel()
        l.text = t
        l.font = .systemFont(ofSize: 13, weight: .semibold)
        l.textColor = .secondaryLabel
        l.numberOfLines = 0
        return l
    }

    private func switchRow(_ title: String, _ on: Bool, _ action: @escaping (UISwitch) -> Void) -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 8
        let l = UILabel()
        l.text = title
        l.font = .systemFont(ofSize: 15)
        l.numberOfLines = 0
        let s = UISwitch()
        s.isOn = on
        s.setContentHuggingPriority(.required, for: .horizontal)
        s.addAction(UIAction { [weak s] _ in
            if let s = s { action(s) }
        }, for: .valueChanged)
        row.addArrangedSubview(l)
        row.addArrangedSubview(s)
        return row
    }

    private func segRow(_ title: String, _ items: [String], _ sel: Int, _ action: @escaping (Int) -> Void) -> UIView {
        let v = UIStackView()
        v.axis = .vertical
        v.spacing = 4
        v.addArrangedSubview(sectionLabel(title))
        let seg = UISegmentedControl(items: items)
        seg.selectedSegmentIndex = sel
        seg.addAction(UIAction { [weak seg] _ in
            if let seg = seg { action(seg.selectedSegmentIndex) }
        }, for: .valueChanged)
        v.addArrangedSubview(seg)
        return v
    }

    private func swatchRow(_ selected: Int, _ action: @escaping (Int) -> Void) -> UIView {
        let sv = UIScrollView()
        sv.showsHorizontalScrollIndicator = false
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 8
        row.translatesAutoresizingMaskIntoConstraints = false
        for c in KeyPrefs.swatches {
            let b = UIButton(type: .custom)
            b.backgroundColor = rgb(c)
            b.layer.cornerRadius = 15
            b.layer.borderWidth = c == selected ? 3 : 1
            b.layer.borderColor = (c == selected ? UIColor.systemBlue : UIColor(white: 0.5, alpha: 0.5)).cgColor
            b.translatesAutoresizingMaskIntoConstraints = false
            b.widthAnchor.constraint(equalToConstant: 30).isActive = true
            b.heightAnchor.constraint(equalToConstant: 30).isActive = true
            b.addAction(UIAction { _ in action(c) }, for: .touchUpInside)
            row.addArrangedSubview(b)
        }
        sv.addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: sv.contentLayoutGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: sv.contentLayoutGuide.trailingAnchor),
            row.topAnchor.constraint(equalTo: sv.contentLayoutGuide.topAnchor, constant: 2),
            row.bottomAnchor.constraint(equalTo: sv.contentLayoutGuide.bottomAnchor, constant: -2),
            sv.heightAnchor.constraint(equalToConstant: 34)
        ])
        return sv
    }

    private func buttonRow(_ title: String, _ action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15)
        b.titleLabel?.numberOfLines = 0
        b.contentHorizontalAlignment = .leading
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return b
    }

    @objc private func openSettings() {
        clickFeedback()
        hidePopup()
        settingsView.backgroundColor = isDark ? UIColor(white: 0.12, alpha: 1) : UIColor(red: 0.95, green: 0.95, blue: 0.97, alpha: 1)
        settingsView.overrideUserInterfaceStyle = isDark ? .dark : .light
        settingsTitle.textColor = textColor
        settingsMessage.text = nil
        buildSettingsContent()
        settingsView.isHidden = false
        keysView.isHidden = true
        bar.isHidden = true
        emojiView.isHidden = true
        layoutSettings()
        settingsScroll.contentOffset = .zero
    }

    @objc private func closeSettings() {
        settingsView.isHidden = true
        keysView.isHidden = false
        bar.isHidden = false
        mode = .letters
        applySettings()
        updateAutoShift()
        updateSuggestions()
    }

    private func applySettings() {
        loadSettings()
        heightConstraint?.constant = keyboardHeight
        refreshAppearance(force: true)
        buildKeys()
        view.setNeedsLayout()
    }

    private func buildSettingsContent() {
        for v in settingsStack.arrangedSubviews { v.removeFromSuperview() }
        let p = prefs

        settingsStack.addArrangedSubview(sectionLabel("KLAVIATURALAR / КЛАВИАТУРЫ"))
        for (i, id) in KeyPrefs.langIds.enumerated() {
            let title = KeyPrefs.flags[i] + "  " + KeyPrefs.names[i] + "  (" + KeyPrefs.descs[i] + ")"
            settingsStack.addArrangedSubview(switchRow(title, p.enabledLangs.contains(id)) { [weak self] s in
                guard let self = self else { return }
                if self.prefs.setLang(id, on: s.isOn) {
                    self.settingsMessage.text = nil
                } else {
                    s.setOn(true, animated: true)
                    self.settingsMessage.text = "Hech bölmasa bitta klaviatura kerak / Нужна хотя бы одна клавиатура"
                }
                self.loadSettings()
            })
        }
        settingsStack.addArrangedSubview(settingsMessage)

        settingsStack.addArrangedSubview(sectionLabel("YOZIŞ / ВВОД"))
        settingsStack.addArrangedSubview(switchRow("Harflarni almaştiriş / Замена букв (o' → ö, sh → ş)", p.replace) { [weak self] s in
            self?.prefs.replace = s.isOn
        })
        settingsStack.addArrangedSubview(switchRow("Tezkor iboralar / Быстрые фразы (aa, Aa, AA → Assalomu Alaykum; vv; rr)", p.phrases) { [weak self] s in
            self?.prefs.phrases = s.isOn
        })
        settingsStack.addArrangedSubview(switchRow("Söz takliflari / Подсказки слов", p.suggest) { [weak self] s in
            self?.prefs.suggest = s.isOn
        })
        let volLabel = sectionLabel("Ovoz balandligi / Громкость: \(p.volume)%")
        settingsStack.addArrangedSubview(volLabel)
        let slider = UISlider()
        slider.minimumValue = 0
        slider.maximumValue = 100
        slider.value = Float(p.volume)
        slider.addAction(UIAction { [weak self, weak slider, weak volLabel] _ in
            guard let self = self, let slider = slider else { return }
            let v = Int(slider.value.rounded())
            volLabel?.text = "Ovoz balandligi / Громкость: \(v)%"
            self.prefs.volume = v
            self.volume = v
        }, for: .valueChanged)
        slider.addAction(UIAction { [weak self] _ in self?.clickFeedback() }, for: [.touchUpInside, .touchUpOutside])
        settingsStack.addArrangedSubview(slider)
        if !hasFullAccess {
            settingsStack.addArrangedSubview(sectionLabel("Ovoz va tebraniş uçun: Sozlamalar → Klaviatura → Uzbek Keys → «Töliq ruxsat».\nДля звука и вибрации включите «Полный доступ»."))
        }
        settingsStack.addArrangedSubview(buttonRow("Söz xotirasini tozalaş / Очистить память слов") { [weak self] in
            self?.memory.clear()
            self?.settingsMessage.text = "Tozalandi / Очищено"
        })

        settingsStack.addArrangedSubview(sectionLabel("KÖRINIŞI / ВНЕШНИЙ ВИД"))
        settingsStack.addArrangedSubview(segRow("Tugmalar rangi / Цвет клавиш", ["Apple", "Один", "Радуга", "Каждая"], p.colorMode) { [weak self] i in
            self?.prefs.colorMode = i
            self?.loadSettings()
            self?.rebuildColorStack()
        })
        settingsStack.addArrangedSubview(colorStack)
        rebuildColorStack()
        settingsStack.addArrangedSubview(segRow("Şakli / Форма", ["Apple", "Скруглённые", "Круглые"], p.shape) { [weak self] i in
            self?.prefs.shape = i
        })
        settingsStack.addArrangedSubview(segRow("Balandligi / Высота", ["Низкая", "Средняя", "Высокая"], p.height) { [weak self] i in
            self?.prefs.height = i
        })
        settingsStack.addArrangedSubview(segRow("Qalinligi / Жирность", ["Тонкий", "Обычный", "Средний", "Жирный"], p.weight) { [weak self] i in
            self?.prefs.weight = i
        })
        settingsStack.addArrangedSubview(segRow("Mavzu / Тема", ["Как в телефоне", "Светлая", "Тёмная"], p.theme) { [weak self] i in
            self?.prefs.theme = i
        })
        let footer = sectionLabel("Uzbek Keys · by işax")
        footer.textAlignment = .center
        settingsStack.addArrangedSubview(footer)
    }

    private func rebuildColorStack() {
        for v in colorStack.arrangedSubviews { v.removeFromSuperview() }
        let p = prefs
        switch p.colorMode {
        case 1:
            colorStack.addArrangedSubview(sectionLabel("Harflar / Буквы"))
            colorStack.addArrangedSubview(swatchRow(p.oneColor) { [weak self] c in
                self?.prefs.oneColor = c
                self?.rebuildColorStack()
            })
            colorStack.addArrangedSubview(sectionLabel("Xizmat tugmalari / Служебные клавиши (⇧ ⌫ 123 ↵)"))
            colorStack.addArrangedSubview(swatchRow(p.oneSpecial) { [weak self] c in
                self?.prefs.oneSpecial = c
                self?.rebuildColorStack()
            })
        case 3:
            colorStack.addArrangedSubview(sectionLabel("Rangni tanlang, keyin «Böyaş» / Выберите цвет, затем «Красить»"))
            colorStack.addArrangedSubview(swatchRow(p.brush) { [weak self] c in
                self?.prefs.brush = c
                self?.rebuildColorStack()
            })
            colorStack.addArrangedSubview(buttonRow("🖌 Böyaş / Красить клавиши") { [weak self] in
                self?.startPainting()
            })
            colorStack.addArrangedSubview(buttonRow("Ranglarni tiklaş / Сбросить цвета") { [weak self] in
                self?.prefs.custom = [:]
                self?.settingsMessage.text = "Tiklandi / Сброшено"
            })
        default:
            break
        }
    }

    private func startPainting() {
        closeSettings()
        painting = true
        updateBar()
    }

    @objc private func stopPainting() {
        painting = false
        updateBar()
    }

    // MARK: Building and layout

    private func currentRows() -> [[String]] {
        var rs: [[String]]
        switch mode {
        case .symbols, .symbols2:
            let src = mode == .symbols ? symbolsRows : symbols2Rows
            rs = [src[0], src[1], [mode == .symbols ? "SYM2" : "SYM1"] + src[2] + ["DEL"]]
        default:
            let src = letterRows
            rs = [src[0].map { String($0) }, src[1].map { String($0) }, ["SHIFT"] + src[2].map { String($0) } + ["DEL"]]
        }
        var bottom = [mode == .symbols || mode == .symbols2 ? "ABC" : "SYM1"]
        if langCount > 1 || needsInputModeSwitchKey { bottom.append("LANG") }
        bottom.append("EMOJI")
        if hasToggle { bottom.append("TOGGLE") }
        bottom.append("SPACE")
        bottom.append("RET")
        rs.append(bottom)
        return rs
    }

    private func buildKeys() {
        hidePopup()
        for k in rows.joined() { k.removeFromSuperview() }
        var idx = 0
        var built: [[KeyView]] = []
        for ids in currentRows() {
            var row: [KeyView] = []
            for id in ids {
                let k = KeyView(id: id)
                k.index = idx
                idx += 1
                keysView.addSubview(k)
                row.append(k)
            }
            built.append(row)
        }
        rows = built
        keyCount = max(1, idx)
        restyleKeys()
        layoutAll()
    }

    private func layoutAll() {
        let W = view.bounds.width
        let H = view.bounds.height
        guard W > 0, H > 0 else { return }
        let bh = barHeight
        bar.frame = CGRect(x: 0, y: 0, width: W, height: bh)
        layoutBar()
        keysView.frame = CGRect(x: 0, y: bh, width: W, height: H - bh)
        emojiView.frame = view.bounds
        settingsView.frame = view.bounds
        if mode == .emoji { layoutEmoji() }
        if !settingsView.isHidden { layoutSettings() }
        guard rows.count >= 4 else { return }

        let KH = H - bh
        let land = isLandscape
        let side: CGFloat = land ? 5 : 6
        let g10: CGFloat = 6
        let topPad: CGFloat = land ? 6 : 8
        let bottomPad: CGFloat = land ? 4 : 6
        let rowGap: CGFloat = land ? 7 : 11
        let keyH = max(20, (KH - topPad - bottomPad - 3 * rowGap) / 4)
        let cols = max(10, max(rows[0].count, rows[1].count))
        let gap: CGFloat = cols > 10 ? 4.5 : g10
        let unit = (W - 2 * side - CGFloat(cols - 1) * gap) / CGFloat(cols)
        let unit10 = (W - 2 * side - 9 * g10) / 10
        gridTop = topPad
        gridKeyH = keyH
        gridGap = rowGap

        for (r, row) in rows.enumerated() {
            let y = topPad + CGFloat(r) * (keyH + rowGap)
            var frames: [CGRect] = []
            func add(_ x: CGFloat, _ w: CGFloat) {
                frames.append(CGRect(x: x, y: y, width: w, height: keyH))
            }
            if r < 2 {
                let n = CGFloat(row.count)
                var x = (W - (n * unit + (n - 1) * gap)) / 2
                for _ in row { add(x, unit); x += unit + gap }
            } else if r == 2 {
                let m = row.count - 2
                let mf = CGFloat(m)
                if mode == .letters || mode == .emoji {
                    var wide = unit10 * 1.3 + 2
                    var lw = unit
                    var need = mf * lw + (mf - 1) * gap + 2 * wide + 2 * g10 + 2 * side
                    if need > W {
                        wide = max(unit, wide - (need - W) / 2)
                        need = mf * lw + (mf - 1) * gap + 2 * wide + 2 * gap + 2 * side
                        if need > W { lw = (W - 2 * side - 2 * wide - (mf + 1) * gap) / mf }
                    }
                    add(side, wide)
                    var x = (W - (mf * lw + (mf - 1) * gap)) / 2
                    for _ in 0..<m { add(x, lw); x += lw + gap }
                    add(W - side - wide, wide)
                } else {
                    let wide = unit10 * 1.3 + 2
                    let inner = W - 2 * side - 2 * wide - 4 * g10
                    let w = (inner - (mf - 1) * g10) / mf
                    add(side, wide)
                    var x = side + wide + 2 * g10
                    for _ in 0..<m { add(x, w); x += w + g10 }
                    add(W - side - wide, wide)
                }
            } else {
                let fixed: [CGFloat] = row.map { k in
                    switch k.id {
                    case "SPACE": return 0
                    case "RET": return unit10 * 1.9
                    case "LANG": return unit10 * (langCount > 1 ? 1.5 : 1.1)
                    case "EMOJI", "TOGGLE": return unit10 * 1.05
                    default: return unit10 * 1.25
                    }
                }
                let used = fixed.reduce(0, +) + CGFloat(row.count - 1) * g10
                let spaceW = max(unit10, W - 2 * side - used)
                var x = side
                for (i, k) in row.enumerated() {
                    let w = k.id == "SPACE" ? spaceW : fixed[i]
                    add(x, w)
                    x += w + g10
                }
            }
            for (i, k) in row.enumerated() where i < frames.count {
                k.frame = frames[i]
            }
        }
    }

    private func key(at p: CGPoint) -> KeyView? {
        guard !rows.isEmpty else { return nil }
        var r = Int(floor((p.y - gridTop + gridGap / 2) / (gridKeyH + gridGap)))
        r = min(max(r, 0), rows.count - 1)
        var best: KeyView?
        var bestD = CGFloat.greatestFiniteMagnitude
        for k in rows[r] {
            let f = k.frame
            let d = p.x < f.minX ? f.minX - p.x : (p.x > f.maxX ? p.x - f.maxX : 0)
            if d < bestD {
                bestD = d
                best = k
            }
        }
        return best
    }

    // MARK: Styling

    private func symbol(_ name: String, size: CGFloat = 20) -> UIImage? {
        let cacheKey = "\(name)-\(size)"
        if let img = symbolCache[cacheKey] { return img }
        let img = UIImage(systemName: name, withConfiguration: UIImage.SymbolConfiguration(pointSize: size, weight: .regular))
        symbolCache[cacheKey] = img
        return img
    }

    private func isCharacter(_ id: String) -> Bool { id.count == 1 }

    private func restyleKeys() {
        for k in rows.joined() { style(k) }
    }

    /// Restyles only the keys that change while typing.
    private func restyleDynamicKeys() {
        for k in rows.joined() where k.id == "SPACE" || k.id == "RET" || k.id == "TOGGLE" {
            style(k)
        }
    }

    /// Key colours for the chosen colour mode: (normal, pressed, Apple look).
    private func colors(for k: KeyView, special: Bool) -> (UIColor, UIColor, Bool) {
        switch colorMode {
        case 1:
            let c = rgb(special ? oneSpecial : oneColor)
            return (c, darker(c), false)
        case 2:
            let h = CGFloat(k.index) / CGFloat(keyCount) * 0.92
            let c = UIColor(hue: h, saturation: isDark ? 0.55 : 0.40, brightness: isDark ? 0.62 : 1, alpha: 1)
            return (c, darker(c), false)
        case 3:
            if let v = custom[k.id] {
                let c = rgb(v)
                return (c, darker(c), false)
            }
        default:
            break
        }
        let base = special ? specialColor : keyColor
        var pressed = special ? pressedSpecialColor : specialColor
        if glassStyle && !special { pressed = pressedSpecialColor }
        return (base, pressed, true)
    }

    private func style(_ k: KeyView) {
        let id = k.id
        let special = !isCharacter(id) && id != "SPACE"
        let (base, pressed, apple) = colors(for: k, special: special)
        let fg = apple ? textColor : textOn(base)
        let weight = KeyPrefs.weights[weightIdx]
        let specialWeight: UIFont.Weight = weightIdx >= 2 ? .medium : weight
        k.cornerShape = shapeIdx
        k.layer.shadowColor = shadowCG
        k.layer.shadowOpacity = shadowOpacity
        k.label.textColor = fg
        k.label.alpha = cursorTouch == nil ? 1 : 0
        k.icon.alpha = cursorTouch == nil ? 1 : 0
        k.icon.tintColor = fg
        k.label.text = nil
        k.subLabel.text = nil
        k.icon.image = nil
        k.baseColor = base
        k.pressedColor = pressed
        switch id {
        case "SHIFT":
            k.icon.image = symbol(shift == 2 ? "capslock.fill" : (shift == 1 ? "shift.fill" : "shift"))
            if shift > 0 && !glassStyle && apple { k.baseColor = keyColor }
        case "DEL":
            k.icon.image = symbol("delete.left", size: 21)
        case "LANG":
            if langCount > 1 {
                let i = KeyPrefs.index(lang)
                k.label.text = KeyPrefs.flags[i] + " " + KeyPrefs.shorts[i]
                k.label.font = .systemFont(ofSize: 15, weight: specialWeight)
            } else {
                k.icon.image = symbol("globe")
            }
        case "EMOJI":
            k.icon.image = symbol("face.smiling", size: 21)
        case "TOGGLE":
            k.label.text = "öğ"
            k.label.font = .systemFont(ofSize: 19, weight: .semibold)
            k.label.textColor = replacementsOn ? (apple ? UIColor.systemGreen : fg) : fg.withAlphaComponent(0.35)
        case "SPACE":
            k.label.font = .systemFont(ofSize: 15, weight: specialWeight)
            k.label.textColor = fg.withAlphaComponent(0.6)
            if let n = toggleNotice {
                k.label.text = n
            } else if let r = lastReplacement, let o = lastOriginal {
                let from = o.trimmingCharacters(in: .whitespacesAndNewlines)
                let to = r.trimmingCharacters(in: .whitespacesAndNewlines)
                k.label.text = "\(from) → \(to)"
            } else {
                k.label.text = KeyPrefs.names[KeyPrefs.index(lang)]
            }
        case "RET":
            styleReturn(k, apple: apple, fg: fg)
        case "SYM1", "SYM2", "ABC":
            k.label.text = id == "SYM1" ? "123" : (id == "SYM2" ? "#+=" : (isCyrillic ? "АБВ" : "ABC"))
            k.label.font = .systemFont(ofSize: 17, weight: specialWeight)
        default:
            let upper = mode == .letters && shift > 0
            k.label.text = upper ? id.uppercased() : id
            k.label.font = .systemFont(ofSize: mode == .letters ? 26 : 24, weight: weight)
        }
    }

    private var returnDisabled: Bool {
        let p = textDocumentProxy
        guard p.enablesReturnKeyAutomatically == true else { return false }
        return (p.documentContextBeforeInput ?? "").isEmpty && (p.documentContextAfterInput ?? "").isEmpty
    }

    private func styleReturn(_ k: KeyView, apple: Bool, fg: UIColor) {
        var title: String?
        var iconName: String?
        var blue = true
        let cyr = isCyrillic
        switch textDocumentProxy.returnKeyType ?? .default {
        case .go: title = cyr ? "Перейти" : "go"
        case .google, .search, .yahoo: iconName = "magnifyingglass"
        case .join: title = cyr ? "Войти" : "join"
        case .next: title = cyr ? "Далее" : "next"; blue = false
        case .route: title = cyr ? "Маршрут" : "route"
        case .send: iconName = "arrow.up"
        case .done: title = cyr ? "Готово" : "done"
        case .emergencyCall: title = "SOS"
        case .continue: title = cyr ? "Далее" : "continue"; blue = false
        default: iconName = "return.left"; blue = false
        }
        if let t = title {
            k.label.text = t
            k.label.font = .systemFont(ofSize: 17)
        }
        if let n = iconName {
            k.icon.image = symbol(n, size: 21)
        }
        if returnDisabled {
            k.label.textColor = fg.withAlphaComponent(0.3)
            k.icon.tintColor = fg.withAlphaComponent(0.3)
        } else if blue && apple {
            k.baseColor = .systemBlue
            k.pressedColor = UIColor.systemBlue.withAlphaComponent(0.7)
            k.label.textColor = .white
            k.icon.tintColor = .white
        }
    }

    // MARK: Popups

    private func showPopup(over k: KeyView, text: String, highlighted: Bool) {
        let f = keysView.convert(k.frame, to: view)
        let w = f.width + 20
        let h = f.height + 14
        let x = min(max(f.midX - w / 2, 2), view.bounds.width - w - 2)
        let y = max(f.minY - h + 6, 0)
        popup.frame = CGRect(x: x, y: y, width: w, height: h)
        popup.backgroundColor = highlighted ? .systemBlue : (isDark ? UIColor(white: 0.5, alpha: 1) : .white)
        popup.layer.shadowPath = UIBezierPath(roundedRect: popup.bounds, cornerRadius: 10).cgPath
        popupLabel.frame = popup.bounds.insetBy(dx: 3, dy: 0)
        popupLabel.textColor = highlighted ? .white : textColor
        popupLabel.text = text
        popup.isHidden = false
        view.bringSubviewToFront(popup)
        popupKey = k
    }

    private func hidePopup() {
        popup.isHidden = true
        popupKey = nil
    }

    private func press(_ k: KeyView) {
        if isCharacter(k.id) {
            showPopup(over: k, text: k.label.text ?? k.id, highlighted: false)
        } else {
            k.isPressed = true
        }
    }

    private func unpress(_ k: KeyView) {
        k.isPressed = false
        if popupKey === k { hidePopup() }
    }

    private func clickFeedback() {
        guard fullAccess else { return }
        haptic.impactOccurred()
        let v = volume
        guard v > 0 else { return }
        // The click sound is played off the main thread so it never delays the key.
        soundQueue.async { [weak self] in
            guard let self = self else { return }
            if self.clickSound == nil { self.clickSound = ClickSound() }
            self.clickSound?.play(volume: Float(v) / 100)
        }
    }

    // MARK: Touches

    private func scheduleHold(_ t: UITouch, _ action: @escaping () -> Void) {
        holdTimer?.invalidate()
        holdTouch = t
        holdTimer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: false) { [weak self] _ in
            self?.holdTimer = nil
            self?.holdTouch = nil
            action()
        }
    }

    private func cancelHold() {
        holdTimer?.invalidate()
        holdTimer = nil
        holdTouch = nil
    }

    private func touchBegan(_ t: UITouch, _ e: UIEvent?) {
        if painting {
            if let k = key(at: t.location(in: keysView)) {
                var c = prefs.custom
                c[k.id] = prefs.brush
                prefs.custom = c
                custom = c
                clickFeedback()
                style(k)
            }
            return
        }
        if cursorTouch != nil || altTouch != nil { return }
        // Fast typing: a new finger types what the other fingers are still holding, like the Apple keyboard.
        for (other, ok) in touchKeys where other !== t && armed.contains(other) {
            armed.remove(other)
            commit(ok.id)
            unpress(ok)
        }
        if mode == .emoji { return }
        cancelHold()
        guard let k = key(at: t.location(in: keysView)) else { return }
        touchKeys[t] = k
        defer { clickFeedback() }      // show the key first, then sound/vibration
        switch k.id {
        case "DEL":
            k.isPressed = true
            deleteBegan()
        case "SHIFT":
            shiftTapped()
        case "SYM1", "SYM2", "ABC":
            switchMode(k.id)
            modeTouch = t
            if let nk = key(at: t.location(in: keysView)) {
                touchKeys[t] = nk
                nk.isPressed = true
            }
        case "LANG":
            k.isPressed = true
            if langCount > 1 {
                armed.insert(t)
                if needsInputModeSwitchKey {
                    scheduleHold(t) { [weak self] in
                        self?.armed.remove(t)
                        self?.advanceToNextInputMode()
                    }
                }
            } else if let e = e {
                handleInputModeList(from: keysView, with: e)
            }
        case "SPACE":
            k.isPressed = true
            armed.insert(t)
            scheduleHold(t) { [weak self] in self?.beginCursorMode(t) }
        case "RET", "TOGGLE", "EMOJI":
            k.isPressed = true
            armed.insert(t)
        default:
            press(k)
            armed.insert(t)
            if alternates[k.id] != nil {
                scheduleHold(t) { [weak self] in self?.showAlternate(t) }
            }
        }
    }

    private func touchMoved(_ t: UITouch, _ e: UIEvent?) {
        if painting { return }
        let p = t.location(in: keysView)
        if t === cursorTouch {
            moveCursor(to: p.x)
            return
        }
        guard t !== altTouch, let old = touchKeys[t] else { return }
        if old.id == "DEL" { return }
        if old.id == "LANG" {
            if langCount <= 1, let e = e { handleInputModeList(from: keysView, with: e) }
            return
        }
        guard let k = key(at: p), k !== old else { return }
        if holdTouch === t { cancelHold() }
        unpress(old)
        touchKeys[t] = k
        switch k.id {
        case "SHIFT", "SYM1", "SYM2", "ABC", "LANG", "DEL":
            armed.remove(t)
        case "SPACE", "RET", "TOGGLE", "EMOJI":
            k.isPressed = true
            armed.insert(t)
        default:
            press(k)
            armed.insert(t)
            if alternates[k.id] != nil {
                scheduleHold(t) { [weak self] in self?.showAlternate(t) }
            }
        }
    }

    private func touchEnded(_ t: UITouch, _ e: UIEvent?, cancelled: Bool) {
        if painting { return }
        if holdTouch === t { cancelHold() }
        defer {
            touchKeys[t] = nil
            armed.remove(t)
            if modeTouch === t { modeTouch = nil }
        }
        if t === cursorTouch {
            if let k = touchKeys[t] { unpress(k) }
            endCursorMode()
            return
        }
        if t === altTouch {
            altTouch = nil
            hidePopup()
            if !cancelled, let s = altText {
                toggleNotice = nil
                typeCommitted(s)
            }
            altText = nil
            if let k = touchKeys[t] { unpress(k) }
            return
        }
        guard let k = touchKeys[t] else { return }
        if k.id == "DEL" { deleteEnded() }
        if k.id == "LANG" && langCount <= 1, let e = e { handleInputModeList(from: keysView, with: e) }
        if !cancelled && armed.contains(t) {
            commit(k.id)
            if t === modeTouch && isCharacter(k.id) && (mode == .symbols || mode == .symbols2) {
                mode = .letters
                buildKeys()
                updateAutoShift()
                updateSuggestions()
            }
        }
        unpress(k)
    }

    private func showAlternate(_ t: UITouch) {
        guard let k = touchKeys[t], let alt = alternates[k.id] else { return }
        altTouch = t
        armed.remove(t)
        let s = (mode == .letters && shift > 0) ? alt.uppercased() : alt
        altText = s
        showPopup(over: k, text: s, highlighted: true)
    }

    private func beginCursorMode(_ t: UITouch) {
        guard touchKeys[t]?.id == "SPACE" else { return }
        cursorTouch = t
        armed.remove(t)
        cursorX = t.location(in: keysView).x
        UIView.animate(withDuration: 0.15) {
            for k in self.rows.joined() {
                k.label.alpha = 0
                k.subLabel.alpha = 0
                k.icon.alpha = 0
            }
        }
    }

    private func moveCursor(to x: CGFloat) {
        let step: CGFloat = 9
        let n = Int((x - cursorX) / step)
        if n != 0 {
            textDocumentProxy.adjustTextPosition(byCharacterOffset: n)
            cursorX += CGFloat(n) * step
        }
    }

    private func endCursorMode() {
        cursorTouch = nil
        UIView.animate(withDuration: 0.15) {
            for k in self.rows.joined() {
                k.label.alpha = 1
                k.subLabel.alpha = 1
                k.icon.alpha = 1
            }
        }
        lastOriginal = nil
        lastReplacement = nil
        afterEdit()
    }

    // MARK: Key actions

    private func commit(_ id: String) {
        if id != "TOGGLE" { toggleNotice = nil }
        switch id {
        case "SPACE":
            spaceTapped()
        case "RET":
            if returnDisabled { return }
            returnKey()
        case "TOGGLE":
            replacementsOn.toggle()
            prefs.replace = replacementsOn
            lastOriginal = nil
            lastReplacement = nil
            toggleNotice = replacementsOn ? "öğ ✓" : "öğ — off"
        case "LANG":
            if langCount > 1 { switchLang() }
            return
        case "EMOJI":
            openEmoji()
            return
        case "SHIFT", "DEL", "SYM1", "SYM2", "ABC":
            return
        default:
            typeCommitted((mode == .letters && shift > 0) ? id.uppercased() : id)
            // Like the Apple keyboard: an apostrophe typed from the 123 layer brings the letters back (o' / g').
            if mode == .symbols || mode == .symbols2, let ch = id.first, isApostrophe(ch) {
                mode = .letters
                buildKeys()
                updateAutoShift()
                updateSuggestions()
            }
            return
        }
        afterEdit()
    }

    private func switchLang() {
        let on = prefs.enabledLangs
        let i = on.firstIndex(of: lang) ?? 0
        lang = on[(i + 1) % on.count]
        prefs.lang = lang
        lastOriginal = nil
        lastReplacement = nil
        mode = .letters
        buildKeys()
        updateAutoShift()
        updateSuggestions()
    }

    private func typeCommitted(_ s: String) {
        type(s)
        if mode == .letters && shift == 1 { setShift(0) }
        afterEdit()
    }

    private func afterEdit() {
        updateAutoShift()
        restyleDynamicKeys()
        updateSuggestions()
    }

    private func shiftTapped() {
        let now = Date()
        let v: Int
        if now.timeIntervalSince(lastShiftTap) < 0.35 && shift != 2 {
            v = 2
        } else if shift == 2 {
            v = 0
        } else {
            v = shift == 0 ? 1 : 0
        }
        lastShiftTap = now
        setShift(v)
    }

    private func setShift(_ v: Int) {
        guard v != shift else { return }
        shift = v
        restyleKeys()
    }

    private func switchMode(_ id: String) {
        switch id {
        case "SYM1": mode = .symbols
        case "SYM2": mode = .symbols2
        default: mode = .letters
        }
        buildKeys()
        if mode == .letters { updateAutoShift() }
        updateSuggestions()
    }

    private func spaceTapped() {
        let now = Date()
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        if now.timeIntervalSince(lastSpaceTap) < 0.45, before.hasSuffix(" "),
           let prev = before.dropLast().last, prev.isLetter || prev.isNumber {
            textDocumentProxy.deleteBackward()
            textDocumentProxy.insertText(". ")
            lastSpaceTap = .distantPast
            lastOriginal = nil
            lastReplacement = nil
        } else {
            type(" ")
            lastSpaceTap = now
        }
        if mode == .symbols || mode == .symbols2 {
            mode = .letters
            buildKeys()
        }
    }

    private func deleteBegan() {
        toggleNotice = nil
        deleteOnce()
        afterEdit()
        deleteCount = 0
        deleteTimer?.invalidate()
        deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: false) { [weak self] _ in
            self?.startDeleteRepeat(0.1)
        }
    }

    private func startDeleteRepeat(_ interval: TimeInterval) {
        deleteTimer?.invalidate()
        deleteTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.deleteOnce()
            self.afterEdit()
            self.deleteCount += 1
            if self.deleteCount == 15 && interval > 0.05 {
                self.startDeleteRepeat(0.05)
            }
        }
    }

    private func deleteEnded() {
        deleteTimer?.invalidate()
        deleteTimer = nil
    }

    private func updateAutoShift() {
        guard shift != 2, mode == .letters else { return }
        let proxy = textDocumentProxy
        let kind = proxy.autocapitalizationType ?? .sentences
        var caps = false
        if kind != UITextAutocapitalizationType.none {
            let before = proxy.documentContextBeforeInput ?? ""
            if kind == .allCharacters {
                caps = true
            } else if kind == .words {
                caps = before.isEmpty || (before.last?.isWhitespace ?? true)
            } else {
                let trimmed = before.trimmingCharacters(in: .whitespaces)
                caps = trimmed.isEmpty || before.hasSuffix("\n")
                    || (before.hasSuffix(" ") && (trimmed.hasSuffix(".") || trimmed.hasSuffix("!") || trimmed.hasSuffix("?")))
            }
        }
        setShift(caps ? 1 : 0)
    }

    // MARK: Text

    private func isApostrophe(_ c: Character) -> Bool {
        return c == "'" || c == "‘" || c == "’" || c == "ʻ" || c == "ʼ"
    }

    private func isEndChar(_ c: Character) -> Bool {
        return " \n-()[]{}':;\"/\\,.?!‘’ʻ".contains(c)
    }

    private func replacement(after p: Character, typed c: Character) -> String? {
        if isApostrophe(c) {
            switch p {
            case "o": return "ö"
            case "O": return "Ö"
            case "g": return "ğ"
            case "G": return "Ğ"
            default: return nil
            }
        }
        if c == "h" {
            switch p {
            case "s": return "ş"
            case "S": return "Ş"
            case "c": return "ç"
            case "C": return "Ç"
            default: return nil
            }
        }
        if c == "H" {
            switch p {
            case "S": return "Ş"
            case "C": return "Ç"
            default: return nil
            }
        }
        return nil
    }

    private func hasSelection() -> Bool {
        if #available(iOS 16.0, *) {
            if let sel = textDocumentProxy.selectedText, !sel.isEmpty { return true }
        }
        return false
    }

    /// The shortcut right before the cursor (aa, Aa or AA as a whole word) and its phrase.
    private func phraseFor(_ before: String) -> (String, String)? {
        guard phrasesOn else { return nil }
        let table: [(String, String)]
        if lang == "UZ" {
            table = cyrPhrases
        } else if hasToggle {
            table = latPhrases
        } else {
            return nil
        }
        let chars = Array(before.suffix(3))
        let n = chars.count
        guard n >= 2 else { return nil }
        if n == 3 && (chars[0].isLetter || chars[0].isNumber) { return nil }
        let a = chars[n - 2]
        let b = chars[n - 1]
        let lower = a.isLowercase && b.isLowercase
        let cap = a.isUppercase
        guard lower || cap else { return nil }
        let typed = String([a, b])
        let w = typed.lowercased()
        for (abbr, phrase) in table where abbr == w {
            return (typed, phrase)
        }
        return nil
    }

    /// Expands a shortcut before the cursor. end = what was typed after the word (may be empty).
    private func tryPhrase(before: String, end: String) -> Bool {
        guard let (typed, phrase) = phraseFor(before) else { return false }
        for _ in 0..<typed.count { textDocumentProxy.deleteBackward() }
        textDocumentProxy.insertText(phrase + end)
        lastOriginal = typed + end
        lastReplacement = phrase + end
        return true
    }

    private func type(_ s: String) {
        let proxy = textDocumentProxy
        lastOriginal = nil
        lastReplacement = nil
        if s.count == 1, let c = s.first, !hasSelection() {
            let before = proxy.documentContextBeforeInput ?? ""
            if replacementsOn, let p = before.last {
                if lang == "YA", let rep = replacement(after: p, typed: c) {
                    proxy.deleteBackward()
                    proxy.insertText(rep)
                    lastOriginal = String(p) + s
                    lastReplacement = rep
                    return
                }
                if lang == "OZ", isApostrophe(c), p.isLetter {
                    let a = "oOgG".contains(p) ? "ʻ" : "ʼ"
                    proxy.insertText(a)
                    lastOriginal = s
                    lastReplacement = a
                    return
                }
            }
            if isEndChar(c) {
                if tryPhrase(before: before, end: s) { return }
                learnBefore(before)
            }
        }
        proxy.insertText(s)
    }

    private func returnKey() {
        lastOriginal = nil
        lastReplacement = nil
        if !hasSelection() {
            let b = textDocumentProxy.documentContextBeforeInput ?? ""
            if !tryPhrase(before: b, end: "") { learnBefore(b) }
        }
        textDocumentProxy.insertText("\n")
    }

    @discardableResult
    private func undoReplacement() -> Bool {
        guard let rep = lastReplacement, let orig = lastOriginal else { return false }
        lastOriginal = nil
        lastReplacement = nil
        guard (textDocumentProxy.documentContextBeforeInput ?? "").hasSuffix(rep) else { return false }
        for _ in 0..<rep.count { textDocumentProxy.deleteBackward() }
        textDocumentProxy.insertText(orig)
        return true
    }

    private func deleteOnce() {
        if undoReplacement() { return }
        lastOriginal = nil
        lastReplacement = nil
        textDocumentProxy.deleteBackward()
    }
}
