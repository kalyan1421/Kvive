import UIKit

class iOSKeyboardService: UIInputViewController {

    private enum ShiftMode {
        case off
        case on
        case locked
    }

    private let appGroupID = "group.com.kvive.aikeyboard.shared"
    private let defaults: UserDefaults?

    private let layoutController = UnifiedLayoutController()
    private let suggestionBar = UIStackView()
    private let rowsStack = UIStackView()
    private var heightConstraint: NSLayoutConstraint?
    private var longPressPopup: LongPressPopup?

    private var shiftMode: ShiftMode = .off
    private var currentMode: KeyboardMode = .letters

    private let keySpacing: CGFloat = 6
    private let DEFAULT_HEIGHT_DP: CGFloat = 285
    private let TOOLBAR_HEIGHT_DP: CGFloat = 72
    private let SUGGESTIONS_HEIGHT_DP: CGFloat = 44

    private var currentLanguage: String {
        defaults?.string(forKey: "flutter.current_language") ?? "en"
    }

    private var numberRowEnabled: Bool {
        defaults?.bool(forKey: "flutter.show_number_row") ?? false
    }

    private var customHeightPreference: CGFloat {
        CGFloat(defaults?.float(forKey: "flutter.keyboard_height_dp") ?? 0)
    }

    // MARK: - Init

    override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
        defaults = UserDefaults(suiteName: appGroupID)
        super.init(nibName: nibNameOrNil, bundle: nibBundleOrNil)
    }

    required init?(coder: NSCoder) {
        defaults = UserDefaults(suiteName: appGroupID)
        super.init(coder: coder)
    }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        configureUI()
        rebuildLayout()
    }

    // MARK: - UI Construction

    private func configureUI() {
        view.backgroundColor = .systemBackground

        suggestionBar.axis = .horizontal
        suggestionBar.distribution = .fillEqually
        suggestionBar.spacing = keySpacing
        suggestionBar.translatesAutoresizingMaskIntoConstraints = false

        rowsStack.axis = .vertical
        rowsStack.alignment = .fill
        rowsStack.distribution = .fillEqually
        rowsStack.spacing = keySpacing
        rowsStack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(suggestionBar)
        view.addSubview(rowsStack)

        let safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            suggestionBar.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: keySpacing),
            suggestionBar.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -keySpacing),
            suggestionBar.topAnchor.constraint(equalTo: safe.topAnchor, constant: keySpacing),
            suggestionBar.heightAnchor.constraint(equalToConstant: dp(SUGGESTIONS_HEIGHT_DP)),

            rowsStack.leadingAnchor.constraint(equalTo: safe.leadingAnchor, constant: keySpacing),
            rowsStack.trailingAnchor.constraint(equalTo: safe.trailingAnchor, constant: -keySpacing),
            rowsStack.topAnchor.constraint(equalTo: suggestionBar.bottomAnchor, constant: keySpacing),
            rowsStack.bottomAnchor.constraint(equalTo: safe.bottomAnchor, constant: -keySpacing)
        ])

        heightConstraint = view.heightAnchor.constraint(greaterThanOrEqualToConstant: computeKeyboardHeight())
        heightConstraint?.isActive = true

        // Prepare 3 suggestion buttons
        for _ in 0..<3 {
            let button = UIButton(type: .system)
            button.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
            button.setTitle("", for: .normal)
            button.isHidden = true
            button.addAction(UIAction { [weak self, weak button] _ in
                guard
                    let title = button?.title(for: .normal),
                    !title.isEmpty
                else { return }
                self?.textDocumentProxy.insertText(title)
            }, for: .touchUpInside)
            suggestionBar.addArrangedSubview(button)
        }
    }

    private func computeKeyboardHeight() -> CGFloat {
        var height = customHeightPreference > 0 ? customHeightPreference : DEFAULT_HEIGHT_DP
        height += TOOLBAR_HEIGHT_DP
        height += SUGGESTIONS_HEIGHT_DP
        return dp(height)
    }

    private func dp(_ value: CGFloat) -> CGFloat {
        // Treat dp as points for now (parity with Android definitions)
        value
    }

    // MARK: - Layout Rendering

    func rebuildLayout() {
        dismissAlternates()
        rowsStack.arrangedSubviews.forEach {
            rowsStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        let (rows, _) = layoutController.buildLayout(languageCode: currentLanguage,
                                                     mode: currentMode,
                                                     numberRowEnabled: numberRowEnabled)

        for row in rows {
            let rowStack = UIStackView()
            rowStack.axis = .horizontal
            rowStack.alignment = .fill
            rowStack.distribution = .fillProportionally
            rowStack.spacing = 0
            rowStack.translatesAutoresizingMaskIntoConstraints = false

            let totalWeight = row.reduce(CGFloat.zero) { $0 + adjustedKey(for: $1).widthWeight }

            for key in row {
                let displayKey = adjustedKey(for: key)

                let container = UIView()
                container.translatesAutoresizingMaskIntoConstraints = false
                rowStack.addArrangedSubview(container)

                let button = makeKeyButton(for: displayKey)
                container.addSubview(button)
                button.translatesAutoresizingMaskIntoConstraints = false

                NSLayoutConstraint.activate([
                    container.widthAnchor.constraint(equalTo: rowStack.widthAnchor, multiplier: displayKey.widthWeight / max(totalWeight, 1)),
                    button.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: keySpacing / 2),
                    button.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -keySpacing / 2),
                    button.topAnchor.constraint(equalTo: container.topAnchor),
                    button.bottomAnchor.constraint(equalTo: container.bottomAnchor),
                    button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44)
                ])
            }

            rowsStack.addArrangedSubview(rowStack)
        }

        heightConstraint?.constant = computeKeyboardHeight()
        view.setNeedsLayout()
    }

    private func makeKeyButton(for key: IOKey) -> KeyButton {
        let button = KeyButton(key: key)
        button.onTap = { [weak self] key in self?.handleTap(key) }
        button.onRepeat = { [weak self] key in self?.handleRepeat(key) }
        button.onLongPress = { [weak self, weak button] key, _ in
            guard let anchor = button else { return }
            self?.presentAlternates(for: key, anchor: anchor)
        }
        return button
    }

    private func adjustedKey(for key: IOKey) -> IOKey {
        guard key.special == nil else { return key }
        guard key.label.count == 1 else { return key }

        switch shiftMode {
        case .off:
            return IOKey(label: key.label.lowercased(),
                         output: key.output?.lowercased(),
                         special: key.special,
                         alternates: key.alternates,
                         widthWeight: key.widthWeight)
        case .on, .locked:
            return IOKey(label: key.label.uppercased(),
                         output: key.output?.uppercased(),
                         special: key.special,
                         alternates: key.alternates,
                         widthWeight: key.widthWeight)
        }
    }

    // MARK: - Key Handling

    private func handleTap(_ key: IOKey) {
        dismissAlternates()

        if let special = key.special {
            handleSpecialKey(special)
            return
        }

        let text: String
        switch shiftMode {
        case .off:
            text = key.output ?? key.label
        case .on, .locked:
            text = (key.output ?? key.label).uppercased()
        }

        textDocumentProxy.insertText(text)
        if shiftMode == .on { shiftMode = .off }
        rebuildLayout()
    }

    private func handleRepeat(_ key: IOKey) {
        guard key.special == .delete else { return }
        textDocumentProxy.deleteBackward()
    }

    private func handleSpecialKey(_ key: IOSpecialKey) {
        switch key {
        case .delete:
            textDocumentProxy.deleteBackward()
        case .space:
            textDocumentProxy.insertText(" ")
        case .return:
            textDocumentProxy.insertText("\n")
        case .globe:
            advanceToNextInputMode()
        case .shift:
            switch shiftMode {
            case .off:
                shiftMode = .on
            case .on:
                shiftMode = .locked
            case .locked:
                shiftMode = .off
            }
            rebuildLayout()
        case .numbers:
            currentMode = .numbers
            shiftMode = .off
            rebuildLayout()
        case .letters:
            currentMode = .letters
            shiftMode = .off
            rebuildLayout()
        case .symbols:
            currentMode = .symbols
            shiftMode = .off
            rebuildLayout()
        case .emoji:
            // Placeholder for emoji panel
            break
        }
    }

    private func presentAlternates(for key: IOKey, anchor: UIView) {
        guard !key.alternates.isEmpty else { return }
        dismissAlternates()

        let popup = LongPressPopup(options: key.alternates)
        popup.onPick = { [weak self] choice in
            self?.textDocumentProxy.insertText(choice)
            self?.dismissAlternates()
        }
        longPressPopup = popup
        popup.present(from: anchor, in: view)
    }

    private func dismissAlternates() {
        longPressPopup?.removeFromSuperview()
        longPressPopup = nil
    }

    // MARK: - Suggestions

    func updateSuggestions(_ suggestions: [String]) {
        for (index, view) in suggestionBar.arrangedSubviews.enumerated() {
            guard let button = view as? UIButton else { continue }
            if index < suggestions.count {
                button.setTitle(suggestions[index], for: .normal)
                button.isHidden = suggestions[index].isEmpty
            } else {
                button.setTitle("", for: .normal)
                button.isHidden = true
            }
        }
    }

    // MARK: - External hooks

    func onSettingsChanged() {
        heightConstraint?.constant = computeKeyboardHeight()
        rebuildLayout()
    }
}
