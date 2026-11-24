// Draws the 1024x1024 app icon (green, four keys: ö ğ ş ç).
// Usage (macOS): swift ios/tools/make_icon.swift <output.png>
import AppKit
import CoreText

let size = 1024
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "icon-1024.png"

guard let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
                                 bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false, isPlanar: false,
                                 colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0),
      let gc = NSGraphicsContext(bitmapImageRep: rep) else {
    fatalError("cannot create bitmap")
}
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = gc
let ctx = gc.cgContext

let full = NSRect(x: 0, y: 0, width: size, height: size)
let top = NSColor(calibratedRed: 0.20, green: 0.66, blue: 0.33, alpha: 1)
let bottom = NSColor(calibratedRed: 0.10, green: 0.42, blue: 0.20, alpha: 1)
NSGradient(starting: top, ending: bottom)!.draw(in: full, angle: -90)

let letters = ["ö", "ğ", "ş", "ç"]
let keyW: CGFloat = 330
let gap: CGFloat = 60
let origin = (CGFloat(size) - (2 * keyW + gap)) / 2
let font = NSFont.systemFont(ofSize: 230, weight: .semibold)
let ink = NSColor(calibratedRed: 0.10, green: 0.36, blue: 0.18, alpha: 1)

for (i, ch) in letters.enumerated() {
    let col = CGFloat(i % 2)
    let row = CGFloat(1 - i / 2)          // first two letters on the top row
    let r = NSRect(x: origin + col * (keyW + gap), y: origin + row * (keyW + gap), width: keyW, height: keyW)
    NSColor(calibratedRed: 0.08, green: 0.31, blue: 0.16, alpha: 1).setFill()
    NSBezierPath(roundedRect: r.offsetBy(dx: 0, dy: -14), xRadius: 64, yRadius: 64).fill()
    NSColor.white.setFill()
    NSBezierPath(roundedRect: r, xRadius: 64, yRadius: 64).fill()

    let attrs: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: ink,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): ink.cgColor
    ]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: ch, attributes: attrs))
    let glyphs = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
    ctx.textPosition = CGPoint(x: r.midX - glyphs.midX, y: r.midY - glyphs.midY)
    CTLineDraw(line, ctx)
}

NSGraphicsContext.restoreGraphicsState()
guard let data = rep.representation(using: .png, properties: [:]) else { fatalError("cannot encode png") }
try data.write(to: URL(fileURLWithPath: out))
print("icon written to \(out)")
