package com.emirkuljanin.hnfeed.core.imagegen.cloud

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudPromptTest {

    @Test
    fun asksForAnAbstractMetaphorOfTheTitle_withoutTheHnPrefix() {
        val prompt = CloudPrompt.forTitle("Show HN: I built a tiny Hacker News reader")

        assertContains(prompt, "visual metaphor for: I built a tiny Hacker News reader.")
        assertContains(prompt, "Shapes and colour only")
        assertFalse("Show HN" in prompt)
    }

    @Test
    fun neverContainsWordingThatCloudflaresClassifierFlags() {
        assertFalse("safe for work" in CloudPrompt.forTitle("Anything").lowercase())
    }

    @Test
    fun capsVeryLongTitles() {
        val prompt = CloudPrompt.forTitle("x".repeat(5_000))

        assertTrue(prompt.length < 600, "Prompt was ${prompt.length} characters")
    }
}
