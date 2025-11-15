package com.kvive.keyboard

import android.util.Log

/**
 * Emoji suggestion engine that suggests emojis based on typed words
 * Provides contextual emoji recommendations
 */
object EmojiSuggestionEngine {
    private const val TAG = "EmojiSuggestionEngine"
    
    // Word to emoji mappings
    private val wordToEmojiMap = mapOf(
        // Emotions
        "happy" to listOf("😊", "😁", "😄", "😃", "🙂", "😌", "🥰", "😍"),
        "sad" to listOf("😢", "😭", "😔", "😞", "😟", "🥺", "😿"),
        "love" to listOf("❤️", "💕", "💖", "💗", "💓", "💘", "💝", "🥰", "😍"),
        "angry" to listOf("😠", "😡", "🤬", "😤", "💢", "👿"),
        "excited" to listOf("🤩", "🥳", "🎉", "🎊", "✨", "💥", "🔥"),
        "tired" to listOf("😴", "💤", "😪", "🥱", "😫"),
        "surprised" to listOf("😲", "😱", "🤯", "😳", "😮", "🫨"),
        "cool" to listOf("😎", "🆒", "❄️", "🧊"),
        "funny" to listOf("😂", "🤣", "😄", "😆", "🤪", "🤭"),
        "crying" to listOf("😭", "😢", "😿", "💧"),
        
        // Food & Drink
        "food" to listOf("🍕", "🍔", "🍟", "🌭", "🥪", "🌮", "🍝", "🍜"),
        "pizza" to listOf("🍕"),
        "burger" to listOf("🍔"),
        "coffee" to listOf("☕", "🫖"),
        "tea" to listOf("🍵", "🫖", "🧋"),
        "beer" to listOf("🍺", "🍻"),
        "wine" to listOf("🍷", "🥂", "🍾"),
        "cake" to listOf("🍰", "🎂", "🧁"),
        "ice" to listOf("🍦", "🍨", "🧊", "❄️"),
        "fruit" to listOf("🍎", "🍊", "🍌", "🍇", "🍓", "🥝", "🍑"),
        "apple" to listOf("🍎", "🍏"),
        "banana" to listOf("🍌"),
        "pizza" to listOf("🍕"),
        "chocolate" to listOf("🍫", "🍩"),
        
        // Animals
        "cat" to listOf("🐱", "🐈", "🐈‍⬛", "😸", "😹", "😻"),
        "dog" to listOf("🐶", "🐕", "🦮", "🐕‍🦺"),
        "bird" to listOf("🐦", "🦅", "🦉", "🐧", "🦜"),
        "fish" to listOf("🐟", "🐠", "🐡", "🦈", "🐬"),
        "lion" to listOf("🦁"),
        "tiger" to listOf("🐯"),
        "elephant" to listOf("🐘"),
        "monkey" to listOf("🐵", "🙈", "🙉", "🙊"),
        "bear" to listOf("🐻", "🐻‍❄️", "🧸"),
        "rabbit" to listOf("🐰", "🐇"),
        "mouse" to listOf("🐭", "🐁"),
        
        // Nature & Weather
        "sun" to listOf("☀️", "🌞", "🌅", "🌄"),
        "moon" to listOf("🌙", "🌛", "🌜", "🌚", "🌝"),
        "star" to listOf("⭐", "🌟", "✨", "💫"),
        "rain" to listOf("🌧️", "☔", "💧", "⛈️"),
        "snow" to listOf("❄️", "☃️", "⛄", "🌨️"),
        "fire" to listOf("🔥", "🚒", "👨‍🚒"),
        "water" to listOf("💧", "💦", "🌊", "🏊"),
        "tree" to listOf("🌳", "🌲", "🌴", "🍃"),
        "flower" to listOf("🌸", "🌺", "🌻", "🌷", "🌹", "💐"),
        "rainbow" to listOf("🌈"),
        
        // Activities & Sports
        "football" to listOf("⚽", "🏈"),
        "basketball" to listOf("🏀"),
        "tennis" to listOf("🎾"),
        "baseball" to listOf("⚾"),
        "golf" to listOf("⛳", "🏌️"),
        "swimming" to listOf("🏊", "🏊‍♂️", "🏊‍♀️"),
        "running" to listOf("🏃", "🏃‍♂️", "🏃‍♀️", "💨"),
        "cycling" to listOf("🚴", "🚴‍♂️", "🚴‍♀️", "🚲"),
        "music" to listOf("🎵", "🎶", "🎤", "🎧", "🎸", "🎹"),
        "dance" to listOf("💃", "🕺", "🩰"),
        "art" to listOf("🎨", "🖼️", "🖌️"),
        "game" to listOf("🎮", "🕹️", "🎯", "🎲"),
        
        // Travel & Places
        "car" to listOf("🚗", "🚙", "🏎️"),
        "plane" to listOf("✈️", "🛩️"),
        "train" to listOf("🚂", "🚆", "🚇"),
        "bike" to listOf("🚲", "🏍️"),
        "home" to listOf("🏠", "🏡", "🏘️"),
        "school" to listOf("🏫", "📚", "🎓"),
        "hospital" to listOf("🏥", "⚕️"),
        "beach" to listOf("🏖️", "🌊", "🏄"),
        "mountain" to listOf("🏔️", "⛰️", "🗻"),
        "city" to listOf("🏙️", "🌃"),
        
        // Objects & Technology
        "phone" to listOf("📱", "📞", "☎️"),
        "computer" to listOf("💻", "🖥️", "⌨️"),
        "camera" to listOf("📷", "📸", "🎥"),
        "book" to listOf("📚", "📖", "📝"),
        "money" to listOf("💰", "💵", "💳", "💎"),
        "gift" to listOf("🎁", "🎀", "🎊"),
        "key" to listOf("🔑", "🗝️"),
        "light" to listOf("💡", "🔦", "✨"),
        "clock" to listOf("⏰", "⏱️", "🕐"),
        
        // Celebrations & Events
        "birthday" to listOf("🎂", "🎉", "🎊", "🎈", "🥳"),
        "party" to listOf("🎉", "🎊", "🥳", "🍾", "🎈"),
        "wedding" to listOf("💒", "👰", "🤵", "💍", "💐"),
        "christmas" to listOf("🎄", "🎅", "🤶", "🎁", "❄️"),
        "halloween" to listOf("🎃", "👻", "🦇", "🕷️", "🍭"),
        "celebration" to listOf("🎉", "🎊", "🥳", "🍾", "✨"),
        
        // Work & Study
        "work" to listOf("💼", "👔", "💻", "📊", "📈"),
        "study" to listOf("📚", "📝", "🎓", "✏️", "📖"),
        "meeting" to listOf("👥", "💼", "📊", "🤝"),
        "presentation" to listOf("📊", "📈", "💻", "🎯"),
        
        // Time & Days
        "morning" to listOf("🌅", "☀️", "☕", "🌄"),
        "night" to listOf("🌙", "⭐", "🌃", "😴"),
        "weekend" to listOf("🎉", "😎", "🛋️", "🍻"),
        "monday" to listOf("😫", "☕", "💼"),
        "friday" to listOf("🎉", "🍻", "😎", "🥳"),
        
        // Common words
        "yes" to listOf("✅", "👍", "💯", "🎯"),
        "no" to listOf("❌", "👎", "🚫", "⛔"),
        "good" to listOf("👍", "✅", "😊", "💯"),
        "bad" to listOf("👎", "❌", "😞", "💔"),
        "ok" to listOf("👌", "✅", "👍"),
        "thanks" to listOf("🙏", "😊", "👍", "💕"),
        "please" to listOf("🙏", "🥺"),
        "sorry" to listOf("😔", "🙏", "💔", "😞"),
        "wow" to listOf("😲", "🤯", "😱", "✨"),
        "omg" to listOf("😱", "🤯", "😲", "🫨"),
        "lol" to listOf("😂", "🤣", "😄", "😆"),
        "awesome" to listOf("🤩", "🔥", "💯", "⭐", "✨"),
        "great" to listOf("👍", "🔥", "💯", "⭐"),
        "perfect" to listOf("💯", "✨", "👌", "🎯")
    )
    
    /**
     * Get emoji suggestions for a given word
     */
    fun getEmojiSuggestions(word: String): List<String> {
        if (word.length < 2) return emptyList()
        
        val lowercaseWord = word.lowercase().trim()
        
        // Direct match
        wordToEmojiMap[lowercaseWord]?.let { return it.take(3) }
        
        // Partial matches
        val partialMatches = mutableListOf<String>()
        wordToEmojiMap.forEach { (key, emojis) ->
            when {
                key.contains(lowercaseWord) -> partialMatches.addAll(emojis.take(2))
                lowercaseWord.contains(key) && key.length > 2 -> partialMatches.addAll(emojis.take(2))
            }
        }
        
        // Remove duplicates and limit results
        return partialMatches.distinct().take(3)
    }
    
    /**
     * Get contextual emoji suggestions based on multiple words
     */
    fun getContextualSuggestions(text: String): List<String> {
        val words = text.lowercase().split("\\s+".toRegex()).takeLast(3) // Last 3 words
        val suggestions = mutableSetOf<String>()
        
        words.forEach { word ->
            suggestions.addAll(getEmojiSuggestions(word))
        }
        
        return suggestions.take(5).toList()
    }
    
    /**
     * Get trending/popular emoji suggestions
     */
    fun getTrendingEmojis(): List<String> {
        val context = AIKeyboardService.getInstance()?.applicationContext
        return EmojiRepository.getPopular(8, context).map { it.char }
    }
    
    /**
     * Search emojis by keyword with fuzzy matching
     */
    fun searchEmojis(query: String): List<String> {
        if (query.length < 2) return getTrendingEmojis()
        
        val queryLower = query.lowercase()
        val results = mutableSetOf<String>()
        
        // Exact matches first
        wordToEmojiMap[queryLower]?.let { results.addAll(it) }
        
        // Fuzzy matches
        wordToEmojiMap.forEach { (key, emojis) ->
            val similarity = calculateSimilarity(queryLower, key)
            if (similarity > 0.6) {
                results.addAll(emojis.take(2))
            }
        }
        
        // If no results, fall back to repository search dataset
        if (results.isEmpty()) {
            val context = AIKeyboardService.getInstance()?.applicationContext
            results.addAll(EmojiRepository.search(query, 20, context).map { it.char })
        }
        
        return results.take(10).toList()
    }
    
    /**
     * Simple string similarity calculation
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val commonChars = shorter.count { longer.contains(it) }
        return commonChars.toDouble() / longer.length
    }
    
    /**
     * Get emoji suggestions for current typing context
     */
    fun getSuggestionsForTyping(currentWord: String, previousText: String = ""): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Current word suggestions
        if (currentWord.isNotEmpty()) {
            suggestions.addAll(getEmojiSuggestions(currentWord))
        }
        
        // Context suggestions from previous text
        if (previousText.isNotEmpty()) {
            suggestions.addAll(getContextualSuggestions(previousText))
        }
        
        // Fill with popular if not enough suggestions
        if (suggestions.size < 3) {
            suggestions.addAll(getTrendingEmojis().take(3 - suggestions.size))
        }
        
        return suggestions.distinct().take(5)
    }
    
    /**
     * Log emoji usage for learning (future enhancement)
     */
    fun logEmojiUsage(emoji: String, context: String) {
        Log.d(TAG, "Emoji used: $emoji in context: ${context.take(20)}...")
        // Future: Store usage patterns for personalized suggestions
    }
}
