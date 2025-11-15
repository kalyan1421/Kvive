import UIKit

enum SpecialKey: String {
    case shift, shiftLocked, backspace, space, `return`, globe, emoji, numbers, letters, symbols
}

struct KeyModel: Hashable {
    let label: String
    let output: String?
    let special: SpecialKey?
    let alternates: [String]
    let widthWeight: CGFloat

    init(label: String,
         output: String? = nil,
         special: SpecialKey? = nil,
         alternates: [String] = [],
         widthWeight: CGFloat = 1.0) {
        self.label = label
        self.output = output
        self.special = special
        self.alternates = alternates
        self.widthWeight = widthWeight
    }
}

struct RowModel {
    let keys: [KeyModel]
}

struct LayoutModel {
    let rows: [RowModel]
    let showsNumberRow: Bool
}
