package com.materialkolor

import com.materialkolor.blend.Blend
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.materialkolor.scheme.Variant
import com.materialkolor.score.Score
import com.materialkolor.temperature.TemperatureCache
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks every target against values computed by Google's Java implementation (see [Goldens]).
 *
 * `mcu-upstream` compares the port with upstream on the JVM only; this suite exists for the targets
 * that cannot run Java, where `cbrt`, `pow` and `atan2` come from a different math library and an
 * off-by-one-ulp result can round a channel to a different byte. Where the JVM port already differs
 * from upstream the golden holds the port's value, flagged `matchesUpstream = false`.
 */
class PlatformConformanceTest {
    @Test
    fun hctFromArgbMatchesUpstream() {
        for (golden in Goldens.hctFromArgb) {
            val hct = Hct.fromInt(golden.argb)
            val label = golden.argb.hex()
            assertClose(golden.hue, hct.hue, "hue of $label")
            assertClose(golden.chroma, hct.chroma, "chroma of $label")
            assertClose(golden.tone, hct.tone, "tone of $label")
            assertEquals(golden.argb, hct.toInt(), "round trip of $label")
        }
    }

    @Test
    fun hctSolverMatchesUpstream() {
        val mismatches =
            Goldens.hctSolved.mapNotNull { golden ->
                val actual = Hct.from(golden.hue, golden.chroma, golden.tone).toInt()
                if (actual == golden.argb) {
                    null
                } else {
                    "HCT(${golden.hue}, ${golden.chroma}, ${golden.tone}): expected ${golden.argb.hex()}, was ${actual.hex()}"
                }
            }
        assertNoMismatches(mismatches, Goldens.hctSolved.size)
    }

    @Test
    fun dynamicSchemesMatchGoldens() {
        val colors = MaterialDynamicColors().allDynamicColors().map { it() }
        val mismatches =
            Goldens.schemes.mapNotNull { golden ->
                val scheme = golden.build()
                val argbs = colors.map { it.getArgb(scheme) }
                if (digest(argbs) == golden.digest) {
                    null
                } else {
                    val dump = colors.zip(argbs).joinToString { (color, argb) -> "${color.name}=${argb.hex()}" }
                    "$golden: $dump"
                }
            }
        assertNoMismatches(mismatches, Goldens.schemes.size)
    }

    @Test
    fun harmonizeMatchesUpstream() {
        for ((design, source, expected) in Goldens.harmonized) {
            assertEquals(
                expected.hex(),
                Blend.harmonize(design, source).hex(),
                "harmonize(${design.hex()}, ${source.hex()})",
            )
        }
    }

    @Test
    fun temperatureMatchesUpstream() {
        for ((seed, expected) in Goldens.temperature) {
            val cache = TemperatureCache(Hct.fromInt(seed))
            val actual = listOf(cache.complement.toInt()) + cache.analogousColors.map { it.toInt() }
            assertEquals(expected.map { it.hex() }, actual.map { it.hex() }, "temperature of ${seed.hex()}")
        }
    }

    @Test
    fun quantizeAndScoreMatchJvmPort() {
        val quantized = QuantizerCelebi.quantize(GoldenInputs.image(), 128)
        assertEquals(Goldens.scored.map { it.hex() }, Score.score(quantized).map { it.hex() })
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        message: String,
    ) {
        assertTrue(abs(expected - actual) <= 1e-9, "$message: expected $expected, was $actual")
    }

    private fun assertNoMismatches(
        mismatches: List<String>,
        total: Int,
    ) {
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of $total differ from the goldens:\n" + mismatches.joinToString("\n"))
        }
    }
}

internal data class HctGolden(
    val argb: Int,
    val hue: Double,
    val chroma: Double,
    val tone: Double,
)

internal data class HctSolveGolden(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    val argb: Int,
)

internal data class SchemeGolden(
    val seed: Int,
    val variant: Variant,
    val spec: String,
    val isDark: Boolean,
    val contrast: Double,
    val digest: Int,
    val matchesUpstream: Boolean,
) {
    fun build(): DynamicScheme {
        val hct = Hct.fromInt(seed)
        val version = ColorSpec.SpecVersion.valueOf(spec)
        val platform = DynamicScheme.Platform.PHONE
        return when (variant) {
            Variant.MONOCHROME -> SchemeMonochrome(hct, isDark, contrast, version, platform)
            Variant.NEUTRAL -> SchemeNeutral(hct, isDark, contrast, version, platform)
            Variant.TONAL_SPOT -> SchemeTonalSpot(hct, isDark, contrast, version, platform)
            Variant.VIBRANT -> SchemeVibrant(hct, isDark, contrast, version, platform)
            Variant.EXPRESSIVE -> SchemeExpressive(hct, isDark, contrast, version, platform)
            Variant.FIDELITY -> SchemeFidelity(hct, isDark, contrast, version, platform)
            Variant.CONTENT -> SchemeContent(hct, isDark, contrast, version, platform)
            Variant.RAINBOW -> SchemeRainbow(hct, isDark, contrast, version, platform)
            Variant.FRUIT_SALAD -> SchemeFruitSalad(hct, isDark, contrast, version, platform)
        }
    }

    override fun toString(): String = "${seed.hex()} $variant $spec dark=$isDark contrast=$contrast"
}

/** Kept byte-for-byte in step with `GoldenInputs` in mcu-upstream's GoldenGenerator. */
internal object GoldenInputs {
    fun image(): IntArray =
        IntArray(64 * 64) { i ->
            when {
                i < 1200 -> {
                    0xFF4285F4.toInt()
                }
                i < 1900 -> {
                    0xFFB3261E.toInt()
                }
                i < 2300 -> {
                    0xFFFFE082.toInt()
                }
                else -> {
                    val r = (i * 37) and 0xFF
                    val g = ((i * 101) shr 3) and 0xFF
                    val b = (i * 13 + 7 * (i shr 6)) and 0xFF
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
}

internal fun digest(colors: List<Int>): Int {
    var hash = 0x811C9DC5.toInt()
    for (color in colors) {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            hash = hash xor ((color shr shift) and 0xFF)
            hash *= 0x01000193
        }
    }
    return hash
}

private fun Int.hex(): String = "#" + toUInt().toString(16).uppercase().padStart(8, '0')
