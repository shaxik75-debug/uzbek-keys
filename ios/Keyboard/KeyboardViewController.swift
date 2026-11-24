import UIKit
import AudioToolbox

/// Uzbek Keys keyboard for iPhone, laid out and behaving like the Apple keyboard (iOS 26 look).
/// While typing: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (capitals too),
/// aa -> "Assalomu Alaykum", vv -> "Va Alaykum Assalom" (whole word, after space/punctuation/return).
/// Backspace right after a replacement brings back what was typed; the space bar shows the last replacement.
/// The "öğ" key (where Apple has the emoji key) turns replacements on/off.
/// Apple-like behaviour: key popups, fast typing with several fingers, long-press o/g/s/c for ö/ğ/ş/ç,
/// double space = ". ", hold space and slide to move the cursor, hold 123 and slide to a symbol.

// MARK: - Views

/// One key. Touches are handled by TouchView.
final class KeyView: UIView {
    let id: String
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
        label.frame = bounds.insetBy(dx: 4, dy: 0)
        subLabel.frame = CGRect(x: 0, y: bounds.height - 17, width: bounds.width - 9, height: 14)
        icon.frame = bounds
        layer.shadowPath = UIBezierPath(roundedRect: bounds, cornerRadius: layer.cornerRadius).cgPath
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

    private enum Mode { case letters, symbols, symbols2 }

    // Special key ids are words; every other id is the character the key types.
    private let lettersRows: [[String]] = [
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l", "'"],
        ["SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"],
        ["SYM1", "NEXT", "TOGGLE", "SPACE", "RET"]
    ]
    private let symbolsRows: [[String]] = [
        ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
        ["-", "/", ":", ";", "(", ")", "$", "&", "@", "\""],
        ["SYM2", ".", ",", "?", "!", "'", "DEL"],
        ["ABC", "NEXT", "TOGGLE", "SPACE", "RET"]
    ]
    private let symbols2Rows: [[String]] = [
        ["[", "]", "{", "}", "#", "%", "^", "*", "+", "="],
        ["_", "\\", "|", "~", "<", ">", "€", "£", "₽", "•"],
        ["SYM1", ".", ",", "?", "!", "'", "DEL"],
        ["ABC", "NEXT", "TOGGLE", "SPACE", "RET"]
    ]
    private let alternates: [String: String] = ["o": "ö", "g": "ğ", "s": "ş", "c": "ç"]

    // MARK: State

    private var mode: Mode = .letters
    private var shift = 0                       // 0 off, 1 next letter, 2 caps lock
    private var lastShiftTap = Date.distantPast
    private var lastSpaceTap = Date.distantPast
    private var lastOriginal: String?
    private var lastReplacement: String?
    private var toggleNotice: String?

    // Read once, kept in memory; written back only when it changes.
    private var replacementsOn = true {
        didSet { UserDefaults.standard.set(replacementsOn, forKey: "enabled") }
    }

    // MARK: Views

    private let keysView = TouchView()
    private let popup = UIView()
    private let popupLabel = UILabel()
    private weak var popupKey: KeyView?
    private var rows: [[KeyView]] = []
    private var heightConstraint: NSLayoutConstraint?
    private let haptic = UIImpactFeedbackGenerator(style: .light)

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

    // Colours are worked out once per appearance change, not on every key press.
    private var isDark = false
    private var appearanceReady = false
    private var keyColor: UIColor = .white
    private var specialColor: UIColor = .white
    private var pressedSpecialColor: UIColor = .lightGray
    private var textColor: UIColor = .black
    private var hintColor: UIColor = .gray
    private var subColor: UIColor = .gray
    private var offColor: UIColor = .gray
    private var disabledColor: UIColor = .gray
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

    /// Returns true when the colours changed (the keys then need restyling).
    @discardableResult
    private func refreshAppearance() -> Bool {
        let d = textDocumentProxy.keyboardAppearance == .dark || traitCollection.userInterfaceStyle == .dark
        if appearanceReady && d == isDark { return false }
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
        hintColor = textColor.withAlphaComponent(0.6)
        subColor = textColor.withAlphaComponent(0.45)
        offColor = textColor.withAlphaComponent(0.35)
        disabledColor = textColor.withAlphaComponent(0.3)
        shadowCG = UIColor(white: 0, alpha: d ? 0.45 : 0.3).cgColor
        shadowOpacity = glassStyle ? 0 : 1
        return true
    }

    // MARK: Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        keysView.isMultipleTouchEnabled = true
        keysView.backgroundColor = .clear
        keysView.onBegan = { [weak self] t, e in self?.touchBegan(t, e) }
        keysView.onMoved = { [weak self] t, e in self?.touchMoved(t, e) }
        keysView.onEnded = { [weak self] t, e, c in self?.touchEnded(t, e, cancelled: c) }
        view.addSubview(keysView)

        popup.isUserInteractionEnabled = false
        popup.isHidden = true
        popup.layer.cornerRadius = 10
        popup.layer.shadowColor = UIColor.black.cgColor
        popup.layer.shadowOffset = CGSize(width: 0, height: 1)
        popup.layer.shadowRadius = 4
        popup.layer.shadowOpacity = 0.3
        popupLabel.textAlignment = .center
        popupLabel.font = .systemFont(ofSize: 34, weight: .light)
        popup.addSubview(popupLabel)
        view.addSubview(popup)

        let h = view.heightAnchor.constraint(equalToConstant: keyboardHeight)
        h.priority = UILayoutPriority(999)
        h.isActive = true
        heightConstraint = h

        replacementsOn = UserDefaults.standard.object(forKey: "enabled") as? Bool ?? true
        refreshAppearance()
        buildKeys()
    }

    private var keyboardHeight: CGFloat { isLandscape ? 162 : 216 }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        lastOriginal = nil
        lastReplacement = nil
        toggleNotice = nil
        if shift == 2 { shift = 0 }
        fullAccess = hasFullAccess
        if fullAccess { haptic.prepare() }
        // Rebuild the keys only when the layout really changed; otherwise just refresh their look.
        let hasNext = rows.last?.contains(where: { $0.id == "NEXT" }) ?? false
        if mode != .letters || rows.isEmpty || hasNext != needsInputModeSwitchKey {
            mode = .letters
            buildKeys()
        } else {
            refreshAppearance()
            restyleKeys()
        }
        updateAutoShift()
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
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if refreshAppearance() { restyleKeys() }
    }

    // MARK: Building and layout

    private func currentRows() -> [[String]] {
        var rs: [[String]]
        switch mode {
        case .letters: rs = lettersRows
        case .symbols: rs = symbolsRows
        case .symbols2: rs = symbols2Rows
        }
        if !needsInputModeSwitchKey {
            rs[3] = rs[3].filter { $0 != "NEXT" }
        }
        return rs
    }

    private func buildKeys() {
        hidePopup()
        for k in rows.joined() { k.removeFromSuperview() }
        rows = currentRows().map { ids in
            ids.map { id -> KeyView in
                let k = KeyView(id: id)
                keysView.addSubview(k)
                return k
            }
        }
        restyleKeys()
        layoutAll()
    }

    private func layoutAll() {
        let W = view.bounds.width
        let H = view.bounds.height
        guard W > 0, H > 0 else { return }
        keysView.frame = view.bounds

        let land = isLandscape
        let side: CGFloat = land ? 5 : 6
        let gapX: CGFloat = 6
        let topPad: CGFloat = land ? 6 : 10
        let bottomPad: CGFloat = land ? 4 : 6
        let rowGap: CGFloat = land ? 7 : 11
        let keyH = max(20, (H - topPad - bottomPad - 3 * rowGap) / 4)
        let unit = (W - 2 * side - 9 * gapX) / 10
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
                var x = (W - (n * unit + (n - 1) * gapX)) / 2
                for _ in row { add(x, unit); x += unit + gapX }
            } else if r == 2 {
                let wide = unit * 1.3 + 2
                let n = row.count - 2
                add(side, wide)
                if mode == .letters {
                    var x = (W - (CGFloat(n) * unit + CGFloat(n - 1) * gapX)) / 2
                    for _ in 0..<n { add(x, unit); x += unit + gapX }
                } else {
                    let inner = W - 2 * side - 2 * wide - 4 * gapX
                    let w = (inner - CGFloat(n - 1) * gapX) / CGFloat(n)
                    var x = side + wide + 2 * gapX
                    for _ in 0..<n { add(x, w); x += w + gapX }
                }
                add(W - side - wide, wide)
            } else {
                // Apple bottom row: 123 | emoji (here: öğ) | space | return
                let fixed: [CGFloat] = row.map { k in
                    switch k.id {
                    case "SPACE": return 0
                    case "RET": return unit * 2.7 + gapX
                    default: return unit * 1.3 + 2
                    }
                }
                let used = fixed.reduce(0, +) + CGFloat(row.count - 1) * gapX
                let spaceW = max(unit, W - 2 * side - used)
                var x = side
                for (i, k) in row.enumerated() {
                    let w = k.id == "SPACE" ? spaceW : fixed[i]
                    add(x, w)
                    x += w + gapX
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

    private func style(_ k: KeyView) {
        let id = k.id
        let special = !isCharacter(id) && id != "SPACE"
        k.layer.shadowColor = shadowCG
        k.layer.shadowOpacity = shadowOpacity
        k.label.textColor = textColor
        k.label.alpha = cursorTouch == nil ? 1 : 0
        k.icon.alpha = cursorTouch == nil ? 1 : 0
        k.icon.tintColor = textColor
        k.label.text = nil
        k.subLabel.text = nil
        k.icon.image = nil
        k.baseColor = special ? specialColor : keyColor
        k.pressedColor = special ? pressedSpecialColor : specialColor
        if glassStyle && !special { k.pressedColor = pressedSpecialColor }
        switch id {
        case "SHIFT":
            k.icon.image = symbol(shift == 2 ? "capslock.fill" : (shift == 1 ? "shift.fill" : "shift"))
            if shift > 0 && !glassStyle { k.baseColor = keyColor }
        case "DEL":
            k.icon.image = symbol("delete.left", size: 21)
        case "NEXT":
            k.icon.image = symbol("globe")
        case "TOGGLE":
            k.label.text = "öğ"
            k.label.font = .systemFont(ofSize: 19, weight: .semibold)
            k.label.textColor = replacementsOn ? UIColor.systemGreen : offColor
        case "SPACE":
            k.label.font = .systemFont(ofSize: 15)
            k.label.textColor = hintColor
            if let n = toggleNotice {
                k.label.text = n
            } else if let r = lastReplacement, let o = lastOriginal {
                let from = o.trimmingCharacters(in: .whitespacesAndNewlines)
                let to = r.trimmingCharacters(in: .whitespacesAndNewlines)
                k.label.text = "\(from) → \(to)"
            }
            k.subLabel.text = "uz"
            k.subLabel.textColor = subColor
        case "RET":
            styleReturn(k)
        case "SYM1", "SYM2", "ABC":
            k.label.text = id == "SYM1" ? "123" : (id == "SYM2" ? "#+=" : "ABC")
            k.label.font = .systemFont(ofSize: 17)
        default:
            let upper = mode == .letters && shift > 0
            k.label.text = upper ? id.uppercased() : id
            k.label.font = .systemFont(ofSize: mode == .letters ? 26 : 24, weight: .regular)
        }
    }

    private var returnDisabled: Bool {
        let p = textDocumentProxy
        guard p.enablesReturnKeyAutomatically == true else { return false }
        return (p.documentContextBeforeInput ?? "").isEmpty && (p.documentContextAfterInput ?? "").isEmpty
    }

    private func styleReturn(_ k: KeyView) {
        var title: String?
        var iconName: String?
        var blue = true
        switch textDocumentProxy.returnKeyType ?? .default {
        case .go: title = "go"
        case .google, .search, .yahoo: iconName = "magnifyingglass"
        case .join: title = "join"
        case .next: title = "next"; blue = false
        case .route: title = "route"
        case .send: iconName = "arrow.up"
        case .done: title = "done"
        case .emergencyCall: title = "SOS"
        case .continue: title = "continue"; blue = false
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
            k.label.textColor = disabledColor
            k.icon.tintColor = disabledColor
        } else if blue {
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
        popupLabel.frame = popup.bounds
        popupLabel.textColor = highlighted ? .white : textColor
        popupLabel.text = text
        popup.isHidden = false
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
        // The click sound is played off the main thread so it never delays the key.
        soundQueue.async { AudioServicesPlaySystemSound(1104) }
        haptic.impactOccurred()
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
        if cursorTouch != nil || altTouch != nil { return }
        // Fast typing: a new finger types what the other fingers are still holding, like the Apple keyboard.
        for (other, ok) in touchKeys where other !== t && armed.contains(other) {
            armed.remove(other)
            commit(ok.id)
            unpress(ok)
        }
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
        case "NEXT":
            k.isPressed = true
            if let e = e { handleInputModeList(from: keysView, with: e) }
        case "SPACE":
            k.isPressed = true
            armed.insert(t)
            scheduleHold(t) { [weak self] in self?.beginCursorMode(t) }
        case "RET", "TOGGLE":
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
        let p = t.location(in: keysView)
        if t === cursorTouch {
            moveCursor(to: p.x)
            return
        }
        guard t !== altTouch, let old = touchKeys[t] else { return }
        if old.id == "DEL" { return }
        if old.id == "NEXT" {
            if let e = e { handleInputModeList(from: keysView, with: e) }
            return
        }
        guard let k = key(at: p), k !== old else { return }
        if holdTouch === t { cancelHold() }
        unpress(old)
        touchKeys[t] = k
        switch k.id {
        case "SHIFT", "SYM1", "SYM2", "ABC", "NEXT", "DEL":
            armed.remove(t)
        case "SPACE", "RET", "TOGGLE":
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
        if k.id == "NEXT", let e = e { handleInputModeList(from: keysView, with: e) }
        if !cancelled && armed.contains(t) {
            commit(k.id)
            if t === modeTouch && isCharacter(k.id) && mode != .letters {
                mode = .letters
                buildKeys()
                updateAutoShift()
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
            lastOriginal = nil
            lastReplacement = nil
            toggleNotice = replacementsOn ? "öğ ✓" : "öğ — off"
        case "SHIFT", "DEL", "NEXT", "SYM1", "SYM2", "ABC":
            return
        default:
            typeCommitted((mode == .letters && shift > 0) ? id.uppercased() : id)
            // Like the Apple keyboard: an apostrophe typed from the 123 layer brings the letters back (o' / g').
            if mode != .letters, let ch = id.first, isApostrophe(ch) {
                mode = .letters
                buildKeys()
                updateAutoShift()
            }
            return
        }
        afterEdit()
    }

    private func typeCommitted(_ s: String) {
        type(s)
        if mode == .letters && shift == 1 { setShift(0) }
        afterEdit()
    }

    private func afterEdit() {
        updateAutoShift()
        restyleDynamicKeys()
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
        if mode != .letters {
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

    /// Expands aa / vv / rr before the cursor. end = what was typed after the word (may be empty).
    private func tryPhrase(before: String, end: String) -> Bool {
        let phrases = [("aa", "Assalomu Alaykum"), ("vv", "Va Alaykum Assalom"), ("rr", "Rahmat")]
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
