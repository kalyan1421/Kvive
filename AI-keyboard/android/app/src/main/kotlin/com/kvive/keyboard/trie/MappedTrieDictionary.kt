package com.kvive.keyboard.trie

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Memory-mapped trie reader for the compact 10-byte node format.
 */
class MappedTrieDictionary(context: Context, language: String) {

    private val buffer: MappedByteBuffer

    init {
        val file = ensureDictionaryFile(context, language)
        buffer = RandomAccessFile(file, "r").channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    /**
     * Checks if a word exists and returns its frequency (0 = missing/non-terminal).
     */
    fun getFrequency(word: String): Int {
        var currentOffset = 0 // Start at root

        for (char in word) {
            currentOffset = findChild(currentOffset, char)
            if (currentOffset == -1) return 0
        }

        return getUnsignedByte(currentOffset + 2)
    }

    fun contains(word: String): Boolean = getFrequency(word) > 0

    /**
     * Returns list of words starting with [prefix], ordered by frequency desc.
     */
    fun getSuggestions(prefix: String, limit: Int = 10): List<String> {
        if (prefix.isEmpty()) return emptyList()

        var nodeOffset = 0
        for (char in prefix) {
            nodeOffset = findChild(nodeOffset, char)
            if (nodeOffset == -1) return emptyList()
        }

        val results = mutableListOf<Pair<String, Int>>()
        collectWords(nodeOffset, prefix, results, limit)
        return results
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    /**
     * Alias for getSuggestions - Gboard-style API
     * Returns list of words starting with [prefix], ordered by frequency desc.
     */
    fun lookupByPrefix(prefix: String, limit: Int = 10): List<String> {
        return getSuggestions(prefix, limit)
    }

    // --- Low Level Binary Navigation ---

    // Scans the children of [parentOffset] to find [targetChar]
    // Returns offset of the child node, or -1 if not found
    private fun findChild(parentOffset: Int, targetChar: Char): Int {
        var childOffset = getUInt24(parentOffset + 3)

        while (childOffset != 0) {
            val charAtNode = buffer.getChar(childOffset)
            if (charAtNode == targetChar) {
                return childOffset
            }
            childOffset = getUInt24(childOffset + 6)
        }
        return -1
    }

    private fun collectWords(
        offset: Int,
        currentWord: String,
        results: MutableList<Pair<String, Int>>,
        limit: Int
    ) {
        if (results.size >= limit * 4) return // keep traversal bounded while allowing ranking

        val freq = getUnsignedByte(offset + 2)
        if (freq > 0) {
            results.add(currentWord to freq)
        }

        var child = getUInt24(offset + 3)
        while (child != 0 && results.size < limit * 4) {
            val char = buffer.getChar(child)
            collectWords(child, currentWord + char, results, limit)
            child = getUInt24(child + 6)
        }
    }

    /**
     * Iterate the trie and return up to [limit] most frequent words.
     * Useful for seeding downstream spellcheckers when text assets are unavailable.
     */
    fun getTopFrequentWords(limit: Int = 10000): Map<String, Int> {
        if (limit <= 0) return emptyMap()
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(0, "", results, limit)
        return results
            .sortedByDescending { it.second }
            .take(limit)
            .associate { it.first to it.second }
    }

    // Helper to read 3-byte int from buffer
    private fun getUInt24(index: Int): Int {
        val b1 = buffer.get(index).toInt() and 0xFF
        val b2 = buffer.get(index + 1).toInt() and 0xFF
        val b3 = buffer.get(index + 2).toInt() and 0xFF
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun getUnsignedByte(index: Int): Int = buffer.get(index).toInt() and 0xFF

    /**
     * Returns a map of (Character -> Next Node Offset) for a given parent node.
     * Used for graph traversal in swipe decoding.
     */
    fun getChildren(parentOffset: Int): Map<Char, Int> {
        val children = mutableMapOf<Char, Int>()
        
        // In your binary format, the first child pointer is at offset + 3
        // This assumes your getUInt24 and buffer are accessible
        var childOffset = getUInt24(parentOffset + 3)

        // Iterate through the linked list of siblings
        while (childOffset != 0) {
            val charAtNode = buffer.getChar(childOffset)
            children[charAtNode] = childOffset
            
            // Move to next sibling (offset + 6 is the sibling pointer in your format)
            childOffset = getUInt24(childOffset + 6)
        }
        return children
    }

    /**
     * Get the exact frequency of the word ending at this node offset.
     * Returns 0 if this node is not a word end.
     */
    fun getFrequencyAtNode(nodeOffset: Int): Int {
        // In your format, frequency is at offset + 2
        return getUnsignedByte(nodeOffset + 2)
    }

    private fun ensureDictionaryFile(context: Context, language: String): File {
        val target = File(context.filesDir, "dictionaries/${language}.bin")
        if (target.exists()) return target

        val assetPath = "dictionaries/${language}.bin"
        return try {
            context.assets.open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            target
        } catch (e: Exception) {
            throw IllegalStateException("Binary dictionary not found for '$language' at $target or assets/$assetPath", e)
        }
    }
}
