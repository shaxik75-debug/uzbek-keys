import UIKit

/// Uzbek Keys keyboard for iPhone.
/// While typing: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (capitals too),
/// aa -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom" (whole word, after space/punctuation/return).
/// Backspace right after a replacement brings back what was typed. "öğ" key switches replacements on/off.
final class KeyboardViewController: UIInputViewController {

    private enum Mode { case letters, symbols, symbols2 }

    private let letters: [[String]] = [
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l", "'"],
        ["SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"],
        ["123", "NEXT", "TOGGLE", "SPACE", ".", "RETURN"]
    ]
    private let symbols: [[String]] = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["-", "/", ":", ";", "(", ")", "\"", "@", "&", "?"],
        ["MORE", "_", ",", "!", "'", "%", "+", "*", "DEL"],
        ["ABC", "NEXT", "TOGGLE", "SPACE", ".", "RETURN"]
    ]
    private let symbols2: [[String]] = [
        ["[", "]", "{", "}", "#", "^", "=", "<", ">", "~"],
        ["\\", "|", "$", "€", "£", "₽", "•", "№", "°", "…"],
        ["123", "«", "»", "‘", "’", "ʻ", "×", "÷", "DEL"],
        ["ABC", "NEXT", "TOGGLE", "SPACE", ".", "RETURN"]
    ]

    private var mode: Mode = .letters
    private var shift = 0              // 0 off, 1 next letter, 2 caps lock
    private var lastShiftTap = Date.distantPast
    private var lastOriginal: String?
    private var lastReplacement: String?

    private let stack = UIStackView()
    private var letterButtons: [(UIButton, String)] = []
    private weak var shiftButton: UIButton?
    private weak var toggleButton: UIButton?
    private var deleteTimer: Timer?

    private var replacementsOn: Bool {
        get { UserDefaults.standard.object(forKey: "enabled") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "enabled") }
    }

    private let keyColor = UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.42, alpha: 1) : .white }
    private let specialColor = UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.27, alpha: 1) : UIColor(white: 0.68, alpha: 1) }
    private let onColor = UIColor(red: 0.18, green: 0.49, blue: 0.20, alpha: 1)

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        stack.axis = .vertical
        stack.distribution = .fillEqually
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 2),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -2),
            stack.topAnchor.constraint(equalTo: view.topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -2)
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 232)
        height.priority = UILayoutPriority(999)
        height.isActive = true
        build()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        lastOriginal = nil
        lastReplacement = nil
        updateAutoShift()
        refresh()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        updateAutoShift()
    }

    // MARK: - Layout

    private func weight(_ k: String) -> CGFloat {
        switch k {
        case "SPACE": return 4
        case "SHIFT", "DEL", "RETURN", "123", "ABC", "MORE": return 1.5
        default: return 1
        }
    }

    private func build() {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        letterButtons.removeAll()
        let rows: [[String]]
        switch mode {
        case .letters: rows = letters
        case .symbols: rows = symbols
        case .symbols2: rows = symbols2
        }
        for row in rows {
            let r = UIStackView()
            r.axis = .horizontal
            r.distribution = .fill
            r.spacing = 0
            stack.addArrangedSubview(r)
            let total = row.reduce(CGFloat(0)) { $0 + weight($1) }
            for k in row {
                let cell = makeKey(k)
                r.addArrangedSubview(cell)
                let c = cell.widthAnchor.constraint(equalTo: r.widthAnchor, multiplier: weight(k) / total)
                c.priority = UILayoutPriority(999)
                c.isActive = true
            }
        }
        refresh()
    }

    private func title(for k: String) -> String {
        switch k {
        case "SHIFT": return shift == 2 ? "⇪" : "⇧"
        case "DEL": return "⌫"
        case "RETURN": return "⏎"
        case "SPACE": return "O'zbek"
        case "NEXT": return "🌐"
        case "TOGGLE": return replacementsOn ? "öğ" : "off"
        case "MORE": return "#+="
        default: return k
        }
    }

    private func makeKey(_ k: String) -> UIView {
        let cell = UIView()
        let b = UIButton(type: .custom)
        b.translatesAutoresizingMaskIntoConstraints = false
        cell.addSubview(b)
        NSLayoutConstraint.activate([
            b.leadingAnchor.constraint(equalTo: cell.leadingAnchor, constant: 3),
            b.trailingAnchor.constraint(equalTo: cell.trailingAnchor, constant: -3),
            b.topAnchor.constraint(equalTo: cell.topAnchor, constant: 5),
            b.bottomAnchor.constraint(equalTo: cell.bottomAnchor, constant: -5)
        ])
        let isChar = k.count == 1
        b.layer.cornerRadius = 5
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.25
        b.layer.shadowOffset = CGSize(width: 0, height: 1)
        b.layer.shadowRadius = 0
        b.setTitleColor(.label, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: isChar ? 22 : 17)
        b.backgroundColor = (isChar || k == "SPACE") ? keyColor : specialColor
        b.setTitle(title(for: k), for: .normal)
        b.accessibilityIdentifier = k

        switch k {
        case "NEXT":
            b.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        case "DEL":
            b.addTarget(self, action: #selector(deleteDown), for: .touchDown)
            b.addTarget(self, action: #selector(deleteUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        default:
            b.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
        }
        b.addTarget(self, action: #selector(pressDown(_:)), for: .touchDown)
        b.addTarget(self, action: #selector(pressUp(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel])

        if isChar, let ch = k.first, ch.isLetter { letterButtons.append((b, k)) }
        if k == "SHIFT" { shiftButton = b }
        if k == "TOGGLE" { toggleButton = b }
        return cell
    }

    private func refresh() {
        let upper = shift > 0
        for (b, k) in letterButtons {
            b.setTitle(upper ? k.uppercased() : k, for: .normal)
        }
        if let s = shiftButton {
            s.setTitle(title(for: "SHIFT"), for: .normal)
            s.backgroundColor = shift > 0 ? keyColor : specialColor
        }
        if let t = toggleButton {
            t.setTitle(title(for: "TOGGLE"), for: .normal)
            t.backgroundColor = replacementsOn ? onColor : specialColor
            t.setTitleColor(replacementsOn ? .white : .label, for: .normal)
        }
    }

    // MARK: - Key handling

    @objc private func pressDown(_ sender: UIButton) {
        sender.alpha = 0.5
        UIDevice.current.playInputClick()
    }

    @objc private func pressUp(_ sender: UIButton) {
        sender.alpha = 1
    }

    @objc private func keyTapped(_ sender: UIButton) {
        guard let k = sender.accessibilityIdentifier else { return }
        switch k {
        case "SHIFT":
            let now = Date()
            if shift == 2 {
                shift = 0
            } else if shift == 1 && now.timeIntervalSince(lastShiftTap) < 0.4 {
                shift = 2
            } else {
                shift = shift == 0 ? 1 : 0
            }
            lastShiftTap = now
            refresh()
            return
        case "123":
            mode = .symbols
            build()
            return
        case "MORE":
            mode = .symbols2
            build()
            return
        case "ABC":
            mode = .letters
            build()
            updateAutoShift()
            return
        case "TOGGLE":
            replacementsOn.toggle()
            lastOriginal = nil
            lastReplacement = nil
            refresh()
            return
        case "SPACE":
            type(" ")
            return
        case "RETURN":
            returnKey()
            return
        default:
            break
        }
        var s = k
        if mode == .letters && shift > 0, let ch = k.first, ch.isLetter { s = k.uppercased() }
        type(s)
        if mode == .letters && shift == 1 {
            shift = 0
            refresh()
        }
    }

    @objc private func deleteDown() {
        deleteOnce()
        deleteTimer?.invalidate()
        deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: false) { [weak self] _ in
            self?.deleteTimer = Timer.scheduledTimer(withTimeInterval: 0.07, repeats: true) { [weak self] _ in
                self?.deleteOnce()
            }
        }
    }

    @objc private func deleteUp() {
        deleteTimer?.invalidate()
        deleteTimer = nil
    }

    private func updateAutoShift() {
        guard shift != 2 else { return }
        let proxy = textDocumentProxy
        var caps = false
        if (proxy.autocapitalizationType ?? .sentences) != UITextAutocapitalizationType.none {
            let before = proxy.documentContextBeforeInput ?? ""
            let trimmed = before.trimmingCharacters(in: .whitespaces)
            caps = before.isEmpty || before.hasSuffix("\n")
                || (before.hasSuffix(" ") && (trimmed.hasSuffix(".") || trimmed.hasSuffix("!") || trimmed.hasSuffix("?")))
        }
        let ns = caps ? 1 : 0
        if ns != shift {
            shift = ns
            refresh()
        }
    }

    // MARK: - Text

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

    /// Expands aa / vv before the cursor. end = what was typed after the word (may be empty).
    private func tryPhrase(before: String, end: String) -> Bool {
        let phrases = [("aa", "Assalomu Alaykum"), ("vv", "Va Alaykum Assalom")]
        for (abbr, phrase) in phrases where before.hasSuffix(abbr) {
            let rest = before.dropLast(abbr.count)
            if let q = rest.last, q.isLetter || q.isNumber { continue }
            for _ in 0..<abbr.count { textDocumentProxy.deleteBackward() }
            textDocumentProxy.insertText(phrase + end)
            lastOriginal = abbr + end
            lastReplacement = phrase + end
            return true
        }
        return false
    }

    private func type(_ s: String) {
        let proxy = textDocumentProxy
        lastOriginal = nil
        lastReplacement = nil
        if replacementsOn, s.count == 1, let c = s.first, !hasSelection() {
            let before = proxy.documentContextBeforeInput ?? ""
            if let p = before.last, let rep = replacement(after: p, typed: c) {
                proxy.deleteBackward()
                proxy.insertText(rep)
                lastOriginal = String(p) + s
                lastReplacement = rep
                return
            }
            if isEndChar(c) && tryPhrase(before: before, end: s) { return }
        }
        proxy.insertText(s)
    }

    private func returnKey() {
        lastOriginal = nil
        lastReplacement = nil
        if replacementsOn && !hasSelection() {
            _ = tryPhrase(before: textDocumentProxy.documentContextBeforeInput ?? "", end: "")
        }
        textDocumentProxy.insertText("\n")
    }

    private func deleteOnce() {
        let proxy = textDocumentProxy
        if let rep = lastReplacement, let orig = lastOriginal {
            lastOriginal = nil
            lastReplacement = nil
            if (proxy.documentContextBeforeInput ?? "").hasSuffix(rep) {
                for _ in 0..<rep.count { proxy.deleteBackward() }
                proxy.insertText(orig)
                return
            }
        }
        lastOriginal = nil
        lastReplacement = nil
        proxy.deleteBackward()
    }
}
