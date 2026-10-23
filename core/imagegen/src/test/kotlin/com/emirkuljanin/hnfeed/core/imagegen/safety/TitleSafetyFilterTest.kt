package com.emirkuljanin.hnfeed.core.imagegen.safety

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitleSafetyFilterTest {

    private val filter = TitleSafetyFilter()

    @Test
    fun allowsOrdinaryTitles_includingOnesThatContainBlockedWordsAsParts() {
        listOf(
            "Show HN: A tiny Hacker News reader",
            "Sussex researchers map the Essex coastline",
            "U.S. ex-president signs the bill", // "s" + "ex" must not become "sex".
            "Twitter's ex-CEO starts a new company",
            "Nudge theory and default settings",
            "The Grapes of Wrath, re-read",
            "Gradle 9.8 released",
            "Al Gore warns about climate models",
            "Naked functions are now stable in Rust",
            "A torturous migration to Gradle 9",
            "Sexuality research and online surveys",
        ).forEach { assertTrue(filter.allowsCloudGeneration(it), "Expected '$it' to be allowed") }
    }

    @Test
    fun blocksBlockedWords_regardlessOfCaseAccentsAndSubstitutions() {
        listOf(
            "Study finds NSFW content in training data",
            "The economics of p0rn sites", // Digit-for-letter substitution.
            "Nüde photo leak hits celebrities", // Accents stripped.
        ).forEach { assertFalse(filter.allowsCloudGeneration(it), "Expected '$it' to be blocked") }
    }

    @Test
    fun blocksInflectionsOfBlockedStems() {
        listOf(
            "Pornographic deepfakes are spreading",
            "Sexually explicit chatbot banned",
            "Sextortion scams target teenagers",
            "Journalist beheaded in video",
            "Suicides rise among young adults",
            "Prisoners were tortured, report says",
            "Village massacred in overnight raid",
            "Convicted rapists released early",
            "Report counts rapes in conflict zones",
        ).forEach { assertFalse(filter.allowsCloudGeneration(it), "Expected '$it' to be blocked") }
    }

    @Test
    fun blocksTwoWordPhrases_hyphenatedOrNot() {
        listOf("Self-harm content and recommendation algorithms", "self harm reporting tools")
            .forEach { assertFalse(filter.allowsCloudGeneration(it), "Expected '$it' to be blocked") }
    }
}
