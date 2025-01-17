package com.example.cpudefense

import android.graphics.*
import androidx.core.graphics.createBitmap
import com.example.cpudefense.gameElements.*
import com.example.cpudefense.networkmap.Link
import com.example.cpudefense.networkmap.Network
import com.example.cpudefense.networkmap.Track
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.utils.blur
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.random.nextULong

class Stage(var theGame: Game) {

    lateinit var network: Network
    var sizeX = 0
    var sizeY = 0

    var chips = hashMapOf<Int, Chip>()
    var tracks = hashMapOf<Int, Track>()
    var waves = CopyOnWriteArrayList<Wave>()

    enum class Type { REGULAR, FINAL }
    var type = Type.REGULAR

    data class Data (
        var level: Int = 0,
        var gridSizeX: Int = 1,
        var gridSizeY: Int = 1,
        var maxWaves: Int = 0,
        var countOfWaves: Int = 0,
        var chips: HashMap<Int, Chip.Data> = hashMapOf(),
        var links: HashMap<Int, Link.Data> = hashMapOf(),
        var tracks: HashMap<Int, Track.Data> = hashMapOf(),
        var waves: CopyOnWriteArrayList<Wave.Data> = CopyOnWriteArrayList<Wave.Data>(),
        var attackers: CopyOnWriteArrayList<Attacker.Data> = CopyOnWriteArrayList<Attacker.Data>(),
        var chipsAllowed: Set<Chip.ChipUpgrades> = setOf()
    )
    var data = Data()

    data class Summary(
        var coinsAvailable: Int = 0,
        var coinsGot: Int = 0,
        var won: Boolean = false
    )
    lateinit var summary: Summary

    var rewardCoins = 0  // number of coins that can be obtained by completing the level

    fun calculateRewardCoins(previousSummary: Summary?): Int
            /** calculate the coins available for completing this level,
             * taking into account the coins already got in previous games.
             * @param previousSummary Saved data set for this level, contains number of coins got earlier
             * @return number of coins for the current game
              */
    {
        summary = previousSummary ?: Summary()
        summary.coinsAvailable = rewardCoins - summary.coinsGot
        return summary.coinsAvailable
    }

    fun provideData(): Data
            /** serialize all objects that belong to this stage
             * and return the data object
             * for saving and restoring the game.
             */
    {
        data.gridSizeX = network.data.gridSizeX
        data.gridSizeY = network.data.gridSizeY
        data.chips.clear()
        chips.forEach()
        { (key, value) -> data.chips[key] = value.chipData }
        data.links.clear()
        network.links.forEach()
        { (key, value) -> data.links[key] = value.data }
        data.tracks.clear()
        tracks.forEach()
        { (key, track) -> data.tracks[key] = track.data }
        data.waves.clear()
        waves.forEach()
        { data.waves.add(it.data) }
        data.attackers.clear()
        network.vehicles.forEach()
        { data.attackers.add((it as Attacker).provideData())}
        return data
    }

    companion object {
        fun createStageFromData(game: Game, stageData: Data?): Stage?
        {
            val data = stageData ?: return null
            val stage = Stage(game)
            stage.data = data
            stage.sizeX = data.gridSizeX
            stage.sizeY = data.gridSizeY
            stage.network = Network(game, stage.sizeX, stage.sizeY)
            for ((id, chipData) in stage.data.chips)
            {
                val chip = Chip.createFromData(stage.network, chipData)
                stage.network.addNode(chip, id)
                stage.chips[id] = chip
            }
            for ((id, linkData) in stage.data.links)
            {
                val link = Link.createFromData(stage.network, linkData)
                stage.network.addLink(link, id)
            }
            for ((id, trackData) in stage.data.tracks)
            {
                val track = Track.createFromData(stage, trackData)
                stage.tracks[id] = track
            }
            for (waveData in stage.data.waves)
            {
                val wave = Wave.createFromData(game, waveData)
                stage.waves.add(wave)
            }
            for (attackerData in stage.data.attackers)
            {
                val attacker = Attacker.createFromData(stage, attackerData)
                stage.network.addVehicle(attacker)
            }
            stage.calculateRewardCoins(game.summaryPerLevel[stage.data.level])
            return stage
        }
    }

    fun createNewAttacker(maxNumber: Int, speed: Float, isCoin: Boolean = false,
                          representation: Attacker.Representation = Attacker.Representation.BINARY)
    {
        val actualSpeed = speed * (theGame.gameUpgrades[Hero.Type.DECREASE_ATT_SPEED]?.getStrength() ?: 1.0f)
        val attacker = if (isCoin)
            Cryptocoin(network, (maxNumber*1.5).toULong(), actualSpeed )
        else
            Attacker(network, representation,
            Random.nextULong((maxNumber+1).toULong()), actualSpeed )
        network.addVehicle(attacker)

        if (tracks.size > 0)
            attacker.setOntoTrack(tracks[Random.nextInt(tracks.size)])
    }

    fun chipCount(type: Chip.ChipType): Int
            /**
             * @param type the type of chips to be counted
             *  @return the number of chips of this type in the network
             *  */
    {
        return chips.values.filter { it.chipData.type == type }.size
    }

    fun attackerCount(): Int
    {
        return network.vehicles.size
    }

    fun nextWave(): Wave?
    {
        if (theGame.state.phase != Game.GamePhase.RUNNING)
            return null
        else if (waves.size == 0)
        {
            theGame.onEndOfStage()
            return null
        }
        else {
            data.countOfWaves++
            return waves.removeFirst()
        }
    }

    /* methods for creating and setting up the stage */

    fun initializeNetwork(dimX: Int, dimY: Int)
    /** creates an empty network with the given grid dimensions */
    {
        sizeX = dimX
        sizeY = dimY
        network = Network(theGame, sizeX, sizeY)
        theGame.viewport.setGridSize(sizeX, sizeY)
    }

    fun createChip(gridX: Int, gridY: Int, ident: Int = -1, type: Chip.ChipType = Chip.ChipType.EMPTY): Chip
            /**
             * creates a chip at the given position. If an ident is given, it is used,
             * otherwise a new ident is created.
             * @param gridX Position in grid coordinates
             * @param gridY Position in grid coordinates
             * @param ident Node ident, or -1 to choose a new one
             */
    {
        var id = ident
        lateinit var chip: Chip
        when (type)
        {
            Chip.ChipType.ENTRY -> {
                chip = EntryPoint(network, gridX, gridY)
                if (id == -1)
                    id = 0  // default value, may be overridden
            }
            Chip.ChipType.CPU -> {
                chip = Cpu(network, gridX, gridY)
                if (id == -1)
                    id = 999  // default value
            }
            else -> { chip = Chip(network, gridX, gridY) }
        }
        id = network.addNode(chip, id)
        chips[id] = chip
        chip.setIdent(id)
        return chip
    }

    fun createLink(from: Int, to: Int, ident: Int, mask: Int = 0xF)
    /** adds a link between two existing nodes, referenced by ID
     * @param from Ident of first node
     * @param to Ident of 2nd node
     * @param ident Ident of the link, or -1 to choose a new one
     * */
    {
        val node1 = network.nodes[from] ?: return
        val node2 = network.nodes[to] ?: return
        val link = Link(network, node1, node2, ident, mask)
        network.addLink(link, ident)
    }

    fun createTrack(linkIdents: List<Int>, ident: Int)
            /** adds a track of connected links
             * @param linkIdents List of the link idents in the track
             * */
    {
        val track = network.createTrack(linkIdents, false)
        tracks[ident] = track
    }
    
    fun createWave(attackerCount: Int, attackerStrength: Int, attackerFrequency: Float, attackerSpeed: Float,
                           coins: Int = 0, representation: Attacker.Representation = Attacker.Representation.UNDEFINED)
    {
        val waveData = Wave.Data(attackerCount, attackerStrength, attackerFrequency, attackerSpeed, coins, currentCount = attackerCount, representation = representation)
        waves.add(Wave(theGame, waveData))
    }

    fun createWaveHex(attackerCount: Int, attackerStrength: Int, attackerFrequency: Float, attackerSpeed: Float, coins: Int = 0)
    {
        val waveData = Wave.Data(attackerCount, attackerStrength, attackerFrequency, attackerSpeed, coins, currentCount = attackerCount, representation = Attacker.Representation.HEX)
        waves.add(Wave(theGame, waveData))
    }

    private fun createAttacker(data: Attacker.Data)
    {
        val attacker = Attacker.createFromData(this, data)
        network.addVehicle(attacker)
    }

    fun takeSnapshot(size: Int): Bitmap?
            /** gets a miniature picture of the current level
             * @param size snapshot size in pixels (square)
             * @return the bitmap that holds the snapshot
             */
    {
        val p: Viewport = theGame.viewport
        if (p.viewportWidth > 0 && p.viewportHeight > 0)
        {
            var bigSnapshot = createBitmap(p.viewportWidth, p.viewportHeight)
            network.display(Canvas(bigSnapshot), p)
            /* blur the image */
            bigSnapshot = bigSnapshot.blur(theGame.gameActivity, 3f) ?: bigSnapshot
            return Bitmap.createScaledBitmap(bigSnapshot, size, size, true)
        }
        else
            return null
    }
}