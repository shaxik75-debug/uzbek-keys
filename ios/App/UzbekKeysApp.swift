import SwiftUI
import UIKit

@main
struct UzbekKeysApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

private let green = Color(red: 0.16, green: 0.57, blue: 0.28)

/// A numbered instruction line.
struct Step: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(.white)
                .frame(width: 24, height: 24)
                .background(Circle().fill(green))
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 2)
    }
}

struct ContentView: View {
    @State private var text = ""

    private var version: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(v) (\(b))"
    }

    /// What this build adds or fixes (whatsnew.txt, bundled by the build).
    private var whatsNew: String {
        guard let url = Bundle.main.url(forResource: "whatsnew", withExtension: "txt"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return "" }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    VStack(spacing: 6) {
                        Text("ö ğ ş ç")
                            .font(.system(size: 40, weight: .semibold))
                            .foregroundColor(green)
                        Text("Özbek harflari uçun klaviatura")
                            .font(.headline)
                        Text("Клавиатура для узбекских букв")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("by işax")
                            .font(.footnote.weight(.semibold))
                            .foregroundColor(green)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }

                Section(header: Text("Yoqiş / Как включить")) {
                    Step(number: 1, text: "Sozlamalar → Asosiy → Klaviatura → Klaviaturalar → Yangi klaviatura → Uzbek Keys\nНастройки → Основные → Клавиатура → Клавиатуры → Новые клавиатуры → Uzbek Keys")
                    Step(number: 2, text: "Yozayotganda 🌐 ni bosib, Uzbek Keys ni tanlang.\nВо время набора нажмите 🌐 и выберите Uzbek Keys.")
                    Button("Sozlamalarni oçiş / Открыть настройки") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }

                Section(header: Text("Qanday foydalaniş")) {
                    Step(number: 1, text: "Odatdagidek yozing — harflar özi almaşadi:\no' → ö    g' → ğ    sh → ş    ch → ç")
                    Step(number: 2, text: "aa + probel → Assalomu Alaykum\nvv + probel → Va Alaykum Assalom\nrr + probel → Rahmat")
                    Step(number: 3, text: "Almaşiş kerak bölmasa (masalan, inglizça «sh») — darhol ⌫ ni bosing.")
                    Step(number: 4, text: "«öğ» tugmasi — almaşişni yoqadi / öçiradi.")
                    Step(number: 5, text: "o, g, s, c ni bosib turing — ö, ğ, ş, ç.")
                    Step(number: 6, text: "Ikki marta probel — nuqta. Probelni bosib turib suring — kursor yuradi.")
                }

                Section(header: Text("Как пользоваться")) {
                    Step(number: 1, text: "Пишите как обычно — буквы меняются сами:\no' → ö    g' → ğ    sh → ş    ch → ç")
                    Step(number: 2, text: "aa + пробел → Assalomu Alaykum\nvv + пробел → Va Alaykum Assalom\nrr + пробел → Rahmat")
                    Step(number: 3, text: "Если замена не нужна (например, английское «sh») — сразу нажмите ⌫.")
                    Step(number: 4, text: "Кнопка «öğ» — включить / выключить замену.")
                    Step(number: 5, text: "Удерживайте o, g, s, c — ö, ğ, ş, ç.")
                    Step(number: 6, text: "Два пробела — точка. Удерживайте пробел и ведите пальцем — курсор.")
                }

                Section(header: Text("Sinab köring / Проверка")) {
                    TextField("Bu yerda yozing / Пишите здесь", text: $text)
                }

                if !whatsNew.isEmpty {
                    Section(header: Text("Yangiliklar / Что нового")) {
                        Text(whatsNew)
                            .font(.subheadline)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.vertical, 4)
                    }
                }

                Section(footer: Text("Ovoz va tebraniş / Звук и вибрация: Sozlamalar → Asosiy → Klaviatura → Klaviaturalar → Uzbek Keys → «Töliq ruxsat» / «Полный доступ». Klaviatura internetga çiqmaydi va matnni heç qayerga yubormaydi. Клавиатура не выходит в интернет и никуда не отправляет текст.")) {
                    HStack {
                        Text("Uzbek Keys \(version)")
                        Spacer()
                        Text("by işax")
                            .fontWeight(.semibold)
                            .foregroundColor(green)
                    }
                }
            }
            .navigationTitle("Uzbek Keys")
        }
        .navigationViewStyle(.stack)
    }
}
