import UIKit

final class KeyButton: UIControl {
    private let titleLabel = UILabel()
    private let feedback = UIImpactFeedbackGenerator(style: .light)
    var key: IOKey!
    var onTap: ((IOKey) -> Void)?
    var onLongPress: ((IOKey, CGPoint) -> Void)?
    var onRepeat: ((IOKey) -> Void)?

    private var repeatTimer: Timer?
    private var longPressTimer: Timer?

    init(key: IOKey) {
        super.init(frame: .zero)
        self.key = key
        isExclusiveTouch = true
        layer.cornerRadius = 8
        layer.masksToBounds = true
        backgroundColor = UIColor.secondarySystemFill

        titleLabel.textAlignment = .center
        titleLabel.font = .systemFont(ofSize: 18, weight: .medium)
        titleLabel.text = key.label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        addSubview(titleLabel)
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 4),
            titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 2),
            titleLabel.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -2),
        ])

        addTarget(self, action: #selector(touchDown), for: .touchDown)
        addTarget(self, action: #selector(touchUp), for: [.touchUpInside, .touchCancel, .touchDragExit])
        addTarget(self, action: #selector(touchDragEnter), for: .touchDragEnter)
    }

    @objc private func touchDown() {
        feedback.impactOccurred()
        animateDown()
        scheduleLongPress()
        if key.special == .delete { startRepeat() }
    }

    @objc private func touchUp() {
        animateUp()
        cancelLongPress()
        stopRepeat()
        onTap?(key)
    }

    @objc private func touchDragEnter() { animateDown() }

    private func animateDown() {
        UIView.animate(withDuration: 0.08) { self.backgroundColor = UIColor.tertiarySystemFill }
    }
    private func animateUp() {
        UIView.animate(withDuration: 0.08) { self.backgroundColor = UIColor.secondarySystemFill }
    }

    private func scheduleLongPress() {
        guard !key.alternates.isEmpty else { return }
        longPressTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            let point = self.convert(self.bounds.center, to: self.superview)
            self.onLongPress?(self.key, point)
        }
    }
    private func cancelLongPress() { longPressTimer?.invalidate(); longPressTimer = nil }

    private func startRepeat() {
        repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.06, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.onRepeat?(self.key)
        }
    }
    private func stopRepeat() { repeatTimer?.invalidate(); repeatTimer = nil }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

private extension CGRect { var center: CGPoint { CGPoint(x: midX, y: midY) } }
