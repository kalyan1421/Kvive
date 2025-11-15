import Foundation
import UIKit

final class UnifiedLayoutController {

    private let suite = UserDefaults(suiteName: "group.com.kvive.aikeyboard.shared")
    private let fileManager = FileManager.default
    private lazy var decoder: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()

    func buildLayout(languageCode: String,
                     mode: KeyboardMode,
                     numberRowEnabled: Bool) -> (rows: [[IOKey]], direction: String) {

        let keymap: KeymapFile?
        switch mode {
        case .letters:
            keymap = readJSON(named: "keymaps/\(languageCode).json", as: KeymapFile.self)
        default:
            keymap = nil
        }

        let templateName: String = {
            switch mode {
            case .letters:
                return keymap?.template ?? "layout_templates/qwerty_template.json"
            case .symbols, .numbers:
                return "layout_templates/symbols_template.json"
            case .emoji:
                return keymap?.template ?? "layout_templates/qwerty_template.json"
            }
        }()

        let template: TemplateFile = readJSON(named: templateName, as: TemplateFile.self)
            ?? TemplateFile(
                rows: [
                    ["q","w","e","r","t","y","u","i","o","p"],
                    ["a","s","d","f","g","h","j","k","l"],
                    ["SHIFT","z","x","c","v","b","n","m","DELETE"],
                    ["?123","GLOBE","SPACE","RETURN"]
                ],
                direction: "LTR"
            )

        var rows = materializeRows(template: template,
                                   languageCode: languageCode,
                                   keymap: keymap)

        if numberRowEnabled {
            let numerals = numberRow(for: languageCode)
            let numberRowKeys = numerals.map { IOKey(label: $0, output: $0) }
            rows.insert(numberRowKeys, at: 0)
        }

        let direction = (template.direction ?? "LTR").uppercased()
        return (rows, direction)
    }

    // MARK: - Helpers

    private func materializeRows(template: TemplateFile,
                                 languageCode: String,
                                 keymap: KeymapFile?) -> [[IOKey]] {
        var rows: [[IOKey]] = []

        for row in template.rows {
            var newRow: [IOKey] = []
            for raw in row {
                let mapped = keymap?.base?[raw] ?? raw
                var key = KeyNormalizer.normalize(label: mapped)

                if key.special == nil {
                    key = key.withOutput(mapped)
                }

                if let alternates = keymap?.long_press?[raw], !alternates.isEmpty {
                    key = key.withAlternates(alternates)
                }

                newRow.append(key)
            }
            rows.append(newRow)
        }

        return rows
    }

    private func numberRow(for language: String) -> [String] {
        switch language.lowercased() {
        case "hi":
            return ["१","२","३","४","५","६","७","८","९","०"]
        case "ta":
            return ["௧","௨","௩","௪","௫","௬","௭","௮","௯","௦"]
        case "te":
            return ["౧","౨","౩","౪","౫","౬","౭","౮","౯","౦"]
        default:
            return ["1","2","3","4","5","6","7","8","9","0"]
        }
    }

    private func readJSON<T: Decodable>(named name: String, as type: T.Type) -> T? {
        if let containerURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: "group.com.kvive.aikeyboard.shared") {
            let url = containerURL.appendingPathComponent(name)
            if fileManager.fileExists(atPath: url.path),
               let data = try? Data(contentsOf: url),
               let decoded = try? decoder.decode(T.self, from: data) {
                return decoded
            }
        }

        guard let bundleURL = bundleURL(forResource: name),
              let data = try? Data(contentsOf: bundleURL),
              let decoded = try? decoder.decode(T.self, from: data) else {
            return nil
        }
        return decoded
    }

    private func bundleURL(forResource resource: String) -> URL? {
        let ns = resource as NSString
        let directory = ns.deletingLastPathComponent
        let file = ns.lastPathComponent as NSString
        let name = file.deletingPathExtension
        let ext = file.pathExtension.isEmpty ? nil : file.pathExtension

        return Bundle.main.url(forResource: name,
                               withExtension: ext,
                               subdirectory: directory.isEmpty ? nil : directory)
    }
}
