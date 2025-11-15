import UIKit

final class KeyboardState {
    enum ShiftMode { case off, on, locked }
    var shift: ShiftMode = .off
    var isLetters: Bool = true
    var isSymbols: Bool = false

    func applyShift(to text: String) -> String {
        guard shift != .off else { return text }
        return text.count == 1 ? text.uppercased() : text
    }

    func toggleShift() {
        switch shift {
        case .off: shift = .on
        case .on: shift = .off
        case .locked: shift = .off
        }
    }

    func setCapsLocked(_ locked: Bool) { shift = locked ? .locked : .on }
}
