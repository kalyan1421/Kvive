import Foundation
import UIKit

enum IOSpecialKey: String, Codable {
    case shift
    case delete
    case space
    case `return`
    case globe
    case numbers
    case letters
    case symbols
    case emoji
}

struct IOKey: Hashable {
    let label: String
    let output: String?
    let special: IOSpecialKey?
    let alternates: [String]
    let widthWeight: CGFloat

    init(label: String,
         output: String? = nil,
         special: IOSpecialKey? = nil,
         alternates: [String] = [],
         widthWeight: CGFloat = 1.0) {
        self.label = label
        self.output = output
        self.special = special
        self.alternates = alternates
        self.widthWeight = widthWeight
    }

    func withAlternates(_ values: [String]) -> IOKey {
        IOKey(label: label,
              output: output,
              special: special,
              alternates: values,
              widthWeight: widthWeight)
    }

    func withOutput(_ string: String?) -> IOKey {
        IOKey(label: label,
              output: string,
              special: special,
              alternates: alternates,
              widthWeight: widthWeight)
    }
}

struct TemplateFile: Decodable {
    let rows: [[String]]
    let direction: String?
}

struct KeymapFile: Decodable {
    let language: String
    let template: String?
    let base: [String: String]?
    let alt: [String: String]?
    let long_press: [String: [String]]?
}

enum KeyboardMode {
    case letters
    case numbers
    case symbols
    case emoji
}

enum PanelType {
    case grammar
    case tone
    case assistant
    case clipboard
    case emoji
}

struct KeyNormalizer {
    static func normalize(label raw: String) -> IOKey {
        switch raw {
        case "SHIFT", "⇧":
            return IOKey(label: "⇧", special: .shift, widthWeight: 1.3)
        case "DELETE", "⌫":
            return IOKey(label: "⌫", special: .delete, widthWeight: 1.3)
        case "RETURN", "⏎", "↵":
            return IOKey(label: "↵", special: .return, widthWeight: 1.6)
        case "SPACE", "space", " ":
            return IOKey(label: "space", output: " ", special: .space, widthWeight: 4.4)
        case "GLOBE", "🌐":
            return IOKey(label: "🌐", special: .globe, widthWeight: 1.0)
        case "?123":
            return IOKey(label: "?123", special: .numbers, widthWeight: 1.4)
        case "ABC":
            return IOKey(label: "ABC", special: .letters, widthWeight: 1.4)
        case "#=":
            return IOKey(label: "#=", special: .symbols, widthWeight: 1.4)
        default:
            return IOKey(label: raw, output: raw, widthWeight: 1.0)
        }
    }
}
