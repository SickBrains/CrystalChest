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
import java.awt.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
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
    // Dont steal
    private val engine = DaxWalkerAdapter(PUBLIC_KEY, SECRET_KEY)

    private var currentState: ScriptState = BankingState()
    private var successfulUnlocks = 0
    private val startTime = System.currentTimeMillis()
    var isCombiningKeys = false


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

        // Handle arguments or show GUI
        when (args.lowercase(Locale.getDefault())) {
            "quick" -> {
                ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.QUICK
                println("Setting ABC2 speed to QUICK based on argument.")
            }
            "normal" -> {
                ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.NORMAL
                println("Setting ABC2 speed to NORMAL based on argument.")
            }
            "slow" -> {
                ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.SLOW
                println("Setting ABC2 speed to SLOW based on argument.")
            }
            else -> {
                if (args.isNotEmpty()) {
                    println("Loading profile: $args")
                    loadProfile(args.trim())
                } else {
                    // Show GUI if no valid argument is passed
                    val gui = CrystalChestGUI(
                        onScriptStart = { scriptStarted = true },
                        onProfileLoad = { profileName -> loadProfile(profileName) },
                        onProfileSave = { profileName -> saveProfile(profileName) },
                        onUpdateAdvancedSettings = { min, max, sd ->
                            ABC2Settings.customMin = min
                            ABC2Settings.customMax = max
                            ABC2Settings.customSd = sd
                            ABC2Settings.useAdvancedSettings = true
                        },
                        loadSlidersFromProfile = { minSlider, maxSlider, sdSlider ->
                            // Update sliders with the loaded profile values
                            minSlider.value = ABC2Settings.customMin.toInt()
                            maxSlider.value = ABC2Settings.customMax.toInt()
                            sdSlider.value = ABC2Settings.customSd.toInt()
                        }
                    )

                    SwingUtilities.invokeLater { gui.createAndShowGUI() }
                    // Wait for the GUI to finish or close
                    while (!scriptStarted) {
                        if (gui.guiClosedPrematurely) {
                            println("GUI was closed prematurely, stopping script.")
                            return
                        }
                        Thread.sleep(100)
                    }
                }
            }
        }

        // Proceed with the script logic only after the profile has been loaded or GUI "Start" is pressed
        GlobalWalking.setEngine(engine)
        Painting.addPaint { g: Graphics -> paint(g) }

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



    private fun paint(g: Graphics) {
        val runtimeMillis = System.currentTimeMillis() - startTime
        val runtimeString = formatRuntime(runtimeMillis)

        // Fetch key price
        val keyPrice = Pricing.lookupPrice(990).orElse(20500)

        // Calculate total cost based on keys used
        val cost = successfulUnlocks * keyPrice

        // Calculate net profit
        val netProfit = totalProfit - cost

        // Format the numbers using the utility function
        val totalProfitFormatted = formatLargeNumber(totalProfit)
        val costFormatted = formatLargeNumber(cost)
        val netProfitFormatted = formatLargeNumber(netProfit)

        // Calculate profit per hour
        val profitPerHour = calculateProfitPerHour(netProfit)
        val profitPerHourFormatted = formatLargeNumber(profitPerHour.toInt())

        // Drawing to the screen
        g.drawString("Current state: ${currentState.javaClass.simpleName}", 20, 50)
        g.drawString("Script runtime: $runtimeString", 20, 60)
        g.drawString("Unlocks: $successfulUnlocks (${String.format("%.2f", calculateSuccessesPerHour())} p/h)", 20, 70)
        g.drawString("Revenue: $totalProfitFormatted", 20, 90)
        g.drawString("Cost: $costFormatted", 20, 100)
        g.drawString("Profit: $netProfitFormatted (${profitPerHourFormatted} p/h)", 20, 110)
    }

    private fun calculateProfitPerHour(netProfit: Int): Double {
        val elapsedTimeHours = (System.currentTimeMillis() - startTime) / (1000.0 * 60 * 60)
        return if (elapsedTimeHours > 0) netProfit / elapsedTimeHours else 0.0
    }

    // Format large numbers as XXXk or XM
    private fun formatLargeNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fk", number / 1_000.0)
            else -> number.toString()
        }
    }
    private fun formatRuntime(runtimeMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(runtimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(runtimeMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(runtimeMillis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }


    private fun calculateSuccessesPerHour(): Double {
        val elapsedTimeHours = (System.currentTimeMillis() - startTime) / (1000.0 * 60 * 60)
        return if (elapsedTimeHours > 0) successfulUnlocks / elapsedTimeHours else 0.0
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

    fun incrementSuccessfulUnlocks() {
        successfulUnlocks++
    }

}
fun setupProfileDirectory(): File? {
    // Get the Tribot directory
    val tribotDir = Tribot.getDirectory() // This gets the TRiBot directory

    // Create SickBrains Scripts directory inside the Tribot directory
    val sickBrainsDir = File(tribotDir, "SickBrains Scripts")
    if (!sickBrainsDir.exists()) {
        val dirCreated = sickBrainsDir.mkdir()
        if (!dirCreated) {
            println("Failed to create 'SickBrains Scripts' directory")
            return null
        }
        println("Directory 'SickBrains Scripts' created at: ${sickBrainsDir.absolutePath}")
    }

    // Create Crystal Chest directory inside SickBrains Scripts directory
    val crystalChestDir = File(sickBrainsDir, "Crystal Chest")
    if (!crystalChestDir.exists()) {
        val dirCreated = crystalChestDir.mkdir()
        if (!dirCreated) {
            println("Failed to create 'Crystal Chest' directory")
            return null
        }
        println("Directory 'Crystal Chest' created at: ${crystalChestDir.absolutePath}")
    }

    return crystalChestDir
}

fun saveProfile(profileName: String) {
    val profileDir = setupProfileDirectory() ?: return
    val profileFile = File(profileDir, "$profileName.properties")

    val properties = Properties().apply {
        put("min", ABC2Settings.customMin.toString())
        put("max", ABC2Settings.customMax.toString())
        put("sd", ABC2Settings.customSd.toString())
    }

    try {
        FileOutputStream(profileFile).use { outputStream ->
            properties.store(outputStream, "Crystal Chest Profile: $profileName")
        }
        println("Profile saved successfully: ${profileFile.absolutePath}")
    } catch (e: IOException) {
        e.printStackTrace()
        println("Failed to save profile: ${e.message}")
    }
}

fun loadProfile(
    profileName: String,
    advancedUserCheckbox: JCheckBox? = null,
    tabbedPane: JTabbedPane? = null,
    minSlider: JSlider? = null,
    maxSlider: JSlider? = null,
    sdSlider: JSlider? = null
) {
    val profileDir = setupProfileDirectory() ?: return
    val profileFile = File(profileDir, "$profileName.properties")

    if (!profileFile.exists()) {
        println("Profile not found: ${profileFile.absolutePath}")
        return
    }

    val properties = Properties()
    try {
        FileInputStream(profileFile).use { inputStream ->
            properties.load(inputStream)
        }

        // Load values into ABC2Settings
        ABC2Settings.customMin = properties.getProperty("min")?.toLong() ?: 450L
        ABC2Settings.customMax = properties.getProperty("max")?.toLong() ?: 1200L
        ABC2Settings.customSd = properties.getProperty("sd")?.toDouble() ?: 90.0

        // Ensure that advanced settings are used
        ABC2Settings.useAdvancedSettings = true

        // Update sliders if they are not null (when using the GUI)
        minSlider?.value = ABC2Settings.customMin.toInt()
        maxSlider?.value = ABC2Settings.customMax.toInt()
        sdSlider?.value = ABC2Settings.customSd.toInt()

        // Enable advanced settings in the GUI, if the GUI components are provided
        if (advancedUserCheckbox != null && tabbedPane != null) {
            advancedUserCheckbox.isSelected = true
            tabbedPane.setEnabledAt(1, true) // Enable the Advanced tab
        }

        println("Profile loaded successfully: $profileName")
    } catch (e: IOException) {
        e.printStackTrace()
        println("Failed to load profile: ${e.message}")
    }
}


