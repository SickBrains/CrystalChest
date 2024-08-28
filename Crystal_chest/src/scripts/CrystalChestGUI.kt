package scripts

import javax.swing.*
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import kotlin.math.*

class CrystalChestGUI(
    private val onScriptStart: () -> Unit,
    private val onProfileLoad: (String) -> Unit,
    private val onProfileSave: (String) -> Unit,
    private val onUpdateAdvancedSettings: (Long, Long, Double) -> Unit,  // Callback for updating advanced settings
    private val loadSlidersFromProfile: (JSlider, JSlider, JSlider) -> Unit  // Callback to load sliders from the profile
) {

    private lateinit var gui: JFrame
    private lateinit var minSlider: JSlider
    private lateinit var maxSlider: JSlider
    private lateinit var sdSlider: JSlider
    private lateinit var profileNameField: JTextField

    var guiClosedPrematurely = false
    var scriptStarted = false

    fun createAndShowGUI() {
        gui = JFrame("Crystal Chest Setup")
        gui.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        gui.setSize(600, 400)
        gui.isResizable = true

        // Handle closing behavior
        gui.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                if (!scriptStarted) {
                    guiClosedPrematurely = true
                    println("GUI was closed prematurely, stopping script.")
                    gui.dispose()
                }
            }
        })

        // Create the tabbed pane
        val tabbedPane = JTabbedPane()

        // General Settings Panel
        val generalSettingsPanel = JPanel()
        generalSettingsPanel.layout = BoxLayout(generalSettingsPanel, BoxLayout.Y_AXIS)

        val abc2SpeedLabel = JLabel("Select ABC2 Waiting Speed:")
        generalSettingsPanel.add(abc2SpeedLabel)

        val quickButton = JRadioButton("Quick")
        val normalButton = JRadioButton("Normal", true)
        val slowButton = JRadioButton("Slow")
        val advancedUserCheckbox = JCheckBox("Use Advanced Settings")

        val speedGroup = ButtonGroup()
        speedGroup.add(quickButton)
        speedGroup.add(normalButton)
        speedGroup.add(slowButton)

        generalSettingsPanel.add(quickButton)
        generalSettingsPanel.add(normalButton)
        generalSettingsPanel.add(slowButton)
        generalSettingsPanel.add(advancedUserCheckbox)

        quickButton.addActionListener { ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.QUICK }
        normalButton.addActionListener { ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.NORMAL }
        slowButton.addActionListener { ABC2Settings.abc2Speed = ABC2Settings.ABC2Speed.SLOW }

        // Advanced Settings Panel for Bell Curve Visualization
        val advancedSettingsPanel = JPanel()
        advancedSettingsPanel.layout = BoxLayout(advancedSettingsPanel, BoxLayout.Y_AXIS)

        // Visualize button to show the bell curve
        val visualizeButton = JButton("Visualize Bell Curve")
        visualizeButton.alignmentX = Component.CENTER_ALIGNMENT
        advancedSettingsPanel.add(visualizeButton)
        advancedSettingsPanel.add(Box.createRigidArea(Dimension(0, 10)))

        // Create sliders for min, max, and standard deviation
        minSlider = JSlider(200, 10000, 450)  // Range: 200ms to 10,000ms
        maxSlider = JSlider(500, 15000, 1200) // Range: 500ms to 15,000ms
        sdSlider = JSlider(50, 500, 90)       // Range: 50ms to 500ms

        val minLabel = JLabel("Min Delay: ${minSlider.value} ms")
        val maxLabel = JLabel("Max Delay: ${maxSlider.value} ms")
        val sdLabel = JLabel("Standard Deviation: ${sdSlider.value} ms")

        minSlider.addChangeListener { minLabel.text = "Min Delay: ${minSlider.value} ms" }
        maxSlider.addChangeListener { maxLabel.text = "Max Delay: ${maxSlider.value} ms" }
        sdSlider.addChangeListener { sdLabel.text = "Standard Deviation: ${sdSlider.value} ms" }

        // Add sliders and labels to the panel
        advancedSettingsPanel.add(minLabel)
        advancedSettingsPanel.add(minSlider)
        advancedSettingsPanel.add(Box.createRigidArea(Dimension(0, 10)))

        advancedSettingsPanel.add(maxLabel)
        advancedSettingsPanel.add(maxSlider)
        advancedSettingsPanel.add(Box.createRigidArea(Dimension(0, 10)))

        advancedSettingsPanel.add(sdLabel)
        advancedSettingsPanel.add(sdSlider)
        advancedSettingsPanel.add(Box.createRigidArea(Dimension(0, 20)))

        // Update button for sliders
        val updateButton = JButton("Save Advanced Settings")
        updateButton.alignmentX = Component.CENTER_ALIGNMENT
        advancedSettingsPanel.add(updateButton)

        // Update advanced settings when the button is pressed
        updateButton.addActionListener {
            onUpdateAdvancedSettings(minSlider.value.toLong(), maxSlider.value.toLong(), sdSlider.value.toDouble())
            println("Advanced settings updated.")
        }

        // Action for Visualize Button
        visualizeButton.addActionListener {
            visualizeBellCurve(minSlider.value.toLong(), maxSlider.value.toLong(), sdSlider.value.toDouble())
        }

        // Initially disable the advanced settings tab
        tabbedPane.addTab("General", generalSettingsPanel)
        tabbedPane.addTab("Advanced", advancedSettingsPanel)
        tabbedPane.setEnabledAt(1, false)

        // Enable Advanced Tab when the checkbox is selected
        advancedUserCheckbox.addActionListener {
            val isChecked = advancedUserCheckbox.isSelected
            tabbedPane.setEnabledAt(1, isChecked)
            ABC2Settings.useAdvancedSettings = isChecked
        }

        // Other Settings Panel for Profile Management
        val otherSettingsPanel = JPanel()
        otherSettingsPanel.layout = BoxLayout(otherSettingsPanel, BoxLayout.Y_AXIS)

        val profileNameLabel = JLabel("Profile Name:")
        profileNameLabel.alignmentX = Component.CENTER_ALIGNMENT
        otherSettingsPanel.add(profileNameLabel)

        profileNameField = JTextField("default", 15)
        profileNameField.maximumSize = Dimension(200, 20)
        profileNameField.alignmentX = Component.CENTER_ALIGNMENT
        otherSettingsPanel.add(profileNameField)
        otherSettingsPanel.add(Box.createRigidArea(Dimension(0, 10)))

        val saveProfileButton = JButton("Save Profile")
        saveProfileButton.alignmentX = Component.CENTER_ALIGNMENT
        otherSettingsPanel.add(saveProfileButton)
        otherSettingsPanel.add(Box.createRigidArea(Dimension(0, 10)))

        val loadProfileButton = JButton("Load Profile")
        loadProfileButton.alignmentX = Component.CENTER_ALIGNMENT
        otherSettingsPanel.add(loadProfileButton)

        saveProfileButton.addActionListener {
            val profileName = profileNameField.text.trim()
            if (profileName.isNotEmpty()) {
                onProfileSave(profileName)
            }
        }

        loadProfileButton.addActionListener {
            val profileName = profileNameField.text.trim()
            if (profileName.isNotEmpty()) {
                onProfileLoad(profileName)

                // After loading the profile, update the sliders with loaded values
                loadSlidersFromProfile(minSlider, maxSlider, sdSlider)

                // Enable the advanced tab if advanced settings are being used
                advancedUserCheckbox.isSelected = true
                tabbedPane.setEnabledAt(1, true)
            }
        }

        tabbedPane.addTab("Other", otherSettingsPanel)

        // Start button
        val startButton = JButton("Start")
        startButton.alignmentX = Component.CENTER_ALIGNMENT
        startButton.addActionListener {
            scriptStarted = true
            onScriptStart.invoke()
            gui.dispose()
        }

        // Main panel layout
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.add(tabbedPane)
        mainPanel.add(Box.createRigidArea(Dimension(0, 10)))
        mainPanel.add(startButton)

        gui.contentPane.add(mainPanel)
        gui.setLocationRelativeTo(null)
        gui.isVisible = true
    }


    // Function to visualize the bell curve
    private fun visualizeBellCurve(min: Long, max: Long, sd: Double) {
        val series = XYSeries("Bell Curve")

        val mean = (min + max) / 2.0
        for (x in (min.toInt()..max.toInt())) {
            val probability = (1 / (sd * sqrt(2 * PI))) * exp(-0.5 * ((x - mean) / sd).pow(2))
            series.add(x.toDouble(), probability)
        }

        val dataset = XYSeriesCollection(series)
        val chart = ChartFactory.createXYLineChart(
            "Bell Curve", "Delay (ms)", "Probability", dataset, PlotOrientation.VERTICAL,
            true, true, false
        )

        // Create a chart panel to display the chart
        val chartPanel = ChartPanel(chart)
        val chartFrame = JFrame("Bell Curve Visualization")
        chartFrame.contentPane.add(chartPanel)
        chartFrame.pack()
        chartFrame.setLocationRelativeTo(null)
        chartFrame.isVisible = true
    }
}
