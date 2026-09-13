package now.abhi.graycie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

class BrandResourceTest {
    @Test fun chatFavourGlyphIsCenteredPaddedAndSafeUnderLauncherMasks() {
        val vector = source("src/main/res/drawable/ic_graycie_a.xml")
        assertTrue(vector.contains("android:viewportWidth=\"108\""))
        assertTrue(vector.contains("android:viewportHeight=\"108\""))
        assertTrue(vector.contains("android:fillColor=\"#FF000000\""))
        assertFalse(vector.contains("strokeColor"))

        val coordinates = Regex("(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
            .findAll(Regex("android:pathData=\"([^\"]+)\"").find(vector)!!.groupValues[1])
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()
        val left = coordinates.minOf { it.first }
        val right = coordinates.maxOf { it.first }
        val top = coordinates.minOf { it.second }
        val bottom = coordinates.maxOf { it.second }
        assertTrue(left >= 23.76 && right <= 84.24)
        assertTrue(top >= 23.76 && bottom <= 84.24)
        assertEquals(54.0, (left + right) / 2.0, 0.01)
        assertEquals(54.0, (top + bottom) / 2.0, 0.01)
        assertEquals(58.0, max(right - left, bottom - top), 0.01)

        coordinates.forEach { (x, y) ->
            assertTrue("circular mask must not clip a control point", circleContains(x, y))
            assertTrue("rounded-square mask must not clip a control point", roundedSquareContains(x, y))
        }
    }

    @Test fun legacyAdaptiveThemedAndNotificationVariantsShareTheMonochromeGlyph() {
        val legacy = source("src/main/res/mipmap-anydpi/ic_launcher.xml")
        val adaptive = source("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val themed = source("src/main/res/mipmap-anydpi-v33/ic_launcher.xml")
        val notification = source("src/main/kotlin/now/abhi/graycie/SnoozeNotificationManager.kt")
        listOf(legacy, adaptive, themed).forEach {
            assertTrue(it.contains("@color/graycie_launcher_background"))
            assertTrue(it.contains("@drawable/ic_graycie_a"))
        }
        assertTrue(themed.contains("<monochrome android:drawable=\"@drawable/ic_graycie_a\""))
        assertTrue(notification.contains("setSmallIcon(R.drawable.ic_graycie_a)"))
        assertTrue(source("src/main/res/values/colors.xml").contains(">#FFFFFF</color>"))
    }

    @Test fun obsoletePaintAndFrauncesAssetsAreAbsent() {
        val main = locate("src/main")
        val files = Files.walk(main).use { paths ->
            paths.filter(Files::isRegularFile).toList()
        }
        assertFalse(files.any { it.fileName.toString().contains("fraunces", ignoreCase = true) })
        assertFalse(files.any { it.fileName.toString().contains("paint", ignoreCase = true) })
        val kotlin = source("src/main/kotlin/now/abhi/graycie/ui/ManagerScreen.kt")
        assertFalse(kotlin.contains("PAINT_PATH"))
        assertFalse(kotlin.contains("GraycieMascot"))
    }

    private fun circleContains(x: Double, y: Double): Boolean =
        (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 54 * 54

    private fun roundedSquareContains(x: Double, y: Double, radius: Double = 23.0): Boolean {
        val insetX = when {
            x < radius -> radius - x
            x > 108 - radius -> x - (108 - radius)
            else -> 0.0
        }
        val insetY = when {
            y < radius -> radius - y
            y > 108 - radius -> y - (108 - radius)
            else -> 0.0
        }
        return insetX * insetX + insetY * insetY <= radius * radius
    }

    private fun source(relative: String): String = Files.readString(locate(relative))

    private fun locate(relative: String): Path = listOf(
        Path.of(relative),
        Path.of("app").resolve(relative),
    ).firstOrNull(Files::exists) ?: error("Could not locate $relative")
}
