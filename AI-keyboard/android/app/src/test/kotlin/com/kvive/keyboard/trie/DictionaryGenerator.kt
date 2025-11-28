package com.kvive.keyboard.trie

import org.junit.Test
import java.io.File

class DictionaryGenerator {

    /**
     * RUN THIS FUNCTION BY CLICKING THE GREEN PLAY BUTTON IN THE GUTTER
     */
    @Test
    fun generateEnglishDictionary() {
        // 1. SETUP PATHS (CHANGE THESE TO MATCH YOUR PC)
        // Windows Example: "C:\\Users\\YourName\\Desktop\\frequency_list_en.txt"
        // Mac/Linux Example: "/Users/YourName/Desktop/frequency_list_en.txt"
        val inputPath = "AI-keyboard/android/app/src/main/assets/symspell/frequency_dictionary_en_82_765.txt" 
        val outputPath = "/Users/kalyan/Downloads/Kvive/AI-keyboard/android/app/src/main/assets/dictionaries/en.bin"

        println("🚀 Starting Dictionary Compilation...")
        println("📖 Reading from: $inputPath")

        val inputFile = File(inputPath)
        val outputFile = File(outputPath)

        if (!inputFile.exists()) {
            throw RuntimeException("❌ Input file not found at: $inputPath")
        }

        // 2. PARSE TEXT FILE
        val wordMap = HashMap<String, Int>()
        
        inputFile.forEachLine { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val word = parts[0].lowercase()
                // Clamp frequency to Int, we will compress it to 0-255 later anyway
                val freq = parts[1].toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 1
                
                // Filter out garbage (optional)
                if (word.all { it.isLetter() || it == '\'' }) {
                    wordMap[word] = freq
                }
            }
        }

        println("✅ Loaded ${wordMap.size} unique words.")

        // 3. COMPILE TO BINARY
        try {
            DictionaryCompiler.compile(wordMap, outputFile)
            println("🎉 SUCCESS! Dictionary saved to: ${outputFile.absolutePath}")
            println("📂 File Size: ${outputFile.length() / 1024} KB")
        } catch (e: Exception) {
            println("❌ Error during compilation:")
            e.printStackTrace()
        }
    }
}