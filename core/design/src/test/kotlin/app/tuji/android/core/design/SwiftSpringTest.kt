package app.tuji.android.core.design

import androidx.compose.animation.core.Spring
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SwiftUI → Compose spring conversion, which is the one place this app
 * translates a number rather than copying it.
 *
 * Every case here is an actual spring in the iOS source, quoted by file, so a
 * drift on that side shows up as a failure here rather than as a difference
 * nobody notices.
 */
class SwiftSpringTest {

    @Test fun `duration sets stiffness as the square of the angular frequency`() {
        // TujiBrandLockup: .spring(duration: 0.34, bounce: 0.28)
        val expected = (2.0 * PI / 0.34).let { (it * it).toFloat() }
        assertEquals(expected, SwiftSpring.stiffness(0.34f), 0.5f)
    }

    @Test fun `bounce is the damping ratio read backwards`() {
        assertEquals(0.72f, SwiftSpring.dampingRatio(0.28f), 1e-4f)
        // TujiStatusToast: .spring(duration: 0.24, bounce: 0.12)
        assertEquals(0.88f, SwiftSpring.dampingRatio(0.12f), 1e-4f)
        // MainTabsView's tour: .spring(duration: 0.32, bounce: 0.16)
        assertEquals(0.84f, SwiftSpring.dampingRatio(0.16f), 1e-4f)
    }

    @Test fun `no bounce means critically damped, not bouncy`() {
        // Five of iOS's eight springs are written `.spring(duration:)` with no
        // bounce at all. They must not overshoot.
        assertEquals(Spring.DampingRatioNoBouncy, SwiftSpring.dampingRatio(0f), 1e-4f)
    }

    @Test fun `a shorter duration is a stiffer spring`() {
        // Ordering is the property a reader can check by eye; the constant is
        // only meaningful against SwiftUI's own formula.
        val quick = SwiftSpring.stiffness(0.24f)
        val slow = SwiftSpring.stiffness(0.45f)
        assert(quick > slow) { "0.24s should be stiffer than 0.45s, got $quick vs $slow" }
    }

    @Test fun `damping ratio never reaches zero however wrong the bounce`() {
        // A bounce of 1 would be an undamped spring: it never settles, so the
        // animation never ends and whatever it drives never becomes tappable.
        assert(SwiftSpring.dampingRatio(1f) > 0f)
        assert(SwiftSpring.dampingRatio(2f) > 0f)
    }
}
