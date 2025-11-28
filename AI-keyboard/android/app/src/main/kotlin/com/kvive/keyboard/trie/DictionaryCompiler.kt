package com.kvive.keyboard.trie

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.LinkedList

private const val NODE_SIZE = 10 // 2 char + 1 freq + 3 child + 3 sibling + 1 padding
private const val MAX_OFFSET = 0xFFFFFF // 3-byte unsigned int ceiling (~16 MB)

// Helper class to build the tree in memory before flushing to disk
private class TrieNode(
    val char: Char,
    val parent: TrieNode? = null
) {
    var frequency: Int = 0
    val children: MutableMap<Char, TrieNode> = linkedMapOf()
    var offset: Int = 0
    var firstChild: TrieNode? = null
    var nextSibling: TrieNode? = null
}

object DictionaryCompiler {

    /**
     * Compile a word → frequency map into the compact binary trie format.
     */
    fun compile(words: Map<String, Int>, outputFile: File) {
        val root = TrieNode('^')

        // 1. Build object tree from words
        words.forEach { (word, freq) ->
            var current = root
            for (char in word) {
                current = current.children.getOrPut(char) { TrieNode(char, current) }
            }
            // Clamp frequency to 0-255 (0 = not a word end)
            current.frequency = freq.coerceIn(0, 255)
        }

        // 2. Flatten to linear array (breadth-first) and link siblings
        val nodesList = ArrayList<TrieNode>()
        val queue = LinkedList<TrieNode>()
        queue.add(root)

        var currentOffset = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (currentOffset > MAX_OFFSET) {
                throw IllegalStateException("Trie exceeds 16MB limit (offset=$currentOffset)")
            }
            node.offset = currentOffset
            nodesList.add(node)
            currentOffset += NODE_SIZE

            // Link children deterministically (sorted by char for reproducible output)
            val sortedChildren = node.children.values.sortedBy { it.char }
            node.firstChild = sortedChildren.firstOrNull()
            sortedChildren.zipWithNext { first, second -> first.nextSibling = second }

            // Enqueue children
            sortedChildren.forEach { queue.add(it) }
        }

        val buffer = ByteBuffer.allocate(nodesList.size * NODE_SIZE)

        // 3. Write to binary file
        for (node in nodesList) {
            buffer.putChar(node.char) // Char (2 bytes)
            buffer.put(node.frequency.toByte()) // Frequency (1 byte)

            // Child offset (3 bytes)
            val childOffset = node.firstChild?.offset ?: 0
            putUInt24(buffer, childOffset)

            // Sibling offset (3 bytes)
            val siblingOffset = node.nextSibling?.offset ?: 0
            putUInt24(buffer, siblingOffset)

            buffer.put(0) // Padding/flags
        }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { stream ->
            buffer.flip()
            stream.channel.write(buffer)
        }
    }

    private fun putUInt24(buffer: ByteBuffer, value: Int) {
        require(value in 0..MAX_OFFSET) { "Value $value exceeds 3-byte limit" }
        buffer.put((value shr 16).toByte())
        buffer.put((value shr 8).toByte())
        buffer.put(value.toByte())
    }
}
