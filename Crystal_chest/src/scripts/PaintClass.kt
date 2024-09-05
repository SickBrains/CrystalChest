package scripts

import java.awt.Graphics
import java.util.concurrent.TimeUnit

class PaintClass(
    private val startTime: Long
) {
    fun paint(g: Graphics, currentState: ScriptState, netProfit: Int, profitPerHour: Int, successfulUnlocks: Int) {
        val runtimeMillis = System.currentTimeMillis() - startTime
        val runtimeString = formatRuntime(runtimeMillis)

        g.drawString("Current state: ${currentState.javaClass.simpleName}", 20, 50)
        g.drawString("Script runtime: $runtimeString", 20, 60)
        g.drawString("Unlocks: $successfulUnlocks", 20, 70)
        g.drawString("Net Profit: ${formatLargeNumber(netProfit)}", 20, 80)
        g.drawString("Profit per hour: ${formatLargeNumber(profitPerHour)}", 20, 90)
    }

    private fun formatRuntime(runtimeMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(runtimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(runtimeMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(runtimeMillis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatLargeNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fk", number / 1_000.0)
            else -> number.toString()
        }
    }
}
