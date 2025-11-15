/// Trie data structure for efficient word lookup and prefix matching
/// Optimized for autocorrect and predictive text functionality
class WordTrie {
  final TrieNode _root = TrieNode();
  int _nodeCount = 0;

  /// Insert a word into the trie with optional frequency
  void insert(String word, {int frequency = 1}) {
    if (word.isEmpty) return;
    
    TrieNode current = _root;
    word = word.toLowerCase();
    
    for (int i = 0; i < word.length; i++) {
      String char = word[i];
      
      if (!current.children.containsKey(char)) {
        current.children[char] = TrieNode();
        _nodeCount++;
      }
      
      current = current.children[char]!;
    }
    
    current.isEndOfWord = true;
    current.frequency = frequency;
  }

  /// Search for exact word match
  bool contains(String word) {
    if (word.isEmpty) return false;
    
    TrieNode? node = _findNode(word.toLowerCase());
    return node != null && node.isEndOfWord;
  }

  /// Get frequency of a word (0 if not found)
  int getFrequency(String word) {
    if (word.isEmpty) return 0;
    
    TrieNode? node = _findNode(word.toLowerCase());
    return (node != null && node.isEndOfWord) ? node.frequency : 0;
  }

  /// Find all words with given prefix, sorted by frequency
  List<WordSuggestion> getWordsWithPrefix(String prefix, {int limit = 10}) {
    if (prefix.isEmpty) return [];
    
    TrieNode? prefixNode = _findNode(prefix.toLowerCase());
    if (prefixNode == null) return [];
    
    List<WordSuggestion> suggestions = [];
    _collectWords(prefixNode, prefix.toLowerCase(), suggestions);
    
    // Sort by frequency (descending) then alphabetically
    suggestions.sort((a, b) {
      int freqCompare = b.frequency.compareTo(a.frequency);
      return freqCompare != 0 ? freqCompare : a.word.compareTo(b.word);
    });
    
    return suggestions.take(limit).toList();
  }

  /// Get word suggestions based on partial input with fuzzy matching
  List<WordSuggestion> getSuggestions(String input, {int limit = 5, int maxDistance = 2}) {
    if (input.isEmpty) return [];
    
    input = input.toLowerCase();
    List<WordSuggestion> suggestions = [];
    
    // First, try exact prefix matches
    List<WordSuggestion> prefixMatches = getWordsWithPrefix(input, limit: limit);
    suggestions.addAll(prefixMatches);
    
    // If we don't have enough suggestions, try fuzzy matching
    if (suggestions.length < limit) {
      List<WordSuggestion> fuzzyMatches = _getFuzzyMatches(input, maxDistance, limit - suggestions.length);
      
      // Remove duplicates and add fuzzy matches
      Set<String> existingWords = suggestions.map((s) => s.word).toSet();
      for (var match in fuzzyMatches) {
        if (!existingWords.contains(match.word)) {
          suggestions.add(match);
        }
      }
    }
    
    return suggestions.take(limit).toList();
  }

  /// Get all words in the trie (for debugging/testing)
  List<String> getAllWords() {
    List<WordSuggestion> suggestions = [];
    _collectWords(_root, '', suggestions);
    return suggestions.map((s) => s.word).toList();
  }

  /// Get memory usage statistics
  TrieStats getStats() {
    return TrieStats(
      nodeCount: _nodeCount,
      wordCount: _countWords(_root),
      maxDepth: _getMaxDepth(_root, 0),
    );
  }

  /// Clear all data from the trie
  void clear() {
    _root.children.clear();
    _nodeCount = 0;
  }

  // Private helper methods

  TrieNode? _findNode(String word) {
    TrieNode current = _root;
    
    for (int i = 0; i < word.length; i++) {
      String char = word[i];
      if (!current.children.containsKey(char)) {
        return null;
      }
      current = current.children[char]!;
    }
    
    return current;
  }

  void _collectWords(TrieNode node, String prefix, List<WordSuggestion> suggestions) {
    if (node.isEndOfWord) {
      suggestions.add(WordSuggestion(
        word: prefix,
        frequency: node.frequency,
        confidence: 1.0,
      ));
    }
    
    node.children.forEach((char, childNode) {
      _collectWords(childNode, prefix + char, suggestions);
    });
  }

  List<WordSuggestion> _getFuzzyMatches(String input, int maxDistance, int limit) {
    List<WordSuggestion> matches = [];
    _fuzzySearch(_root, '', input, 0, maxDistance, matches);
    
    // Sort by edit distance (ascending) then frequency (descending)
    matches.sort((a, b) {
      int distanceCompare = a.editDistance.compareTo(b.editDistance);
      return distanceCompare != 0 ? distanceCompare : b.frequency.compareTo(a.frequency);
    });
    
    return matches.take(limit).toList();
  }

  void _fuzzySearch(TrieNode node, String current, String target, int distance, int maxDistance, List<WordSuggestion> matches) {
    if (distance > maxDistance) return;
    
    if (node.isEndOfWord && current.isNotEmpty) {
      int editDistance = _calculateEditDistance(current, target);
      if (editDistance <= maxDistance) {
        matches.add(WordSuggestion(
          word: current,
          frequency: node.frequency,
          confidence: 1.0 - (editDistance / maxDistance),
          editDistance: editDistance,
        ));
      }
    }
    
    // Continue searching if we haven't exceeded max distance
    if (distance < maxDistance || current.length < target.length + maxDistance) {
      node.children.forEach((char, childNode) {
        _fuzzySearch(childNode, current + char, target, distance, maxDistance, matches);
      });
    }
  }

  int _calculateEditDistance(String s1, String s2) {
    if (s1.isEmpty) return s2.length;
    if (s2.isEmpty) return s1.length;
    
    List<List<int>> dp = List.generate(
      s1.length + 1,
      (i) => List.filled(s2.length + 1, 0),
    );
    
    // Initialize first row and column
    for (int i = 0; i <= s1.length; i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= s2.length; j++) {
      dp[0][j] = j;
    }
    
    // Fill the DP table
    for (int i = 1; i <= s1.length; i++) {
      for (int j = 1; j <= s2.length; j++) {
        if (s1[i - 1] == s2[j - 1]) {
          dp[i][j] = dp[i - 1][j - 1];
        } else {
          dp[i][j] = 1 + [
            dp[i - 1][j],     // deletion
            dp[i][j - 1],     // insertion
            dp[i - 1][j - 1], // substitution
          ].reduce((a, b) => a < b ? a : b);
        }
      }
    }
    
    return dp[s1.length][s2.length];
  }

  int _countWords(TrieNode node) {
    int count = node.isEndOfWord ? 1 : 0;
    
    node.children.forEach((char, childNode) {
      count += _countWords(childNode);
    });
    
    return count;
  }

  int _getMaxDepth(TrieNode node, int currentDepth) {
    int maxDepth = currentDepth;
    
    node.children.forEach((char, childNode) {
      int childDepth = _getMaxDepth(childNode, currentDepth + 1);
      if (childDepth > maxDepth) {
        maxDepth = childDepth;
      }
    });
    
    return maxDepth;
  }
}

/// Node in the Trie data structure
class TrieNode {
  Map<String, TrieNode> children = {};
  bool isEndOfWord = false;
  int frequency = 0;
}

/// Word suggestion with metadata
class WordSuggestion {
  final String word;
  final int frequency;
  final double confidence;
  final int editDistance;

  const WordSuggestion({
    required this.word,
    required this.frequency,
    required this.confidence,
    this.editDistance = 0,
  });

  @override
  String toString() {
    return 'WordSuggestion(word: $word, freq: $frequency, conf: ${confidence.toStringAsFixed(2)})';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WordSuggestion &&
            runtimeType == other.runtimeType &&
            word == other.word;
  }

  @override
  int get hashCode => word.hashCode;
}

/// Statistics about the Trie
class TrieStats {
  final int nodeCount;
  final int wordCount;
  final int maxDepth;

  const TrieStats({
    required this.nodeCount,
    required this.wordCount,
    required this.maxDepth,
  });

  double get memoryUsageKB => nodeCount * 0.1; // Rough estimate

  @override
  String toString() {
    return 'TrieStats(nodes: $nodeCount, words: $wordCount, depth: $maxDepth, memory: ${memoryUsageKB.toStringAsFixed(1)}KB)';
  }
}
