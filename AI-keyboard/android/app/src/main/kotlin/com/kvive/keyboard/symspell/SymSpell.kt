package com.kvive.keyboard.symspell

/**
 * Lightweight SymSpell implementation tailored for on-device IME usage.
 *
 * This version retains the delete-based index for speed but also returns
 * distance and frequency information needed for Gboard-style scoring.
 */
class SymSpell(
    private val maxDictionaryEditDistance: Int = 2,
    private val prefixLength: Int = 7
) {
    data class SymSpellResult(
        val term: String,
        val distance: Int,
        val frequency: Int,
        val score: Double
    )

    private val dictionary = HashMap<String, Int>()
    private val deletes = HashMap<String, MutableList<String>>()

    fun loadDictionary(lines: List<String>) {
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            val parts = line.split("\t", ",", " ").filter { it.isNotBlank() }
            if (parts.isEmpty()) return@forEach

            val word = parts[0].lowercase()
            val freq = parts.getOrNull(1)?.toIntOrNull() ?: 1

            addWord(word, freq)
        }
    }

    fun addWord(word: String, frequency: Int = 1) {
        if (word.isBlank()) return
        val normalized = word.lowercase()
        val current = dictionary[normalized] ?: 0
        val newFreq = maxOf(current, frequency)
        dictionary[normalized] = newFreq

        generateDeletes(normalized).forEach { delete ->
            deletes.getOrPut(delete) { mutableListOf() }.add(normalized)
        }
    }

    fun lookup(
        input: String,
        mode: Verbosity = Verbosity.TOP,
        maxEditDistance: Int = maxDictionaryEditDistance
    ): List<SymSpellResult> {
        if (input.isBlank()) return emptyList()
        val normalized = input.lowercase()

        val consideredDeletes = HashSet<String>()
        val consideredSuggestions = HashSet<String>()
        val candidates = ArrayDeque<String>()
        val results = mutableListOf<SymSpellResult>()

        candidates.add(normalized)
        consideredDeletes.add(normalized)

        while (candidates.isNotEmpty()) {
            val candidate = candidates.removeFirst()

            // Stop exploring if difference in length already exceeds threshold
            if (normalized.length - candidate.length > maxEditDistance) continue

            // If candidate is in dictionary, evaluate distance
            dictionary[candidate]?.let { freq ->
                val distance = computeDistance(normalized, candidate, maxEditDistance)
                if (distance <= maxEditDistance && consideredSuggestions.add(candidate)) {
                    results.add(buildResult(candidate, distance, freq, normalized))
                }
            }

            // Explore deletes for further candidates
            if (normalized.length - candidate.length < maxEditDistance) {
                deletes[candidate]?.forEach { suggestion ->
                    if (consideredSuggestions.contains(suggestion)) return@forEach
                    val freq = dictionary[suggestion] ?: return@forEach
                    val distance = computeDistance(normalized, suggestion, maxEditDistance)
                    if (distance <= maxEditDistance && consideredSuggestions.add(suggestion)) {
                        results.add(buildResult(suggestion, distance, freq, normalized))
                    }
                }

                // Add next level deletes
                generateDeletes(candidate).forEach { delete ->
                    if (consideredDeletes.add(delete)) {
                        candidates.add(delete)
                    }
                }
            }
        }

        val sorted = results
            .distinctBy { it.term }
            .sortedWith(
                compareBy<SymSpellResult> { it.distance }
                    .thenByDescending { it.frequency }
                    .thenByDescending { it.score }
            )

        val limited = when (mode) {
            Verbosity.TOP -> sorted.take(1)
            Verbosity.CLOSEST -> sorted.take(3)
            Verbosity.ALL -> sorted
        }

        return limited
    }

    fun frequency(word: String): Int = dictionary[word.lowercase()] ?: 0

    private fun buildResult(
        term: String,
        distance: Int,
        frequency: Int,
        original: String
    ): SymSpellResult {
        val maxLen = maxOf(original.length, term.length).coerceAtLeast(1)
        val distanceScore = 1.0 - (distance.toDouble() / maxLen.toDouble())
        val freqScore = kotlin.math.ln(frequency.toDouble() + 1)
        val score = (distanceScore * 0.7) + (freqScore * 0.3)
        return SymSpellResult(term, distance, frequency, score)
    }

    private fun generateDeletes(word: String): List<String> {
        val deletesForWord = mutableListOf<String>()
        if (word.isEmpty()) return deletesForWord

        val prefix = if (word.length > prefixLength) word.substring(0, prefixLength) else word

        fun addDeletes(current: String, distance: Int) {
            if (distance > maxDictionaryEditDistance) return
            for (i in current.indices) {
                val deletion = current.removeRange(i, i + 1)
                if (deletesForWord.add(deletion) && distance < maxDictionaryEditDistance) {
                    addDeletes(deletion, distance + 1)
                }
            }
        }

        addDeletes(prefix, 1)
        return deletesForWord
    }

    private fun computeDistance(source: String, target: String, maxDistance: Int): Int {
        // Optimized Damerau-Levenshtein with early exit for small maxDistance
        if (source == target) return 0
        if (kotlin.math.abs(source.length - target.length) > maxDistance) return maxDistance + 1

        val s = source.lowercase()
        val t = target.lowercase()
        val m = s.length
        val n = t.length

        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var bestInRow = curr[0]
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost  // substitution
                )

                // Transposition
                if (i > 1 && j > 1 && s[i - 1] == t[j - 2] && s[i - 2] == t[j - 1]) {
                    curr[j] = minOf(curr[j], prev[j - 2] + cost)
                }

                bestInRow = minOf(bestInRow, curr[j])
            }
            if (bestInRow > maxDistance) return maxDistance + 1
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return curr[n]
    }
}

