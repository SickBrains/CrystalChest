package scripts

import dax.api_lib.DaxWalker
import dax.teleports.Teleport
import org.tribot.api2007.Player
import org.tribot.api2007.Skills
import org.tribot.api2007.types.RSArea
import org.tribot.api2007.types.RSTile
import org.tribot.script.sdk.*
import org.tribot.script.sdk.Log.warn
import org.tribot.script.sdk.painting.Painting
import org.tribot.script.sdk.pricing.Pricing
import org.tribot.script.sdk.script.ScriptConfig
import org.tribot.script.sdk.script.TribotScript
import org.tribot.script.sdk.script.TribotScriptManifest
import org.tribot.script.sdk.walking.GlobalWalking
import org.tribot.script.sdk.walking.adapter.DaxWalkerAdapter
import scripts.states.BankingState
import scripts.states.ProcessState
import scripts.states.WalkingState
import java.awt.Graphics
import javax.swing.*

val chestLocation = listOf(
    RSArea(RSTile(2915, 3451, 0), RSTile(2913, 3449, 0)),
    RSArea(RSTile(2907, 3450, 0), RSTile(2913, 3449, 0))
)

@TribotScriptManifest(
    name = "Crystal Chest",
    author = "SickBrains",
    category = "Money Making",
    description = "Opens Crystal Chests in Taverley, you will have to buy the keys and dueling rings yourself."
)
class Crystal_Chest : TribotScript {
    private val engine = DaxWalkerAdapter("sub_1PYBe0A7n2uRXzFbDyHVolZt", "c5e6ece2-8104-48f1-bba8-daeca7c5184f")

    private var currentState: ScriptState = BankingState()
    private var successfulUnlocks = 0
    private val startTime = System.currentTimeMillis()
    private var netProfit = 0
    private var profitPerHour = 0
    var isCombiningKeys = false
    val keyPrice = Pricing.lookupPrice(990).orElse(20500)

    // PaintClass instance
    private val paint = PaintClass(startTime)
    private val profileManager = ProfileManager()

    companion object {
        var totalProfit = 0
    }

    override fun configure(config: ScriptConfig) {
        config.isRandomsAndLoginHandlerEnabled = true
        config.isBreakHandlerEnabled = true
    }

    private var scriptStarted = false

    override fun execute(args: String) {
        println("Script starting")

        if (!Waiting.waitUntil(25000) { GameState.getState() == GameState.State.LOGGED_IN }) {
            println("Failed to log in within 25 seconds, stopping script.")
            return
        }

        if (!startCheck()) {
            println("Construction level is too low, stopping script.")
            warn("Construction level is too low, stopping script.")
            throw Exception("Construction level is too low, make sure to also set your house in Taverley.")
        }

        if (GameState.getVarbit(2187) != 2) {
            println("House is not set in Taverley.")
            warn("House is not set in Taverley.")
            throw Exception("Make sure your house is set in Taverley.")
        }

        Painting.addPaint { g: Graphics ->
            paint.paint(g, currentState, netProfit, profitPerHour, successfulUnlocks)
        }

        profileManager.handleProfileArguments(args, { /* Profile loaded */ }, { showProfileGui() })

        GlobalWalking.setEngine(engine)

        DaxWalker.blacklistTeleports(
            *Teleport.values()
                .asIterable()
                .minus(Teleport.RING_OF_DUELING_CASTLE_WARS)
                .minus(Teleport.POH_OUTSIDE_TAVERLY_TAB)
                .toTypedArray()
        )

        while (true) {
            updateState()
            currentState.performAction(this)
            Waiting.wait(1000)
        }
    }

    private fun updateState() {
        when {
            shouldProcess() -> changeState(ProcessState())
            shouldBank() -> changeState(BankingState())
            shouldWalk() -> changeState(WalkingState())
        }
    }

    private fun shouldProcess(): Boolean {
        return checkKeys() && !checkTeleport() && checkNearChest()
    }

    private fun shouldBank(): Boolean {
        return (!checkKeys() || checkTeleport()) && !isCombiningKeys
    }

    private fun shouldWalk(): Boolean {
        return checkKeys() && !checkTeleport() && !checkNearChest() && !isCombiningKeys
    }

    fun changeState(newState: ScriptState) {
        currentState = newState
        println("Changing state to ${newState.javaClass.simpleName}")
    }

    fun incrementSuccessfulUnlocks() {
        successfulUnlocks++
        updatePrecomputedValues()
    }

    private fun updatePrecomputedValues() {
        netProfit = totalProfit - (successfulUnlocks * keyPrice)
        profitPerHour = calculateProfitPerHour(netProfit)
    }

    private fun calculateProfitPerHour(netProfit: Int): Int {
        val elapsedTimeHours = (System.currentTimeMillis() - startTime) / (1000.0 * 60 * 60)
        return if (elapsedTimeHours > 0) (netProfit / elapsedTimeHours).toInt() else 0
    }

    fun startCheck(): Boolean {
        return Skills.getCurrentLevel(Skills.SKILLS.CONSTRUCTION) > 15
    }

    private fun checkKeys(): Boolean {
        val keyCount = Inventory.getCount(989)
        return keyCount > 0 && Inventory.getEmptySlots() > 3
    }

    private fun checkTeleport(): Boolean {
        val teleportCount = Inventory.getCount("Teleport to house")
        return teleportCount < 1
    }

    private fun checkNearChest(): Boolean {
        return chestLocation.any { it.contains(Player.getPosition()) }
    }

    private fun showProfileGui() {
        val gui = CrystalChestGUI(
            onScriptStart = { scriptStarted = true },
            onProfileLoad = { profileName -> profileManager.loadProfile(profileName) },
            onProfileSave = { profileName -> profileManager.saveProfile(profileName) },
            onUpdateAdvancedSettings = { min, max, sd ->
                ABC2Settings.customMin = min
                ABC2Settings.customMax = max
                ABC2Settings.customSd = sd
                ABC2Settings.useAdvancedSettings = true
            },
            loadSlidersFromProfile = { minSlider, maxSlider, sdSlider ->
                minSlider.value = ABC2Settings.customMin.toInt()
                maxSlider.value = ABC2Settings.customMax.toInt()
                sdSlider.value = ABC2Settings.customSd.toInt()
            }
        )

        SwingUtilities.invokeLater { gui.createAndShowGUI() }

        while (!scriptStarted) {
            if (gui.guiClosedPrematurely) {
                println("GUI was closed prematurely, stopping script.")
                return
            }
            Thread.sleep(100)
        }
    }
}
