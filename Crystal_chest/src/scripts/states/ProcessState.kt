package scripts.states

import org.tribot.script.sdk.GameState.getSelectedItemName
import org.tribot.script.sdk.Inventory
import org.tribot.script.sdk.Inventory.getEmptySlots
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.pricing.Pricing.lookupPrice
import org.tribot.script.sdk.query.Query
import org.tribot.script.sdk.types.GroundItem
import org.tribot.script.sdk.types.InventoryItem
import scripts.*
import scripts.ABC2Settings.withABC2Delay
import scripts.Constants.CHEST
import scripts.Constants.itemIDsToPickup

class ProcessState : ScriptState {

    override fun performAction(script: Crystal_Chest) {
        println("Process state...")
        if (Inventory.contains("Crystal key") && getEmptySlots() > 2) {
            unlockChest(script)
        }
        attemptItemPickup()
    }

    private fun attemptItemPickup() {

        // Ensure there's at least one empty slot
        if (getEmptySlots() > 0) {
            val nearbyItems = Query.groundItems()
                .idEquals(*itemIDsToPickup.toIntArray())
                .filter { it.getTile().distanceTo(MyPlayer.getPosition()) <= 2 }
                .toList()

            nearbyItems.forEach { item ->
                // Try to pick up each item and retry if needed
                if (!pickupItemWithRetry(item, maxRetries = 3)) {
                    println("Failed to pick up item with ID ${item.id} after retries.")
                }
            }
        } else {
            println("No empty space in inventory for item pickup.")
        }
    }

    private fun pickupItemWithRetry(item: GroundItem, maxRetries: Int): Boolean {
        var attempts = 0
        while (attempts < maxRetries) {
            if (pickupItem(item)) {
                println("Successfully picked up item with ID ${item.id}.")
                return true
            } else {
                println("Failed to pick up item with ID ${item.id}. Retrying...")
                if (adjustCameraAndRetry(item)) {
                    println("Picked up item after camera adjustment, ID: ${item.id}.")
                    return true
                }
            }
            attempts++
        }
        return false
    }

    private fun pickupItem(item: GroundItem): Boolean {
        if (item.isVisible) {
            if (item.click("Take")) {
                return waitUntilItemDisappears(item)
            }
        }
        return false
    }

    private fun adjustCameraAndRetry(item: GroundItem): Boolean {
        if (item.adjustCameraTo()) {
            if (item.click("Take")) {
                return waitUntilItemDisappears(item)
            }
        }
        return false
    }

    private fun waitUntilItemDisappears(item: GroundItem): Boolean {
        return Waiting.waitUntil(3000) {
            !Query.groundItems()
                .idEquals(item.id)
                .filter { it.getTile() == item.getTile() }
                .isAny
        }
    }

    private fun unlockChest(script: Crystal_Chest) {
        val inventoryBefore = getCurrentInventory()

        if (Inventory.contains("Crystal key")) {
            withABC2Delay {
                if (getSelectedItemName() != "Crystal key") {
                    useItem("Crystal key")
                }
                val success = interactWithGameObjectById(CHEST, "Use")

                if (success) {
                    script.incrementSuccessfulUnlocks()
                    val inventoryAfter = getCurrentInventory()
                    val gainedItems = compareInventoryStates(inventoryBefore, inventoryAfter)
                    val profit = calculateProfit(gainedItems)

                    println("Total profit from items gained: $profit")
                    addProfit(profit)
                }
            }
        }
    }

    private fun addProfit(amount: Int) {
        Crystal_Chest.totalProfit += amount
    }

    private fun calculateProfit(gainedItems: List<InventoryItemInfo>): Int {
        var profit = 0
        gainedItems.forEach { item ->
            if (item.id == 995) {
                profit += item.quantity  // 995 is coins
            } else {
                val price = lookupPrice(item.id).orElse(0)
                profit += price * item.quantity
                println("Gained item: ID=${item.id}, Quantity=${item.quantity}, Value=${price * item.quantity}")
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
        // Get all unique item IDs in the inventory
        val uniqueItemIds = Inventory.getAll().map { it.id }.distinct()

        // For each unique item ID, get the total count of that item in the inventory
        return uniqueItemIds.map { id ->
            InventoryItemInfo(id, Inventory.getCount(id))
        }
    }

    private fun useItem(itemName: String?): Boolean {
        return Query.inventory()
            .nameEquals(itemName)
            .findClosestToMouse()
            .map(InventoryItem::ensureSelected)
            .orElse(false)
    }

    private fun interactWithGameObjectById(objectId: Int, interaction: String): Boolean {
        val gameObject = Query.gameObjects()
            .idEquals(objectId)
            .findClosestByPathDistance()

        return gameObject.map { obj ->
            println("Found game object with ID $objectId, interacting...")
            obj.interact(interaction)
            handleInteractionCompletion(objectId)
        }.orElse(false)
    }

    private fun handleInteractionCompletion(objectId: Int): Boolean {
        // Wait for player to start moving
        if (Waiting.waitUntil(1000) { MyPlayer.isMoving() }) {
            println("Player started moving towards object ID $objectId.")
            // Wait for the player to stop and check for animation completion
            if (waitForPlayerToStop()) {
                return checkForInteractionCompletion(objectId)
            }
        } else {
            println("Player didn't move; assuming immediate interaction with object ID $objectId.")
            return true
        }
        return false
    }

    private fun waitForPlayerToStop(): Boolean {
        return Waiting.waitUntil { !MyPlayer.isMoving() }
    }

    private fun checkForInteractionCompletion(objectId: Int): Boolean {
        return if (Waiting.waitUntil(1000) { MyPlayer.getAnimation() != -1 }) {
            Waiting.waitUntil { MyPlayer.getAnimation() == -1 }.also {
                if (it) println("Interaction with object ID $objectId completed.")
            }
        } else {
            println("No animation detected; assuming interaction was successful.")
            true
        }
    }
}
