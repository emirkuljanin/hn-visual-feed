package com.emirkuljanin.hnfeed.feature.story

import kotlin.test.Test
import kotlin.test.assertEquals

class HackerNewsHtmlTest {

    @Test
    fun codeBlocks_keepTheirLinesAndIndentation_asMonospace() {
        val html = "<p>Try:</p><pre><code>  val x = 1\n  println(x)</code></pre><p>Done</p>"

        assertEquals(
            "<p>Try:</p><tt>&nbsp;&nbsp;val&nbsp;x&nbsp;=&nbsp;1<br>&nbsp;&nbsp;println(x)</tt><p>Done</p>",
            prepareHackerNewsHtml(html),
        )
    }

    @Test
    fun linksInsideCodeBlocks_keepTheirTags() {
        val html = "<pre><code>curl <a href=\"https://x.io\" rel=\"nofollow\">https://x.io</a></code></pre>"

        assertEquals(
            "<tt>curl&nbsp;<a href=\"https://x.io\" rel=\"nofollow\">https://x.io</a></tt>",
            prepareHackerNewsHtml(html),
        )
    }

    @Test
    fun inlineCode_isMonospace_andOtherTextIsUntouched() {
        assertEquals("Use <tt>adb</tt> often\n", prepareHackerNewsHtml("Use <code>adb</code> often\n"))
    }
}
