package net.craftgame.metro.ride

import net.craftgame.metro.geo.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SteeringTest {
    @Test
    fun aimsAtLeastTheMinimumAheadAndAtLeastOneTickOfTravel() {
        assertEquals(Steering.MIN_LOOKAHEAD, Steering.lookahead(0.0))
        assertEquals(Steering.MIN_LOOKAHEAD, Steering.lookahead(0.5))
        assertEquals(1.2, Steering.lookahead(1.2))
    }

    @Test
    fun returnsNullWhenAlreadyAtTheTarget() {
        assertNull(Steering.steer(Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), speed = 0.5, cruise = 0.6))
    }

    @Test
    fun flatGroundKeepsCruise() {
        val result = Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), speed = 0.5, cruise = 0.6)
        assertNotNull(result)
        assertEquals(0.5 / Steering.PASSENGER_SCALE, result.velocity.x, 1e-9)
        assertEquals(0.0, result.velocity.y, 1e-9)
        assertEquals(0.6, result.maxSpeed, 1e-9)
    }

    @Test
    fun horizontalSpeedCompensatesThePassengerScale() {
        val result = Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(3.0, 0.0, 4.0), speed = 0.8, cruise = 0.8)
        assertNotNull(result)
        val horizontal = Math.hypot(result.velocity.x, result.velocity.z)
        assertEquals(0.8 / Steering.PASSENGER_SCALE, horizontal, 1e-9)
        assertEquals(0.8, horizontal * Steering.PASSENGER_SCALE, 1e-9)
    }

    @Test
    fun verticalComponentIsNotScaled() {
        val result = Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 0.0), speed = 0.6, cruise = 0.6)
        assertNotNull(result)
        val component = 0.6 / Math.sqrt(2.0)
        assertEquals(component, result.velocity.y, 1e-9)
        assertEquals(component / Steering.PASSENGER_SCALE, result.velocity.x, 1e-9)
    }

    @Test
    fun capLetsTheScaledMovementThrough() {
        val cases =
            listOf(
                Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0), speed = 0.8, cruise = 0.6),
                Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 1.0), speed = 0.6, cruise = 0.6),
                Steering.steer(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), speed = 0.6, cruise = 0.6),
                Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 0.0), speed = 0.6, cruise = 0.6),
            )
        for (result in cases) {
            assertNotNull(result)
            val v = result.velocity
            // Vanilla turns the horizontal velocity along the rail axis, so one axis may carry all of it.
            val railAxis = Steering.PASSENGER_SCALE * Math.hypot(v.x, v.z)
            assertTrue(railAxis <= result.maxSpeed + 1e-9, "scaled $railAxis exceeds cap ${result.maxSpeed}")
        }
    }

    @Test
    fun descentIsNotPushedDownward() {
        val result = Steering.steer(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), speed = 0.6, cruise = 0.6)
        assertNotNull(result)
        assertEquals(0.0, result.velocity.y, 1e-9)
        assertEquals(0.6 / Steering.PASSENGER_SCALE, result.velocity.x, 1e-9)
        // 0.6 / cos(45 deg) keeps the horizontal pace while the rail carries the drop.
        assertEquals(0.6 / Math.sqrt(0.5), result.maxSpeed, 1e-6)
    }

    @Test
    fun ascentKeepsAnUpwardComponent() {
        val result = Steering.steer(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 0.0), speed = 0.6, cruise = 0.6)
        assertNotNull(result)
        assertTrue(result.velocity.y > 0.0)
        assertEquals(0.6, result.maxSpeed, 1e-9)
    }
}
