package scripts.states

import org.tribot.script.sdk.*
import org.tribot.script.sdk.Log.info
import org.tribot.script.sdk.types.Area
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.walking.GlobalWalking
import scripts.ABC2Settings.withABC2Delay
import scripts.Constants.CRYSTAL_KEY
import scripts.Constants.LOOP_HALF
import scripts.Constants.TELEPORT_TO_HOUSE
import scripts.Constants.TOOTH_HALF
import scripts.Constants.ringIds
import scripts.Crystal_Chest
import scripts.ScriptState
import java.util.Random




class BankingState : ScriptState {

    override fun performAction(script: Crystal_Chest) {
        val random = Random()
        info("Banking...")

        if (!Bank.isNearby()) {
            GlobalWalking.walkToBank()
            return
        }

        if (!Bank.isOpen()) {
            openBankWithDelay()
            return
        }

        // Check if we should combine keys
        if (!script.isCombiningKeys && hasKeyParts()) {
            if (shouldCombineKeys(Bank.getCount(CRYSTAL_KEY), random)) {
                script.isCombiningKeys = true
                return  // Stop further actions until keys are combined
            }
        }
        if (!depositInventoryWithDelay()) return
        ensureRingEquipped()

        withdrawRetry(TELEPORT_TO_HOUSE, "Teleport to house", random, maxRetries = 3)
        withdrawRetry(CRYSTAL_KEY, "Crystal key", random, maxRetries = 3)

        closeBankWithDelay()
    }
    
    private fun openBankWithDelay() {
        withABC2Delay { Bank.ensureOpen() }
        Waiting.waitUntil(10000, 1000) { Bank.isOpen() }
    }
    
    private fun closeBankWithDelay() {
        withABC2Delay { Bank.close() }
    }

    private fun withdrawRetry(itemId: Int, itemName: String, random: Random, maxRetries: Int) {
        val withdrawAmount = if (itemName == "Teleport to house") {
            weightedRandomChoice(listOf(80 to 6, 5 to 5, 15 to 2))
        } else {
            weightedRandomChoice(listOf(80 to 6, 15 to 5, 5 to 4))
        }

        var success = false
        var attempts = 0

        while (attempts < maxRetries && !success) {
            info("Attempting to withdraw $withdrawAmount of $itemName (attempt ${attempts + 1}/$maxRetries).")
            success = withdrawCheck(itemId, withdrawAmount, itemName)
            if (!success) {
                info("Failed to withdraw $itemName. Retrying...")
                attempts++
            }
        }

        if (!success) {
            error("Failed to withdraw $itemName after $maxRetries attempts. Skipping this item.")
        } else {
            info("Successfully withdrew $withdrawAmount of $itemName after $attempts attempt(s).")
        }
    }

    private fun withdrawCheck(itemId: Int, amount: Int, itemName: String): Boolean {
        val initialCount = Inventory.getCount(itemId)
        if (Bank.withdraw(itemId, amount)) {
            // Wait a bit for the withdrawal to reflect in the inventory
            Waiting.waitUntil(2000) { Inventory.getCount(itemId) > initialCount }

            // Verify that the item count in inventory increased
            val newCount = Inventory.getCount(itemId)
            return newCount > initialCount
        } else {
            info("Withdrawal attempt for $itemName failed.")
            return false
        }
    }

    private fun shouldCombineKeys(itemCount: Int, random: Random): Boolean {
        val chance = calculateChance(itemCount)
        val randomNumber = random.nextInt(100)
        info("Item count: $itemCount, Chance: $chance, Random Number: $randomNumber")
        return randomNumber < chance
    }

    private fun hasKeyParts(): Boolean {
        val toothHalfCount = Bank.getCount(TOOTH_HALF)
        val loopHalfCount = Bank.getCount(LOOP_HALF)

        info("Tooth half count: $toothHalfCount, Loop half count: $loopHalfCount")
        return toothHalfCount > 10 && loopHalfCount > 10
    }

    private fun depositInventoryWithDelay(): Boolean {
        info("Depositing inventory...")
        withABC2Delay { Bank.depositInventory() }
        return Inventory.isEmpty()
    }

    private fun ensureRingEquipped() {
        val isRingEquipped = Equipment.getAll().any { it.id in ringIds }

        if (!isRingEquipped) {
            val ringIdToWithdraw = ringIds.firstOrNull { Bank.contains(it) }

            ringIdToWithdraw?.let {
                info("Withdrawing Ring of Dueling with ID: $it")
                withdrawAndEquipRing(it)
            } ?: error("No Ring of Dueling found in the bank.")
        } else {
            info("Ring of Dueling is already equipped.")
        }
    }

    private fun withdrawAndEquipRing(ringId: Int) {
        withABC2Delay { Bank.withdraw(ringId, 1) }
        withABC2Delay {
            val ring = Inventory.getAll().firstOrNull { it.id in ringIds }
            ring?.let {
                info("Equipping Ring of Dueling with ID: ${ring.id}")
                Equipment.equip(ring.id)
            }
        }
    }

    private fun calculateChance(itemCount: Int): Int {
        return if (itemCount < 20) {
            100
        } else {
            val maxItemCount = 100
            val minChance = 20
            val maxChance = 80

            if (itemCount < maxItemCount) {
                maxChance - ((itemCount - 20).toDouble() / (maxItemCount - 20) * (maxChance - minChance)).toInt()
            } else {
                minChance
            }
        }
    }
}

private fun <T> weightedRandomChoice(choices: List<Pair<Int, T>>): T {
    val totalWeight = choices.sumOf { it.first }
    val randomNumber = Random().nextInt(totalWeight)
    var currentWeight = 0
    for ((weight, item) in choices) {
        currentWeight += weight
        if (randomNumber < currentWeight) return item
    }
    throw IllegalStateException("Big bad this should never happen :(")
}
