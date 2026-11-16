package dex.cosmetics

import dex.cosmetics.polymer.ModelIds
import dex.cosmetics.render.ParticleShapes
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModelAndParticleTest {
    @Test
    fun `existing models keep ids when more models are added and carriers are independent`() {
        val ids = ModelIds(2000)
        assertEquals(2000, ids.allocate("leather", "hat"))
        assertEquals(2001, ids.allocate("leather", "back"))
        assertEquals(2000, ids.allocate("paper", "menu"))
        repeat(10) { assertEquals(2000, ids.allocate("leather", "hat")) }
        assertEquals(2002, ids.allocate("leather", "new"))
        assertEquals(2001, ids.allocate("leather", "back"))
    }

    @Test
    fun `shape emission respects small and zero budgets and stays on the outline`() {
        for (square in listOf(false, true)) {
            for (budget in 0..32) {
                val points = ParticleShapes.outline(square, budget, 2.0)
                assertEquals(budget, points.size)
                for ((x, z) in points) {
                    if (square) assertTrue(abs(abs(x) - 2) < 1e-6 || abs(abs(z) - 2) < 1e-6)
                    else assertEquals(4.0, x * x + z * z, 1e-6)
                }
            }
        }
    }
}
