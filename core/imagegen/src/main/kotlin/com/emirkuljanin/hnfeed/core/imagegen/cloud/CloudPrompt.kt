package com.emirkuljanin.hnfeed.core.imagegen.cloud

/**
 * Turns a story title into a text-to-image prompt. The fixed framing keeps results consistent across the feed.
 *
 * Tuned against the real model (FLUX.1 [schnell] on Cloudflare): asking for "an illustration of the headline"
 * makes it draw screens and documents full of pseudo-text, and negations like "no text" do not help (FLUX has no
 * negative prompt). Asking for an abstract visual metaphor in "shapes and colour only" gives wordless, on-topic
 * compositions. Do not add "safe for work" wording: Cloudflare's prompt classifier flags the phrase as NSFW.
 */
internal object CloudPrompt {

    private val HN_PREFIX = Regex("^(show|ask|tell|launch) hn:\\s*", RegexOption.IGNORE_CASE)
    private const val MAX_TITLE_LENGTH = 200

    fun forTitle(title: String): String {
        val subject = title.replace(HN_PREFIX, "").trim().take(MAX_TITLE_LENGTH)
        return "Abstract geometric illustration, a visual metaphor for: $subject. " +
            "Shapes and colour only, flat vector style, soft palette, layered composition filling a wide 16:9 frame."
    }
}
