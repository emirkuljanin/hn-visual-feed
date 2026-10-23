package com.emirkuljanin.hnfeed.core.imagegen.safety

import java.text.Normalizer
import javax.inject.Inject

/**
 * Decides whether a title may be sent to the cloud text-to-image model. Flagged titles get an on-device thumbnail
 * instead: abstract procedural art that cannot depict anything.
 *
 * This is one layer of a defence in depth, not a classifier: a short list of sexual, graphic-violence and self-harm
 * terms, matched after normalising case, accents and digit-for-letter substitutions. Behind it, the prompt asks for
 * abstract shapes, and Cloudflare screens prompts itself (rejected ones also fall back to on-device art). The list
 * is deliberately small and will miss things; it does not try to cover hate speech or every phrasing. It avoids
 * words with common harmless uses ("Al Gore", "naked functions", "torturous", "sexuality") and matches whole words
 * only ("Sussex", "nudge" pass). A false positive only costs a cloud image: the title still gets on-device art.
 */
internal class TitleSafetyFilter @Inject constructor() {

    fun allowsCloudGeneration(title: String): Boolean {
        val words = words(title)
        if (words.any { it in BLOCKED_WORDS || BLOCKED_STEMS.any(it::startsWith) }) return false
        val pairs = words.zipWithNext { first, second -> "$first $second" }
        return pairs.none { it in BLOCKED_PHRASES }
    }

    private fun words(title: String): List<String> = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .map { LEET[it] ?: it }
        .joinToString("")
        .split(NON_LETTERS)
        .filter { it.isNotEmpty() }

    private companion object {
        val COMBINING_MARKS = Regex("\\p{Mn}+")
        val NON_LETTERS = Regex("[^a-z]+")
        val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's')

        /** Whole words only: short words like "sex" would otherwise match inside harmless ones. */
        val BLOCKED_WORDS = setOf(
            "sex", "sexy", "sexual", "sexually", "sexualised", "sexualized", "nude", "nudes", "nudity", "nsfw", "xxx",
            "hentai", "onlyfans", "fetish", "rape", "rapes", "raped", "raping", "gory", "corpse", "corpses",
            "torture", "tortures", "tortured", "torturing",
        )

        /** Roots with no common harmless words, so every inflection is caught ("beheaded", "suicidal"). */
        val BLOCKED_STEMS = listOf(
            "porn", "sextort", "erotic", "rapist", "behead", "decapitat", "dismember", "mutilat", "suicid", "massacr",
        )

        /** Two-word terms, matched on adjacent words ("self-harm" and "self harm" both normalise to this). */
        val BLOCKED_PHRASES = setOf("self harm", "child abuse")
    }
}
