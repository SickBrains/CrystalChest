package scripts

import org.tribot.script.sdk.Tribot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.swing.JCheckBox
import javax.swing.JSlider
import javax.swing.JTabbedPane

class ProfileManager {

    fun setupProfileDirectory(): File? {
        val tribotDir = Tribot.getDirectory()

        val sickBrainsDir = File(tribotDir, "SickBrains Scripts")
        if (!sickBrainsDir.exists()) {
            val dirCreated = sickBrainsDir.mkdir()
            if (!dirCreated) {
                println("Failed to create 'SickBrains Scripts' directory")
                return null
            }
            println("Directory 'SickBrains Scripts' created at: ${sickBrainsDir.absolutePath}")
        }

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

            ABC2Settings.customMin = properties.getProperty("min")?.toLong() ?: 450L
            ABC2Settings.customMax = properties.getProperty("max")?.toLong() ?: 1200L
            ABC2Settings.customSd = properties.getProperty("sd")?.toDouble() ?: 90.0
            ABC2Settings.useAdvancedSettings = true

            minSlider?.value = ABC2Settings.customMin.toInt()
            maxSlider?.value = ABC2Settings.customMax.toInt()
            sdSlider?.value = ABC2Settings.customSd.toInt()

            if (advancedUserCheckbox != null && tabbedPane != null) {
                advancedUserCheckbox.isSelected = true
                tabbedPane.setEnabledAt(1, true)
            }

            println("Profile loaded successfully: $profileName")
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to load profile: ${e.message}")
        }
    }

    fun handleProfileArguments(
        args: String,
        onProfileLoaded: () -> Unit,
        showProfileGui: () -> Unit
    ) {
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
                    onProfileLoaded()
                } else {
                    showProfileGui()
                }
            }
        }
    }
}
