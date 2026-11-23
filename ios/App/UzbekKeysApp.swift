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

struct ContentView: View {
    @State private var text = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Qoidalar / Правила")) {
                    Text("o' → ö    g' → ğ\nsh → ş    ch → ç")
                        .font(.title3)
                    Text("aa → Assalomu Alaykum\nvv → Va Alaykum Assalom")
                    Text("«öğ» tugmasi — yoqish/o'chirish.\n⌫ darhol bosilsa — almashtirish bekor bo'ladi.\n\nКнопка «öğ» — включить/выключить замену.\n⌫ сразу после замены — вернуть как было.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section(header: Text("Yoqish / Как включить")) {
                    Text("1. Settings → General → Keyboard → Keyboards → Add New Keyboard… → Uzbek Keys\n\n1. Настройки → Основные → Клавиатура → Клавиатуры → Новые клавиатуры… → Uzbek Keys")
                    Text("2. Matn yozayotganda 🌐 tugmasini bosib, Uzbek Keys ni tanlang.\n\n2. Во время набора нажмите 🌐 и выберите Uzbek Keys.")
                    Button("Sozlamalarni ochish / Открыть настройки") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }

                Section(header: Text("Sinab ko'ring / Проверка")) {
                    TextField("Bu yerda yozing / Пишите здесь", text: $text)
                }

                Section(footer: Text("Versiya / Версия \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?") (\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"))")) {
                    EmptyView()
                }
            }
            .navigationTitle("Uzbek Keys")
        }
        .navigationViewStyle(.stack)
    }
}
