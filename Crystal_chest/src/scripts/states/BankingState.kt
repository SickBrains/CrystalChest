package scripts.states

import org.tribot.script.sdk.*
import org.tribot.script.sdk.Log.info
import org.tribot.script.sdk.types.Area
import org.tribot.script.sdk.types.WorldTile
import org.tribot.script.sdk.walking.GlobalWalking
import scripts.ABC2Settings.withABC2Delay
import scripts.Crystal_Chest
import scripts.ScriptState

import java.util.Random

// Constants for item IDs
object ItemIds {
    const val CRYSTAL_KEY = 989
    const val TOOTH_HALF = 987
    const val LOOP_HALF = 985
    const val TELEPORT_TO_HOUSE = "Teleport to house"
}

// Ring of Dueling IDs
val ringIds = setOf(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566)

// Bank area
val bank = Area.fromRectangle(WorldTile(2440, 3087, 0), WorldTile(2443, 3083, 0))

class BankingState : ScriptState {

    override fun performAction(script: Crystal_Chest) {
        val random = Random()
        info("Banking...")

        if (!bank.contains(MyPlayer.getTile())) {
            GlobalWalking.walkTo(bank.randomTile)
            return
        }

        if (!Bank.isOpen()) {
            withABC2Delay { Bank.ensureOpen() }
            Waiting.wait(1000)
            return
        }

        if (hasKeyParts()) {
            val itemCount = Bank.getCount(ItemIds.CRYSTAL_KEY)
            val chance = calculateChance(itemCount)
            val randomNumber = random.nextInt(100)

            info("Item count: $itemCount, Chance: $chance, Random Number: $randomNumber")

            if (randomNumber < chance) {
                script.changeState(KeysState())
                script.isCombiningKeys = true
                return
            }
        }

        // Deposit inventory and ensure equipment
        if (!depositInventoryWithDelay()) {
            return
        }
        ensureRingEquipped()

        // Withdraw items in randomized order
        randomizedItemWithdrawals(random)

        withABC2Delay { Bank.close() }
    }


    // Withdraw items in random order
    private fun randomizedItemWithdrawals(random: Random) {
        if (random.nextBoolean()) {
            info("Withdrawing 'Teleport to house' first.")
            withdrawTeleportToHouse(random)
            withdrawCrystalKeys(random)
        } else {
            info("Withdrawing 'Crystal key' first.")
            withdrawCrystalKeys(random)
            withdrawTeleportToHouse(random)
        }
    }

    // Will add player preferences for the weights later
    private fun withdrawTeleportToHouse(random: Random) {
        val withdrawAmount = weightedRandomChoice(listOf(80 to 6, 5 to 5, 15 to 2))
        if (!withdrawItemWithDelay(ItemIds.TELEPORT_TO_HOUSE, withdrawAmount)) {
            error("Failed to withdraw 'Teleport to house'.")
        }
    }

    private fun withdrawCrystalKeys(random: Random) {
        val withdrawAmount = weightedRandomChoice(listOf(80 to 6, 15 to 5, 5 to 4))
        if (!withdrawItemWithDelay("Crystal key", withdrawAmount)) {
            error("Failed to withdraw 'Crystal key'.")
        }
    }

    private fun hasKeyParts(): Boolean {
        val toothHalfCount = Bank.getCount(ItemIds.TOOTH_HALF)
        val loopHalfCount = Bank.getCount(ItemIds.LOOP_HALF)

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
                withABC2Delay { Bank.withdraw(it, 1) }
                withABC2Delay {
                    val ring = Inventory.getAll().firstOrNull { it.id in ringIds }
                    ring?.let {
                        info("Equipping Ring of Dueling with ID: ${ring.id}")
                        Equipment.equip(ring.id)
                    }
                }
            } ?: error("No Ring of Dueling found in the bank.")
        } else {
            info("Ring of Dueling is already equipped.")
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


// function for weighted random choice
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

private fun withdrawItemWithDelay(itemName: String, amount: Int): Boolean {
    return if (Bank.contains(itemName)) {
        withABC2Delay { Bank.withdraw(itemName, amount) }
        true
    } else {
        error("$itemName not found in the bank.")
        false
    }
}

