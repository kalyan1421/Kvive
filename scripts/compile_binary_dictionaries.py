#!/usr/bin/env python3
"""
Generate compact binary trie dictionaries (.bin) from existing word list assets.

Format: 10-byte nodes (2-byte char, 1-byte freq, 3-byte child offset,
3-byte sibling offset, 1-byte padding). Left-child / right-sibling layout.

Usage:
    python scripts/compile_binary_dictionaries.py \
        --assets android/app/src/main/assets/dictionaries \
        --out android/app/src/main/assets/dictionaries
    python scripts/compile_binary_dictionaries.py --languages en
"""

from __future__ import annotations

import argparse
import struct
from collections import deque
from pathlib import Path
from typing import Dict, List, Tuple

NODE_SIZE = 10
MAX_OFFSET = 0xFFFFFF  # 3-byte unsigned


class TrieNode:
    __slots__ = ("char", "freq", "children", "offset", "first_child", "next_sibling", "parent")

    def __init__(self, char: str, parent: "TrieNode | None" = None):
        self.char = char
        self.freq = 0
        self.children: Dict[str, TrieNode] = {}
        self.offset = 0
        self.first_child: TrieNode | None = None
        self.next_sibling: TrieNode | None = None
        self.parent = parent


def parse_words(path: Path, max_words: int = 50000) -> Dict[str, int]:
    words: Dict[str, int] = {}
    count = 0
    with path.open("r", encoding="utf-8") as f:
        for raw in f:
            if count >= max_words:
                break
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.replace("\t", " ").replace(",", " ").split()
            word = parts[0]
            freq = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else (1000 + count)
            words[word] = max(0, min(255, freq))
            count += 1
    return words


def build_trie(words: Dict[str, int]) -> List[TrieNode]:
    root = TrieNode("^")
    for word, freq in words.items():
        node = root
        for ch in word:
            node = node.children.setdefault(ch, TrieNode(ch, node))
        node.freq = freq

    nodes: List[TrieNode] = []
    queue: deque[TrieNode] = deque([root])
    current_offset = 0

    while queue:
        node = queue.popleft()
        if current_offset > MAX_OFFSET:
            raise ValueError("Trie exceeds 16MB limit")
        node.offset = current_offset
        nodes.append(node)
        current_offset += NODE_SIZE

        # Link siblings in deterministic order
        children = sorted(node.children.values(), key=lambda n: n.char)
        if children:
            node.first_child = children[0]
            for left, right in zip(children, children[1:]):
                left.next_sibling = right
            queue.extend(children)

    return nodes


def write_bin(nodes: List[TrieNode], out_file: Path) -> None:
    out_file.parent.mkdir(parents=True, exist_ok=True)
    with out_file.open("wb") as f:
        for node in nodes:
            f.write(struct.pack(">H", ord(node.char)))  # char (2 bytes, big-endian)
            f.write(struct.pack(">B", node.freq))       # frequency (1 byte)
            f.write(_uint24(node.first_child.offset if node.first_child else 0))
            f.write(_uint24(node.next_sibling.offset if node.next_sibling else 0))
            f.write(b"\x00")  # padding / flags


def _uint24(value: int) -> bytes:
    if value < 0 or value > MAX_OFFSET:
        raise ValueError(f"Value {value} exceeds 3-byte limit")
    return bytes([(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF])


def discover_languages(assets_dir: Path) -> List[str]:
    return sorted({p.name.split("_words.txt")[0] for p in assets_dir.glob("*_words.txt")})


def compile_language(lang: str, assets_dir: Path, out_dir: Path) -> Path:
    words_path = assets_dir / f"{lang}_words.txt"
    if not words_path.exists():
        raise FileNotFoundError(f"Missing word list: {words_path}")
    words = parse_words(words_path)
    nodes = build_trie(words)
    out_file = out_dir / f"{lang}.bin"
    write_bin(nodes, out_file)
    return out_file


def main():
    parser = argparse.ArgumentParser(description="Compile word lists into binary trie dictionaries.")
    parser.add_argument("--assets", type=Path, default=Path("android/app/src/main/assets/dictionaries"))
    parser.add_argument("--out", type=Path, default=None, help="Output directory for .bin files (defaults to assets dir)")
    parser.add_argument("--languages", nargs="*", help="Languages to compile (default: all *_words.txt in assets dir)")
    args = parser.parse_args()

    assets_dir: Path = args.assets
    out_dir: Path = args.out or assets_dir

    if not assets_dir.exists():
        raise SystemExit(f"Assets directory not found: {assets_dir}")

    langs = args.languages or discover_languages(assets_dir)
    if not langs:
        raise SystemExit(f"No *_words.txt files found in {assets_dir}")

    print(f"Compiling languages: {', '.join(langs)}")
    for lang in langs:
        out = compile_language(lang, assets_dir, out_dir)
        size_kb = out.stat().st_size / 1024.0
        print(f"  ✓ {lang}.bin -> {out} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
