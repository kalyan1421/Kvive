import UIKit

final class LongPressPopup: UIView {
    private let stack = UIStackView()
    var onPick: ((String) -> Void)?

    init(options: [String]) {
        super.init(frame: .zero)
        backgroundColor = .systemBackground
        layer.cornerRadius = 8
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.2
        layer.shadowRadius = 6

        stack.axis = .horizontal
        stack.alignment = .fill
        stack.distribution = .fillEqually
        stack.spacing = 2
        stack.translatesAutoresizingMaskIntoConstraints = false

        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 6),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 6),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -6),
        ])

        options.forEach { opt in
            let b = UIButton(type: .system)
            b.setTitle(opt, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 18)
            b.addAction(UIAction { [weak self] _ in self?.onPick?(opt) }, for: .touchUpInside)
            stack.addArrangedSubview(b)
        }
    }

    func present(from anchor: UIView, in container: UIView) {
        container.addSubview(self)
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            bottomAnchor.constraint(equalTo: anchor.topAnchor, constant: -6),
            centerXAnchor.constraint(equalTo: anchor.centerXAnchor),
            heightAnchor.constraint(equalToConstant: 44)
        ])
        layoutIfNeeded()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}
