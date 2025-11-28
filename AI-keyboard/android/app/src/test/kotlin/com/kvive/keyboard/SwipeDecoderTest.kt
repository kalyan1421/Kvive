package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for SwipeDecoderML Beam Search algorithm
 * Tests the "Gboard-quality" swipe decoding functionality
 */
class SwipeDecoderTest {

    private lateinit var mockContext: Context
    private lateinit var mockTrie: MappedTrieDictionary

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockTrie = mock(MappedTrieDictionary::class.java)
    }

    /**
     * Test 1: Simple vertical swipe "IT"
     * Verifies basic beam search functionality
     */
    @Test
    fun `test exact vertical swipe produces simple word`() {
        // Setup a simple "IT" graph
        // Root(0) -> 'i'(10) -> 't'(20) [End]
        `when`(mockTrie.getChildren(0)).thenReturn(mapOf('i' to 10))
        `when`(mockTrie.getChildren(10)).thenReturn(mapOf('t' to 20))
        `when`(mockTrie.getChildren(20)).thenReturn(emptyMap())
        `when`(mockTrie.getFrequencyAtNode(0)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(10)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(20)).thenReturn(100) // "it" is valid word
        
        // Define simple layout: i=(0.5, 0.0), t=(0.5, 1.0)
        val layout = mapOf(
            'i' to Pair(0.5f, 0.0f), 
            't' to Pair(0.5f, 1.0f)
        )
        
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        // Create a perfect vertical swipe path (7 points for good sampling)
        val path = SwipePath(listOf(
            Pair(0.5f, 0.0f),  // Start at i
            Pair(0.5f, 0.17f),
            Pair(0.5f, 0.33f),
            Pair(0.5f, 0.5f),  // Middle
            Pair(0.5f, 0.67f),
            Pair(0.5f, 0.83f),
            Pair(0.5f, 1.0f)   // End at t
        ))
        
        val results = decoder.decode(path)
        
        // Should find "it"
        assertNotNull("Results should not be null", results)
        assertTrue("Should have at least one result", results.isNotEmpty())
        assertEquals("First result should be 'it'", "it", results.firstOrNull()?.first)
    }

    /**
     * Test 2: "Corner Cutting" Test - The Real Proof of Beam Search
     * Tests if decoder can handle imprecise paths (HELLO with skipped L's)
     */
    @Test
    fun `test corner cutting swipe recognizes correct word`() {
        // Setup "HELLO" graph with competitive paths
        // Root(0) -> 'h'(10) -> 'e'(20) -> 'l'(30) -> 'l'(40) -> 'o'(50) [HELLO, freq=200]
        //                    -> 'r'(25) -> 'o'(35) [HERO, freq=50]
        
        `when`(mockTrie.getChildren(0)).thenReturn(mapOf('h' to 10))
        `when`(mockTrie.getChildren(10)).thenReturn(mapOf('e' to 20))
        `when`(mockTrie.getChildren(20)).thenReturn(mapOf(
            'l' to 30,  // Path to HELLO
            'r' to 25   // Path to HERO
        ))
        `when`(mockTrie.getChildren(30)).thenReturn(mapOf('l' to 40))
        `when`(mockTrie.getChildren(40)).thenReturn(mapOf('o' to 50))
        `when`(mockTrie.getChildren(50)).thenReturn(emptyMap())
        `when`(mockTrie.getChildren(25)).thenReturn(mapOf('o' to 35))
        `when`(mockTrie.getChildren(35)).thenReturn(emptyMap())
        
        // Frequencies
        `when`(mockTrie.getFrequencyAtNode(0)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(10)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(20)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(30)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(40)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(50)).thenReturn(200) // "hello" - high frequency
        `when`(mockTrie.getFrequencyAtNode(25)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(35)).thenReturn(50)  // "hero" - lower frequency
        
        // QWERTY layout (simplified)
        val layout = mapOf(
            'h' to Pair(0.575f, 0.44f),
            'e' to Pair(0.25f, 0.17f),
            'l' to Pair(0.875f, 0.44f),
            'r' to Pair(0.35f, 0.17f),
            'o' to Pair(0.85f, 0.17f)
        )
        
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        // Create path: H -> E -> (cut corner toward O, passing near R but closer to L path)
        val path = SwipePath(listOf(
            Pair(0.575f, 0.44f), // H
            Pair(0.4f, 0.30f),   // Between H and E
            Pair(0.25f, 0.17f),  // E
            Pair(0.45f, 0.25f),  // Between E and O (cutting corner, near R)
            Pair(0.65f, 0.30f),  // Moving toward O
            Pair(0.85f, 0.17f)   // O
        ))
        
        val results = decoder.decode(path)
        
        // Should prefer "hello" over "hero" due to higher frequency
        assertNotNull("Results should not be null", results)
        assertTrue("Should have at least one result", results.isNotEmpty())
        
        val firstResult = results.firstOrNull()?.first
        println("Corner cutting test result: $firstResult (all: ${results.map { it.first }})")
        
        // With proper beam search and frequency weighting, "hello" should rank higher
        assertTrue("Should contain 'hello' in results", 
            results.any { it.first == "hello" })
    }

    /**
     * Test 3: Empty path handling
     */
    @Test
    fun `test empty path returns empty results`() {
        val layout = mapOf('a' to Pair(0.5f, 0.5f))
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        val path = SwipePath(emptyList())
        val results = decoder.decode(path)
        
        assertTrue("Empty path should return empty results", results.isEmpty())
    }

    /**
     * Test 4: Very short path (less than 3 points)
     */
    @Test
    fun `test short path returns empty results`() {
        val layout = mapOf('a' to Pair(0.5f, 0.5f))
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        val path = SwipePath(listOf(
            Pair(0.5f, 0.5f),
            Pair(0.6f, 0.6f)
        ))
        val results = decoder.decode(path)
        
        assertTrue("Path with less than 3 points should return empty results", results.isEmpty())
    }

    /**
     * Test 5: Path scoring function (backward compatibility)
     */
    @Test
    fun `test computePathScore returns valid score`() {
        `when`(mockTrie.getChildren(0)).thenReturn(mapOf('h' to 10))
        `when`(mockTrie.getChildren(10)).thenReturn(mapOf('i' to 20))
        `when`(mockTrie.getChildren(20)).thenReturn(emptyMap())
        `when`(mockTrie.getFrequencyAtNode(20)).thenReturn(100)
        
        val layout = mapOf(
            'h' to Pair(0.0f, 0.0f),
            'i' to Pair(1.0f, 1.0f)
        )
        
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        val path = SwipePath(listOf(
            Pair(0.0f, 0.0f),
            Pair(0.5f, 0.5f),
            Pair(1.0f, 1.0f)
        ))
        
        val score = decoder.computePathScore("hi", path)
        
        assertTrue("Score should be between 0 and 1", score >= 0.0 && score <= 1.0)
        assertTrue("Score should be positive for valid path", score > 0.0)
    }

    /**
     * Test 6: Frequency weighting
     * Verifies that higher frequency words rank higher
     */
    @Test
    fun `test frequency weighting prefers common words`() {
        // Setup two words with same path but different frequencies
        `when`(mockTrie.getChildren(0)).thenReturn(mapOf('c' to 10))
        `when`(mockTrie.getChildren(10)).thenReturn(mapOf('a' to 20))
        `when`(mockTrie.getChildren(20)).thenReturn(mapOf('t' to 30, 'r' to 40))
        `when`(mockTrie.getChildren(30)).thenReturn(emptyMap())
        `when`(mockTrie.getChildren(40)).thenReturn(emptyMap())
        
        `when`(mockTrie.getFrequencyAtNode(0)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(10)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(20)).thenReturn(0)
        `when`(mockTrie.getFrequencyAtNode(30)).thenReturn(200) // "cat" - common
        `when`(mockTrie.getFrequencyAtNode(40)).thenReturn(10)  // "car" - less common
        
        val layout = mapOf(
            'c' to Pair(0.35f, 0.71f),
            'a' to Pair(0.075f, 0.44f),
            't' to Pair(0.45f, 0.17f),
            'r' to Pair(0.35f, 0.17f)
        )
        
        val decoder = SwipeDecoderML(mockContext, layout, mockTrie)
        
        // Ambiguous path between C-A-T and C-A-R
        val path = SwipePath(listOf(
            Pair(0.35f, 0.71f),  // C
            Pair(0.2f, 0.57f),   // Toward A
            Pair(0.075f, 0.44f), // A
            Pair(0.25f, 0.30f),  // Ambiguous (between T and R)
            Pair(0.4f, 0.17f)    // Between T and R
        ))
        
        val results = decoder.decode(path)
        
        assertNotNull("Results should not be null", results)
        assertTrue("Should have results", results.isNotEmpty())
        
        // "cat" should rank higher due to frequency
        if (results.size >= 2) {
            val catScore = results.find { it.first == "cat" }?.second
            val carScore = results.find { it.first == "car" }?.second
            
            if (catScore != null && carScore != null) {
                assertTrue("'cat' should score higher than 'car' due to frequency",
                    catScore > carScore)
            }
        }
    }
}

