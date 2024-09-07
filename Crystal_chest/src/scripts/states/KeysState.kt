package scripts.states

import org.tribot.script.sdk.*
import org.tribot.script.sdk.query.Query
import scripts.ABC2Settings.withABC2Delay
import scripts.Constants
import scripts.Crystal_Chest
import scripts.ScriptState
import java.util.*

class KeysState : ScriptState {

    override fun performAction(script: Crystal_Chest) {
        println("Combining keys...")
        script.isCombiningKeys = false

        combineKeys(Random())
    }

    private fun combineKeys(random: Random) {
        withABC2Delay {
            Bank.ensureOpen()
            println("Depositing inventory...")
            Bank.depositInventory()
            Waiting.waitUntil { Inventory.isEmpty() }
            withdrawKeyParts(random)
        }
    }

    private fun withdrawKeyParts(random: Random) {
        val keyAmount = random.nextInt(8) + 7

        // Withdraw Tooth half of a key
        withABC2Delay {
            if (Bank.withdraw(985, keyAmount)) {
                println("Withdrew $keyAmount 'Tooth half of a key'.")
            } else {
                println("Failed to withdraw 'Tooth half of a key'.")
            }
        }
        // Wait for tooth halves to be in inventory
        Waiting.waitUntil { Inventory.getCount(985) >= keyAmount }
        // Withdraw Loop half of a key
        withABC2Delay {
            if (Bank.withdraw(987, keyAmount)) {
                println("Withdrew $keyAmount 'Loop half of a key'.")
            } else {
                println("Failed to withdraw 'Loop half of a key'.")
            }
        }
        // Wait for loop halves to be in inventory
        Waiting.waitUntil { Inventory.getCount(987) >= keyAmount }
        // Close the bank and proceed to combine keys
        Bank.close()
        combineKeyParts()
    }

    private fun combineKeyParts() {
        val toothHalfCount = Inventory.getCount(Constants.TOOTH_HALF)
        val loopHalfCount = Inventory.getCount(Constants.LOOP_HALF)

        if (toothHalfCount > 0 && loopHalfCount > 0) {
            combineSingleKeyPair(toothHalfCount, loopHalfCount)
        } else {
            println("Not enough key parts to combine.")
        }
    }

    private fun combineSingleKeyPair(toothHalfCount: Int, loopHalfCount: Int) {
        val toothHalf = Query.inventory().idEquals(985).findClosestToMouse()
        val loopHalf = Query.inventory().idEquals(987).findRandom()

        toothHalf.ifPresent { item1 ->
            loopHalf.ifPresent { item2 ->
                withABC2Delay {
                    if (item1.useOn(item2)) {
                        println("Combined 'Tooth half of a key' and 'Loop half of a key'.")
                        combineKeyParts()
                    } else {
                        println("Failed to combine keys.")
                    }
                }
            }
        }
    }
}
