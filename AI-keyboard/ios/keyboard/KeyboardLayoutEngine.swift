import UIKit

final class KeyboardLayoutEngine {
    func lettersLayout(shiftOn: Bool) -> LayoutModel {
        func k(_ s: String, alt: [String] = []) -> KeyModel {
            KeyModel(label: shiftOn ? s.uppercased() : s, output: shiftOn ? s.uppercased() : s, alternates: alt)
        }
        let row1 = RowModel(keys: "q w e r t y u i o p".split(separator: " ").map { k(String($0)) })
        let row2 = RowModel(keys: "a s d f g h j k l".split(separator: " ").map { k(String($0)) })
        let row3: [KeyModel] = [
            KeyModel(label: "⇧", special: .shift, widthWeight: 1.3),
        ] + "z x c v b n m".split(separator: " ").map { k(String($0)) } + [
            KeyModel(label: "⌫", special: .backspace, widthWeight: 1.3),
        ]
        let row4 = RowModel(keys: [
            KeyModel(label: "123", special: .numbers, widthWeight: 1.4),
            KeyModel(label: "🌐", special: .globe, widthWeight: 1.0),
            KeyModel(label: "space", output: " ", special: .space, widthWeight: 4.4),
            KeyModel(label: "↵", special: .return, widthWeight: 1.6),
        ])
        return LayoutModel(rows: [row1, row2, RowModel(keys: row3), row4], showsNumberRow: false)
    }

    func numbersLayout() -> LayoutModel {
        func k(_ s: String) -> KeyModel { KeyModel(label: s, output: s) }
        let r1 = RowModel(keys: "1 2 3 4 5 6 7 8 9 0".split(separator: " ").map { k(String($0)) })
        let r2 = RowModel(keys: "- / : ; ( ) ₹ & @ \"".split(separator: " ").map { k(String($0)) })
        let r3 = RowModel(keys: [
            KeyModel(label: "#=", special: .symbols, widthWeight: 1.4),
        ] + " . , ? ! ' ".split(separator: " ").map { k(String($0)) } + [
            KeyModel(label: "⌫", special: .backspace, widthWeight: 1.4)
        ])
        let r4 = RowModel(keys: [
            KeyModel(label: "ABC", special: .letters, widthWeight: 1.4),
            KeyModel(label: "🌐", special: .globe, widthWeight: 1.0),
            KeyModel(label: "space", output: " ", special: .space, widthWeight: 4.4),
            KeyModel(label: "↵", special: .return, widthWeight: 1.6),
        ])
        return LayoutModel(rows: [r1, r2, r3, r4], showsNumberRow: false)
    }

    func symbolsLayout() -> LayoutModel {
        func k(_ s: String) -> KeyModel { KeyModel(label: s, output: s) }
        let r1 = RowModel(keys: "[ ] { } # % ^ * + =".split(separator: " ").map { k(String($0)) })
        let r2 = RowModel(keys: "_ \\ | ~ < > € £ ¥ •".split(separator: " ").map { k(String($0)) })
        let r3 = RowModel(keys: [
            KeyModel(label: "123", special: .numbers, widthWeight: 1.4),
        ] + " . , ? ! ' ".split(separator: " ").map { k(String($0)) } + [
            KeyModel(label: "⌫", special: .backspace, widthWeight: 1.4)
        ])
        let r4 = RowModel(keys: [
            KeyModel(label: "ABC", special: .letters, widthWeight: 1.4),
            KeyModel(label: "🌐", special: .globe, widthWeight: 1.0),
            KeyModel(label: "space", output: " ", special: .space, widthWeight: 4.4),
            KeyModel(label: "↵", special: .return, widthWeight: 1.6),
        ])
        return LayoutModel(rows: [r1, r2, r3, r4], showsNumberRow: false)
    }
}
