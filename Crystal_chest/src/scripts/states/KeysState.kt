package scripts.states

import org.tribot.script.sdk.*
import org.tribot.script.sdk.query.Query
import scripts.ABC2Settings.withABC2Delay
import scripts.Crystal_Chest
import scripts.ScriptState


import java.util.*

class KeysState : ScriptState {
    override fun performAction(script: Crystal_Chest) {
        println("Combining keys...")
        val random = Random()
        combineKeys(random)
        script.isCombiningKeys = false
    }

    private fun combineKeys(random: Random) {
        withABC2Delay { Bank.ensureOpen() }

        withABC2Delay {
            println("Depositing inventory...")
            Bank.depositInventory()
        }

        Waiting.wait(1000)

        val keyAmount = random.nextInt(8) + 7

        withABC2Delay {
            if (Bank.withdraw(985, keyAmount)) {
                println("Withdrew $keyAmount 'Tooth half of a key'.")
            } else {
                println("Failed to withdraw $keyAmount 'Tooth half of a key'. Please check bank contents.")

            }
        }

        Waiting.wait(1000)

        withABC2Delay {
            if (Bank.withdraw(987, keyAmount)) {
                println("Withdrew $keyAmount 'Loop half of a key'.")
            } else {
                println("Failed to withdraw $keyAmount 'Loop half of a key'. Please check bank contents.")

            }
        }
        Waiting.wait(1000)
        Bank.close()
        useKeys()
    }

    private fun useKeys() {
        var toothHalfCount = Inventory.getCount(985)
        var loopHalfCount = Inventory.getCount(987)

        while (toothHalfCount > 0 && loopHalfCount > 0) {
            withABC2Delay {
                if (useItem(985, 987)) {
                    println("Combined 'Tooth half of a key' and 'Loop half of a key'.")
                    toothHalfCount--
                    loopHalfCount--
                } else {
                    println("Failed to combine 'Tooth half of a key' and 'Loop half of a key'. Breaking out of the loop.")
                }
            }
        }

        if (toothHalfCount == 0 || loopHalfCount == 0) {
            println("Not enough key parts to combine anymore.")
        }
    }

    fun useItem(itemId1: Int, itemId2: Int): Boolean {
        val item1 = Query.inventory().idEquals(itemId1).findClosestToMouse()
        val item2 = Query.inventory().idEquals(itemId2).findRandom()

        return item1.map { i1 ->
            item2.map { i2 ->
                i1.useOn(i2)
            }.orElse(false)
        }.orElse(false)
    }
}
