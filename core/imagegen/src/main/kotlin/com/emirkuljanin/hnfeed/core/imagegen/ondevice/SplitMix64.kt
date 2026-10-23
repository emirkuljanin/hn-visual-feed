package com.emirkuljanin.hnfeed.core.imagegen.ondevice

/**
 * A tiny, fully specified pseudo-random generator (SplitMix64). Used instead of `kotlin.random.Random`, whose
 * sequence is only guaranteed within one Kotlin version: on-device art must stay identical across library
 * upgrades, or every cached thumbnail and screenshot baseline would silently change.
 */
internal class SplitMix64(seed: Long) {

    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Uniform in [-1, 1). */
    fun nextSignedUnit(): Double = (nextLong() ushr 11) * DOUBLE_UNIT * 2.0 - 1.0

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
