package dex.cosmetics.render

import kotlin.math.cos
import kotlin.math.sin

/** Emits exactly count points; small budgets never expand to an implicit minimum shape size. */
internal object ParticleShapes {
    fun outline(square: Boolean, count: Int, radius: Double): List<Pair<Double, Double>> =
        (0 until count).map { i ->
            if (!square) {
                val angle = 2 * Math.PI * i / count
                radius * cos(angle) to radius * sin(angle)
            } else {
                val position = 4.0 * i / count
                val fraction = position % 1.0
                when (position.toInt()) {
                    0 -> radius * (2 * fraction - 1) to -radius
                    1 -> radius to radius * (2 * fraction - 1)
                    2 -> radius * (1 - 2 * fraction) to radius
                    else -> -radius to radius * (1 - 2 * fraction)
                }
            }
        }
}
