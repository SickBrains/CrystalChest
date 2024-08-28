package scripts.states

import dax.teleports.Teleport.setMoveCosts
import org.tribot.api2007.types.RSTile
import org.tribot.script.sdk.MyPlayer
import org.tribot.script.sdk.walking.GlobalWalking
import org.tribot.script.sdk.Waiting
import org.tribot.script.sdk.types.WorldTile
import scripts.*

class WalkingState : ScriptState {
    override fun performAction(script: Crystal_Chest) {
        println("Walking to chest...")
        // To prevent it from TPing while being near the chest
        setMoveCosts(50)
        if (GlobalWalking.walkTo(toWorldTile(chestLocation.random().randomTile))) {
            // Wait until the player stops moving
            Waiting.waitUntil(30000) { !MyPlayer.isMoving() }
        } else {
            println("Failed to initiate walking.")
        }
    }
}
fun toWorldTile(tile: RSTile): WorldTile {
    return WorldTile(tile.x, tile.y, tile.plane)
}