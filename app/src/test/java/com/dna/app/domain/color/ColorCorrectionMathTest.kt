package com.dna.app.domain.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorCorrectionMathTest {

    @Test
    fun `neutral correction yields identity matrix`() {
        val m = ColorCorrectionMath.toMatrix4x5(ColorCorrection.NEUTRAL)
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        identity.forEachIndexed { i, expected -> assertNear(expected, m[i], 0.01f, "index $i") }
    }

    @Test
    fun `4x4 export drops the bias column`() {
        val m4x4 = ColorCorrectionMath.toRgbMatrix4x4(ColorCorrection.NEUTRAL)
        assertEquals(16, m4x4.size)
        assertNear(1f, m4x4[0], 0.01f, "r->r")
        assertNear(1f, m4x4[5], 0.01f, "g->g")
        assertNear(1f, m4x4[10], 0.01f, "b->b")
    }

    @Test
    fun `warm temperature boosts red over blue`() {
        val (r, _, b) = ColorCorrectionMath.kelvinToRgbGain(3000f, 0f)
        assertTrue("warm temp should give r > b (got r=$r b=$b)", r > b)
    }

    @Test
    fun `cool temperature boosts blue over red`() {
        val (r, _, b) = ColorCorrectionMath.kelvinToRgbGain(9000f, 0f)
        assertTrue("cool temp should give b > r (got r=$r b=$b)", b > r)
    }

    @Test
    fun `neutral white-balance gain pulls colour cast toward grey`() {
        val warmCast = ColorCorrectionMath.whiteBalanceGainFromTap(220, 200, 160)
        // Red is the brightest channel, so its gain should be the smallest.
        assertTrue("red gain ${warmCast[0]} should be < green ${warmCast[1]}", warmCast[0] < warmCast[1])
        assertTrue("green gain ${warmCast[1]} should be < blue ${warmCast[2]}", warmCast[1] < warmCast[2])
    }

    private fun assertNear(expected: Float, actual: Float, eps: Float, label: String) {
        assertTrue("$label: expected $expected ± $eps, got $actual", abs(expected - actual) <= eps)
    }
}
