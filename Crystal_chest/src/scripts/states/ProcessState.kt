package scripts.states

import org.tribot.script.sdk.GameState.getSelectedItemName
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Inventory.getEmptySlots
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.Waiting.waitUniform
import org.tribot.script.sdk.pricing.Pricing.lookupPrice
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.GroundItem
import org.tribot.script.sdk.types.InventoryItem
import scripts.*
import scripts.ABC2Settings.withABC2Delay


// This isnt refined, was my first time working with tribot sdk
class ProcessState : ScriptState {
    override fun performAction(script: Crystal_Chest) {
        println("Process state...")
        if (Inventory.contains("Crystal key") && getEmptySlots() > 2) {
            unlockChest(script)
        }
        attemptItemPickup()
    }

    private fun attemptItemPickup() {
        // Will only pick up items with these IDs (that have value)
        val itemIDsToPickup = listOf(1631, 995, 1603, 1601, 2363, 985, 987, 454, 441, 1079, 1093)

        // Check if there is at least one empty inventory slot
        if (getEmptySlots() > 0) {
            val nearbyItems = Query.groundItems()
                .idEquals(*itemIDsToPickup.toIntArray())
                .filter { it.getTile().distanceTo(MyPlayer.getPosition()) <= 2 }
                .toList()

            nearbyItems.forEach { item ->
                // Try to pick up each item with proper visibility and interaction checks
                if (pickupItem(item)) {
                    println("Successfully picked up item with ID ${item.id}.")
                } else {
                    println("Failed to pick up item with ID ${item.id}. Retrying...")
                    if (adjustCameraAndRetry(item)) {
                        println("Picked up item after camera adjustment, ID: ${item.id}.")
                    } else {
                        println("Failed to pick up item with ID ${item.id} after retry.")
                    }
                }
            }
        } else {
            println("No empty space in inventory for item pickup.")
        }
    }
    private fun pickupItem(item: GroundItem): Boolean {
        // First attempt to pick up the item if it's visible
        if (item.isVisible) {
            if (item.click("Take")) {
                // Small wait to ensure the item is picked up and removed from the ground
                return waitUntilItemDisappears(item)
            }
        }
        return false
    }
    private fun adjustCameraAndRetry(item: GroundItem): Boolean {
        // Attempt to adjust the camera to make the item visible and retry pickup
        if (item.adjustCameraTo()) {
            if (item.click("Take")) {
                return waitUntilItemDisappears(item)
            }
        }
        return false
    }
    private fun waitUntilItemDisappears(item: GroundItem): Boolean {
        // Wait until the item is no longer present in the game world
        return Waiting.waitUntil(3000) {
            !Query.groundItems()
                .idEquals(item.id)
                .filter { it.getTile() == item.getTile() }
                .isAny
        }
    }
    fun unlockChest(script: Crystal_Chest) {
        val inventoryBefore = getCurrentInventory()

        if (Inventory.contains("Crystal key")) {
            withABC2Delay {
                if (getSelectedItemName() != "Crystal key") {
                    useItem("Crystal key")
                }
                val success = interactWithGameObjectById(172, "Use")

                if (success) {
                    script.incrementSuccessfulUnlocks()
                    waitUniform(250, 500)
                    val inventoryAfter = getCurrentInventory()
                    val gainedItems = compareInventoryStates(inventoryBefore, inventoryAfter)
                    val profit = calculateProfit(gainedItems)

                    println("Total profit from items gained: $profit")
                    addProfit(profit)
                }
            }
        }
    }

    fun addProfit(amount: Int) {
        Crystal_Chest.totalProfit += amount
    }
    private fun calculateProfit(gainedItems: List<InventoryItemInfo>): Int {
        var profit = 0
        gainedItems.forEach { item ->
            if (item.id == 995) {
                profit += item.quantity
            } else {
                val price = lookupPrice(item.id).orElse(0)
                val itemValue = price * item.quantity
                profit += itemValue
                println("Gained item: ID=${item.id}, Quantity=${item.quantity}, Value=$itemValue")
            }
        }
        return profit
    }
    private fun compareInventoryStates(
        before: List<InventoryItemInfo>,
        after: List<InventoryItemInfo>
    ): List<InventoryItemInfo> {
        val beforeMap = before.associateBy { it.id }
        val gainedItems = mutableListOf<InventoryItemInfo>()

        after.forEach { item ->
            val beforeQuantity = beforeMap[item.id]?.quantity ?: 0
            if (item.quantity > beforeQuantity) {
                gainedItems.add(InventoryItemInfo(item.id, item.quantity - beforeQuantity))
            }
        }
        return gainedItems
    }

    data class InventoryItemInfo(val id: Int, val quantity: Int)

    private fun getCurrentInventory(): List<InventoryItemInfo> {
        val inventoryItems = Inventory.getAll()
        val itemCounts = inventoryItems.groupBy { it.id }.mapValues { entry ->
            Inventory.getCount(entry.key)
        }
        return itemCounts.map { (id, count) -> InventoryItemInfo(id, count) }
    }

    fun useItem(itemName: String?): Boolean {
        return Query.inventory()
            .nameStartsWith(itemName)
            .findClosestToMouse()
            .map(InventoryItem::ensureSelected)
            .orElse(false)
    }

    fun interactWithGameObjectById(objectId: Int, interaction: String, justInteract: Boolean = false): Boolean {

        val success = Query.gameObjects()
            .idEquals(objectId)
            .findClosestByPathDistance()
            .map { gameObject ->
                println("Game object with ID $objectId found, attempting to interact.")
                gameObject.interact(interaction)
            }
            .orElse(false)

        if (success) {
            return handleInteractionSuccess(objectId, justInteract)
        } else {
            println("Failed to initiate interaction with object ID $objectId.")
        }

        return false
    }

    fun handleInteractionSuccess(objectId: Int, justInteract: Boolean): Boolean {
        println("Successfully initiated interaction with object ID $objectId.")
        if (justInteract) return true

        Waiting.wait(50)

        val playerStartedMoving = Waiting.waitUntil(1000) { MyPlayer.isMoving() }
        if (playerStartedMoving) {
            println("Player is moving towards the object.")
            if (waitForPlayerToStop()) {
                return checkForInteractionCompletion(objectId)
            }
        } else {
            println("Player did not start moving; assuming immediate interaction was successful.")
            return true
        }

        return false
    }

    fun waitForPlayerToStop(): Boolean {
        return Waiting.waitUntil { !MyPlayer.isMoving() }.also {
            if (!it) println("Player is still moving after waiting for 50 milliseconds.")
        }
    }
    fun checkForInteractionCompletion(objectId: Int): Boolean {
        val animationStarted = Waiting.waitUntil(1000) { MyPlayer.getAnimation() != -1 }
        if (animationStarted) {
            return Waiting.waitUntil { MyPlayer.getAnimation() == -1 }.also {
                if (it) {
                    println("Interaction with object ID $objectId completed successfully.")
                } else {
                    println("Failed to confirm interaction completion within the expected timeframe.")
                }
            }
        } else {
            println("No animation detected; assuming interaction was successful.")
            return true
        }
    }
}