package dev.partscanner.hid.barcode

import kotlin.math.PI
import kotlin.math.abs

class OneEuroFilter(
    private val minCutoff: Double = 1.3,
    private val beta: Double = 0.02,
    private val derivativeCutoff: Double = 1.0,
) {
    private var previousTimeMillis: Long? = null
    private var valueFilter: LowPassFilter? = null
    private var derivativeFilter: LowPassFilter? = null

    fun filter(value: Float, timeMillis: Long): Float {
        val previousTime = previousTimeMillis
        previousTimeMillis = timeMillis

        if (previousTime == null) {
            valueFilter = LowPassFilter(value.toDouble())
            derivativeFilter = LowPassFilter(0.0)
            return value
        }

        val dt = ((timeMillis - previousTime).coerceAtLeast(1)).toDouble() / 1000.0
        val previousValue = valueFilter?.last ?: value.toDouble()
        val derivative = (value - previousValue) / dt
        val filteredDerivative = derivativeFilter
            ?.filter(derivative, alpha(derivativeCutoff, dt))
            ?: derivative
        val cutoff = minCutoff + beta * abs(filteredDerivative)
        return (valueFilter?.filter(value.toDouble(), alpha(cutoff, dt)) ?: value.toDouble()).toFloat()
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private class LowPassFilter(initial: Double) {
        var last: Double = initial
            private set

        fun filter(value: Double, alpha: Double): Double {
            last = alpha * value + (1.0 - alpha) * last
            return last
        }
    }
}
