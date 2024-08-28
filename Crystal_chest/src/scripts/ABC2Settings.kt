package scripts

import org.tribot.script.sdk.Waiting
import kotlin.math.*
import kotlin.random.Random


object ABC2Settings {
    var abc2Speed = ABC2Speed.NORMAL // Default ABC2 speed
    var useAdvancedSettings = false // Flag to determine if advanced settings are used
    var customMin = 450L
    var customMax = 1200L
    var customSd = 90.0

    private var settingsPrinted = false // Flag to track if settings have been printed

    enum class ABC2Speed {
        QUICK, NORMAL, SLOW
    }

    // Modify this function to use either the advanced settings or the selected ABC2 speed
    fun withABC2Delay(action: () -> Unit) {
        val delay = if (useAdvancedSettings) {
            if (!settingsPrinted) {
                // Print settings once
                println("Using advanced settings: min=$customMin, max=$customMax, sd=$customSd")
                settingsPrinted = true
            }
            getNextActionDelay(customMin, customMax, customSd)
        } else {
            if (!settingsPrinted) {
                // Print the preset speed once
                println("Using preset speed: $abc2Speed")
                settingsPrinted = true
            }
            when (abc2Speed) {
                ABC2Speed.QUICK -> getNextActionDelay(200L, 350L)  // Quick (human reaction time)
                ABC2Speed.NORMAL -> getNextActionDelay(400L, 750L) // Normal (a bit slower)
                ABC2Speed.SLOW -> getNextActionDelay(450L, 1200L)  // Slow (original slow)
            }
        }

        println("Applying ABC2 delay of $delay ms")
        Waiting.wait(delay.toInt())
        action()
    }

    private fun getNextActionDelay(min: Long, max: Long, sd: Double = (max - min) / 10.0): Long {
        val mean = (min + max) / 2.0
        val u1 = 1.0 - Random.nextDouble()
        val u2 = 1.0 - Random.nextDouble()
        val rndStdNormal = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        val rndNormal = mean + sd * rndStdNormal
        return rndNormal.coerceIn(min.toDouble(), max.toDouble()).toLong()
    }
}
