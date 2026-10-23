package com.emirkuljanin.hnfeed.core.imagegen.ondevice

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the generator's output so on-device art cannot change silently. Expected values were computed independently
 * (a separate SplitMix64 implementation; seed 0 matches the published reference sequence).
 */
class SplitMix64Test {

    @Test
    fun matchesTheReferenceSequence() {
        val random = SplitMix64(0)

        assertEquals(0xE220A8397B1DCDAFuL.toLong(), random.nextLong())
        assertEquals(0x6E789E6AA1B965F4uL.toLong(), random.nextLong())
        assertEquals(0x06C45D188009454FuL.toLong(), random.nextLong())
    }

    @Test
    fun vectorFromText_isPinned() {
        val vector = ProceduralArtist.vectorFromText("Rust support lands in the Linux kernel")

        assertEquals(-0.16421311f, vector[0], absoluteTolerance = 1e-7f)
        assertEquals(0.49670508f, vector[1], absoluteTolerance = 1e-7f)
        assertEquals(0.70844954f, vector[2], absoluteTolerance = 1e-7f)
    }
}
