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

    private var rewardCoins = 0  // number of coins that can be obtained by completing the level

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
        val actualSpeed = speed * (theGame.gameUpgrades[Upgrade.Type.DECREASE_ATT_SPEED]?.getStrength() ?: 1.0f)
        val attacker = if (isCoin)
            Cryptocoin(network, (maxNumber*1.5).toULong(), actualSpeed )
        else
            Attacker(network, representation,
            Random.nextULong((maxNumber+1).toULong()), actualSpeed )
        network.addVehicle(attacker)

        if (tracks.size > 0)
            attacker.setOntoTrack(tracks[Random.nextInt(tracks.size)])
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

    private fun initializeNetwork(dimX: Int, dimY: Int)
    /** creates an empty network with the given grid dimensions */
    {
        sizeX = dimX
        sizeY = dimY
        network = Network(theGame, sizeX, sizeY)
        theGame.viewport.setViewportSize(sizeX, sizeY)
    }

    private fun createChip(gridX: Int, gridY: Int, ident: Int = -1, type: Chip.ChipType = Chip.ChipType.EMPTY): Chip
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

    private fun createLink(from: Int, to: Int, ident: Int, mask: Int = 0xF)
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

    private fun createTrack(linkIdents: List<Int>, ident: Int)
            /** adds a track of connected links
             * @param linkIdents List of the link idents in the track
             * */
    {
        val track = network.createTrack(linkIdents, false)
        tracks[ident] = track
    }
    
    private fun createWave(attackerCount: Int, attackerStrength: Int, attackerFrequency: Float, attackerSpeed: Float,
                           coins: Int = 0, representation: Attacker.Representation = Attacker.Representation.UNDEFINED)
    {
        val waveData = Wave.Data(attackerCount, attackerStrength, attackerFrequency, attackerSpeed, coins, currentCount = attackerCount, representation = representation)
        waves.add(Wave(theGame, waveData))
    }

    private fun createWaveHex(attackerCount: Int, attackerStrength: Int, attackerFrequency: Float, attackerSpeed: Float, coins: Int = 0)
    {
        val waveData = Wave.Data(attackerCount, attackerStrength, attackerFrequency, attackerSpeed, coins, currentCount = attackerCount, representation = Attacker.Representation.HEX)
        waves.add(Wave(theGame, waveData))
    }

    private fun createAttacker(data: Attacker.Data)
    {
        var attacker = Attacker.createFromData(this, data)
        network.addVehicle(attacker)
    }

    fun takeSnapshot(size: Int): Bitmap?
            /** gets a miniature picture of the current level
             * @param size snapshot size in pixels (square)
             * @return the bitmap that holds the snapshot
             */
    {
        var p: Viewport = theGame.viewport
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

    fun createNetwork(level: Int): Network
    {
        this.data.level = level
        waves.clear()
        type = Type.REGULAR
        data.chipsAllowed =
            setOf(Chip.ChipUpgrades.ACC, Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.SHIFT, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.MEM)

        when (level) {
            1 -> {
                initializeNetwork(40, 40)

                createChip(20, 1, type = Chip.ChipType.ENTRY)
                createChip(15, 20, 1)
                createChip(20, sizeY - 2, type = Chip.ChipType.CPU)

                createLink(0, 1, 0, mask = 0x06)
                createLink(1, 999, 1, mask = 0x06)

                createTrack(listOf(0, 1), 0)

                createWave(4, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB)
            }
            2 -> {
                initializeNetwork(40, 40)

                createChip(20, 1, type = Chip.ChipType.ENTRY)
                createChip(15, 20, 1)
                createChip(20, sizeY - 2, type = Chip.ChipType.CPU)

                createLink(0, 1, 0, mask = 0x06)
                createLink(1, 999, 1, mask = 0x06)

                createTrack(listOf(0, 1), 0)

                createWave(8, 1, .075f, 1f)
                createWave(8, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB)
            }
            3 -> {
                initializeNetwork(50, 50)

                createChip(1, 1, type = Chip.ChipType.ENTRY)
                createChip(20, 8, 1)
                createChip(20, 12, 2)
                createChip(45, 20, 3)
                createChip(45, 30, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 999, 4)

                createTrack(listOf(1, 2, 3, 4), 0)

                createWave(4, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)
                createWave(10, 1, .075f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP)
            }
            4 -> {
                initializeNetwork(50, 50)

                createChip(1, 1, type = Chip.ChipType.ENTRY)
                createChip(10, 12, 1)
                createChip(15, 20, 2)
                createChip(30, 30, 3)
                createChip(35, 45, 4)
                createChip(20, 35, 5)
                createChip(45, 45, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3, mask = 0x03)
                createLink(3, 4, 4, mask = 0x03)
                createLink(2, 5, 5, mask = 0x0C)
                createLink(5, 4, 6, mask = 0x0C)
                createLink(4, 999, 7)

                createTrack(listOf(1, 2, 3, 4, 7), 0)
                createTrack(listOf(1, 2, 5, 6, 7), 1)

                createWave(10, 1, .1f, 1f)
                createWave(10, 2, .1f, 1f)
                createWave(10, 2, .1f, 1f)
                createWave(10, 3, .15f, 1f)

                data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP)
            }
            5 -> {
                initializeNetwork(50, 50)

                createChip(1, 15, type = Chip.ChipType.ENTRY)
                createChip(25, 15, 1)
                createChip(40, 15, 2)
                createChip(40, 5, 3)
                createChip(25, 5, 4)
                createChip(25, 25, 5)
                createChip(25, 35, 6)
                createChip(40, 35, 7)
                createChip(40, 25, 8)
                createChip(5, 25, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 4, 4)
                createLink(4, 1, 5)
                createLink(1, 5, 6)
                createLink(5, 6, 7)
                createLink(6, 7, 8)
                createLink(7, 8, 9)
                createLink(8, 5, 10)
                createLink(5, 999, 11)

                createTrack((1..11).toList(), 0)

                createWave(10, 2, .125f, 1.2f)
                createWave(15, 3, .1250f, 1.1f)
                createWave(15, 3, .1f, 1.1f)
                createWave(20, 4, .1f, 1.1f)
                createWave(20, 7, .050f, 1f)
                createWave(15, 15, .050f, 1f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
            }
            6 -> {
                initializeNetwork(50, 50)

                createChip(1, 5, type = Chip.ChipType.ENTRY)
                createChip(25, 15, 1)
                createChip(40, 15, 2)
                createChip(40, 5, 3)
                createChip(25, 5, 4)
                createChip(25, 25, 5)
                createChip(25, 35, 6)
                createChip(40, 35, 7)
                createChip(40, 25, 8)
                createChip(5, 35, type = Chip.ChipType.CPU)

                createLink(0, 4, 1)
                createLink(4, 3, 2)
                createLink(4, 1, 3)
                createLink(3, 2, 4)
                createLink(1, 2, 5)
                createLink(1, 5, 6)
                createLink(2, 8, 7)
                createLink(5, 8, 8)
                createLink(5, 6, 9)
                createLink(8, 7, 10)
                createLink(6, 7, 11)
                createLink(6, 999, 12)

                createTrack(listOf(1, 2, 4, 5, 6, 8, 10, 11, 12), 0)
                createTrack(listOf(1, 3, 5, 7, 8, 9, 12), 1)

                createWave(10, 2, .125f, 1.2f)
                createWave(15, 3, .120f, 1.1f)
                createWave(15, 3, .110f, 1.1f)
                createWave(20, 4, .110f, 1.1f)
                createWave(20, 7, .050f, 1f, coins = 0)
                createWave(15, 15, .050f, 1f, coins = 0)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            7 -> {
                initializeNetwork(50, 50)

                createChip(10, 10, type = Chip.ChipType.ENTRY)
                createChip(25, 10, 2)
                createChip(40, 10, 3)
                createChip(10, 25, 8)
                createChip(25, 25, type = Chip.ChipType.CPU)
                createChip(40, 25, 4)
                createChip(10, 40, 7)
                createChip(25, 40, 6)
                createChip(40, 40, 5)


                createLink(0, 2, 1)
                createLink(2, 3, 2)
                createLink(3, 4, 3)
                createLink(4, 5, 4)
                createLink(5, 6, 5)
                createLink(6, 7, 6)
                createLink(7, 8, 7)
                createLink(8, 999, 8)

                createTrack(listOf(1, 2, 3, 4, 5, 6, 7, 8, 0), 0)

                createWave(10, 2, .125f, 1.2f)
                createWave(15, 3, .120f, 1.1f)
                createWave(15, 3, .110f, 1.1f)
                createWave(20, 4, .110f, 1.1f)
                createWave(20, 7, .050f, 1f)
                createWave(15, 15, .050f, 1f, coins = 1)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            8 -> {
                initializeNetwork(50, 50)

                createChip(1, 25, type = Chip.ChipType.ENTRY)
                createChip(20, 15, 1)
                createChip(35, 15, 2)
                createChip(20, 25, 3)
                createChip(35, 25, 4)
                createChip(20, 35, 5)
                createChip(35, 35, 6)
                createChip(45, 25, type = Chip.ChipType.CPU)


                createLink(0, 3, 1)
                createLink(3, 1, 2)
                createLink(1, 2, 3)
                createLink(2, 4, 4)
                createLink(3, 4, 5)
                createLink(3, 5, 6)
                createLink(5, 6, 7)
                createLink(6, 4, 8)
                createLink(4, 999, 9)

                createTrack(listOf(1, 5, 4, 3, 2, 5, 9), 0)
                createTrack(listOf(1, 5, 8, 7, 6, 5, 9), 1)
                createTrack(listOf(1, 5, 4, 3, 2, 5, 9), 2)
                createTrack(listOf(1, 5, 8, 7, 6, 5, 9), 3)
                createTrack(listOf(1, 5, 9), 4)

                createWave(15, 2, .150f, 1.4f)
                createWave(16, 2, .160f, 1.5f)
                createWave(25, 2, .180f, 1.7f)
                createWave(25, 2, .220f, 2.0f)
                createWave(40, 2, .300f, 2.2f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            9 -> {
                initializeNetwork(50, 50)

                createChip(10, 12, ident = 0, type = Chip.ChipType.ENTRY)
                createChip(20, 19, 1)
                createChip(8, 38, 2)
                createChip(25, 45, 3)
                createChip(45, 5, 4, type = Chip.ChipType.ENTRY)
                createChip(32, 25, 5)
                createChip(40, 38, 6)
                createChip(45, 18, type = Chip.ChipType.CPU)

                createLink(0, 1, 1, mask = 0x07)
                createLink(1, 2, 2, mask = 0x07)
                createLink(2, 3, 3, mask = 0x0E)
                createLink(3, 6, 4, mask = 0x07)
                createLink(6, 999, 7, mask = 0x07)
                createLink(4, 5, 5, mask = 0x02)
                createLink(5, 2, 6, mask = 0x08)

                createTrack(listOf(1, 2, 3, 4, 7), 0)
                createTrack(listOf(5, 6, 3, 4, 7), 1)
                createTrack(listOf(1, 2, 3, 4, 7), 2)

                createWave(16, 1, .125f, 1.1f)
                createWave(20, 2, .120f, 1.0f)
                createWave(20, 3, .110f, 1.0f)
                createWave(20, 4, .110f, 1.0f)
                createWave(20, 7, .050f, 1f, coins = 0)
                createWave(15, 15, .050f, 1f, coins = 0)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            10 -> {
                initializeNetwork(50, 50)

                createChip(10, 45, type = Chip.ChipType.ENTRY)
                createChip(10, 35, 1)
                createChip(20, 20, 2)
                createChip(30, 5, 3)
                createChip(10, 5, 4)
                createChip(30, 35, 5)
                createChip(40, 35, 6)
                createChip(48, 35, type = Chip.ChipType.CPU)


                createLink(0, 1, 1, mask = 0x0C)
                createLink(1, 2, 2, mask = 0x0C)
                createLink(2, 3, 3, mask = 0x03)
                createLink(2, 4, 4, mask = 0x0C)
                createLink(2, 5, 5, mask = 0x03)
                createLink(3, 4, 7, mask = 0x06)
                createLink(5, 6, 6, mask = 0x06)
                createLink(6, 999, 8, mask = 0x06)

                createTrack(listOf(1, 2, 3, 7, 4, 5, 6, 8), 0)

                createWave(10, 2, .140f, 1.4f)
                createWave(20, 3, .130f, 1.3f)
                createWave(10, 5, .120f, 1.2f)
                createWave(20, 7, .110f, 1.1f)
                createWave(10, 8, .110f, 1.0f)
                createWave(20, 12, .110f, 1.0f)
                createWaveHex(20, 18, .100f, 1.0f)
                createWaveHex(20, 28, .100f, 1.1f, coins = 1)
                createWaveHex(20, 40, .100f, 1.2f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            11 -> {
                initializeNetwork(50, 50)

                createChip(23, 3, 50, type = Chip.ChipType.ENTRY)
                createChip(25, 5, 51, type = Chip.ChipType.ENTRY)
                createChip(27, 3, 52, type = Chip.ChipType.ENTRY)
                createChip(10, 5, 1)
                createChip(40, 5, 2)
                createChip(10, 20, 3)
                createChip(25, 20, 4)
                createChip(40, 20, 5)
                createChip(10, 35, 6)
                createChip(25, 35, 7, type = Chip.ChipType.SUB)
                createChip(40, 35, 8)
                createChip(25, 42, type = Chip.ChipType.CPU)
                chips[7]?.setType(Chip.ChipType.SUB)

                createLink(50, 1, 1, mask = 0x0E)
                createLink(52, 2, 2, mask = 0x07)
                createLink(1, 3, 3, mask = 0x0E)
                createLink(51, 4, 4, mask = 0x07)
                createLink(2, 5, 5, mask = 0x07)
                createLink(3, 6, 6, mask = 0x0E)
                createLink(4, 7, 7, mask = 0x07)
                createLink(5, 8, 8, mask = 0x07)
                createLink(6, 999, 9, mask = 0x0E)
                createLink(7, 999, 10, mask = 0x07)
                createLink(8, 999, 11, mask = 0x07)

                createTrack(listOf(1, 3, 6, 9), 0)
                createTrack(listOf(4, 7, 10), 1)
                createTrack(listOf(2, 5, 8, 11), 2)

                createWave(10, 1, .125f, 1.2f)
                createWave(10, 1, .125f, 1.1f)
                createWave(15, 2, .100f, 1.0f)
                createWave(20, 3, .100f, 1.0f)
                createWave(20, 4, .100f, 1.0f)
                createWave(20, 5, .125f, 1.0f)
                createWave(20, 6, .05f, 1f, coins = 1)
                createWave(15, 8, .05f, 1f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3

            }
            12 -> {
                initializeNetwork(50, 50)

                createChip(45, 1, type = Chip.ChipType.ENTRY)
                createChip(34, 5, 1)
                createChip(12, 15, 2)
                createChip(45, 12, 6)
                createChip(30, 20, 3)
                createChip(25, 32, 4)
                createChip(40, 40, 5)
                createChip(12, 23, 7)
                createChip(10, 40, 8)
                createChip(20, 45, 9)
                createChip(45, 30, type = Chip.ChipType.CPU)

                createLink(0, 1, 1, mask = 0x0C)
                createLink(1, 2, 2, mask = 0x0C)
                createLink(2, 3, 3, mask = 0x0C)
                createLink(3, 4, 4, mask = 0x03)
                createLink(4, 5, 5, mask = 0x0C)
                createLink(0, 6, 7, mask = 0x03)
                createLink(6, 3, 8, mask = 0x03)
                createLink(3, 7, 9, mask = 0x0C)
                createLink(7, 8, 10, mask = 0x0C)
                createLink(8, 9, 11, mask = 0x0C)
                createLink(9, 5, 12, mask = 0x0C)
                createLink(5, 999, 6, mask = 0x03)

                createTrack(listOf(1, 2, 3, 4, 5, 6), 0)
                createTrack(listOf(7, 8, 9, 10, 11, 12, 6), 1)

                createWave(16, 2, .09f, 2f)
                createWave(12, 3, .075f, 1f)
                createWave(12, 5, .075f, 1f)
                createWave(10, 12, .075f, 1f)
                createWave(10, 16, .075f, 1f)
                createWave(10, 20, .075f, 1f)
                createWave(10, 30, .075f, 1f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            13 -> {
                initializeNetwork(50, 50)

                createChip(10, 45, 0, type = Chip.ChipType.ENTRY)
                createChip(40, 5, 1, type = Chip.ChipType.ENTRY)
                createChip(10, 35, 2)
                createChip(10, 25, 3)
                createChip(10, 15, 4)
                createChip(25, 15, 5)
                createChip(40, 15, 6)
                createChip(40, 25, 7)
                createChip(40, 35, 8)
                createChip(25, 35, 9)
                createChip(25, 25, type = Chip.ChipType.CPU)

                createLink(0, 2, 1)
                createLink(2, 3, 2)
                createLink(3, 4, 3)
                createLink(4, 5, 4)
                createLink(5, 6, 5)
                createLink(6, 7, 6)
                createLink(7, 999, 7)
                createLink(1, 6, 8)
                createLink(7, 8, 9)
                createLink(8, 9, 10)
                createLink(9, 2, 11)
                createLink(3, 999, 12)

                createTrack(listOf(1, 2, 3, 4, 5, 6, 7), 0)
                createTrack(listOf(8, 6, 9, 10, 11, 2, 12), 1)

                createWave(12, 3, .07f, 1.1f)
                createWave(12, 4, .07f, 1.2f)
                createWave(12, 6, .06f, 1.2f)
                createWave(16, 10, .06f, 1.2f)
                createWave(12, 12, .05f, 1.2f)
                createWave(16, 15, .05f, 1.2f)
                createWaveHex(12, 20, .05f, 1.2f)
                createWaveHex(12, 32, .05f, 1.2f)

                data.chipsAllowed =
                    setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT)
                rewardCoins = 3
            }
            14 -> {
                initializeNetwork(50, 55)

                createChip(8, 3, ident = 0, type = Chip.ChipType.ENTRY)
                createChip(15, 22, ident = 1, type = Chip.ChipType.ENTRY)
                createChip(20, 6, 2)
                createChip(32, 10, 3)
                createChip(46, 20, 4)
                createChip(22, 30, 5)
                createChip(8, 30, 6)
                createChip(3, 42, 7)
                createChip(21, 45, 8)
                createChip(32, 40, 9)
                createChip(43, 35, type = Chip.ChipType.CPU)

                createLink(0, 2, 1)
                createLink(2, 3, 2)
                createLink(3, 4, 3)
                createLink(4, 5, 4)
                createLink(5, 6, 5)
                createLink(6, 7, 6)
                createLink(7, 8, 7)
                createLink(8, 9, 8)
                createLink(9, 999, 9)
                createLink(1, 6, 10)

                createTrack(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 0)
                createTrack(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 1)
                createTrack(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 2)
                createTrack(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), 3)
                createTrack(listOf(10, 6, 7, 8, 9), 4)

                createWave(12, 3, .05f, 1.0f)
                createWave(12, 5, .06f, 1.1f)
                createWave(12, 7, .07f, 1.1f)
                createWave(10, 12, .08f, 1.1f)
                createWave(10, 20, .08f, 1.2f)
                createWave(10, 40, .09f, 1.2f)
                createWaveHex(15, 50, .09f, 1f, coins = 0)
                createWaveHex(20, 80, .10f, 1f, coins = 0)

                data.chipsAllowed = setOf(
                    Chip.ChipUpgrades.SUB,
                    Chip.ChipUpgrades.POWERUP,
                    Chip.ChipUpgrades.SHIFT,
                    Chip.ChipUpgrades.MEM
                )
                rewardCoins = 3
            }
            15 -> {
                initializeNetwork(50, 55)

                createChip(5, 3, type = Chip.ChipType.ENTRY)
                createChip(10, 12, 1)
                createChip(20, 15, 2)
                createChip(20, 40, 3)
                createChip(30, 45, 4)
                createChip(40, 42, 5)
                createChip(45, 30, 6)
                createChip(45, 20, 61)
                createChip(5, 30, 7)
                createChip(17, 30, 8, type = Chip.ChipType.ENTRY)
                createChip(23, 30, 9, type = Chip.ChipType.ENTRY)
                createChip(32, 30, 10)
                createChip(40, 10, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 4, 4)
                createLink(4, 5, 5)
                createLink(5, 6, 6)
                createLink(6, 61, 61)
                createLink(61, 999, 7)
                createLink(1, 7, 8)
                createLink(7, 8, 9)
                createLink(9, 10, 10)
                createLink(10, 6, 11)

                createTrack(listOf(1, 2, 3, 4, 5, 6, 61, 7), 0)
                createTrack(listOf(1, 8, 9, 10, 11, 61, 7), 1)

                createWave(12, 3, .05f, 1.0f)
                createWave(12, 4, .06f, 1.1f)
                createWave(12, 6, .07f, 1.1f)
                createWave(12, 10, .08f, 1.1f)
                createWave(12, 16, .08f, 1.1f)
                createWave(10, 28, .08f, 1.2f)
                createWave(10, 12, .05f, 2.0f)
                createWave(10, 50, .09f, 1.2f)
                createWave(15, 70, .09f, 1f, coins = 0)
                createWave(15, 80, .09f, 1f, coins = 0)
                createWaveHex(20, 100, .10f, 1f, coins = 0)

                data.chipsAllowed = setOf(
                    Chip.ChipUpgrades.SUB,
                    Chip.ChipUpgrades.POWERUP,
                    Chip.ChipUpgrades.SHIFT,
                    Chip.ChipUpgrades.MEM
                )
                rewardCoins = 3
            }
            16 -> {
                initializeNetwork(50, 55)

                createChip(40, 7, type = Chip.ChipType.ENTRY)
                createChip(20, 5, 1)
                createChip(20, 10, 2)
                createChip(10, 20, 3)
                createChip(30, 20, 4)
                createChip(20, 30, 5)
                createChip(40, 30, 6)
                createChip(10, 40, 7)
                createChip(30, 40, 8)
                createChip(20, 50, 9)
                createChip(40, 48, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3, mask = 0x0C)
                createLink(2, 4, 4, mask = 0x03)
                createLink(3, 5, 5, mask = 0x0C)
                createLink(4, 5, 6, mask = 0x02)
                createLink(4, 6, 7, mask = 0x01)
                createLink(5, 7, 8, mask = 0x0C)
                createLink(5, 8, 9, mask = 0x02)
                createLink(6, 8, 10, mask = 0x01)
                createLink(7, 9, 12, mask = 0x0C)
                createLink(8, 9, 13, mask = 0x06)
                createLink(9, 999, 14)

                createTrack(listOf(1, 2, 3, 5, 8, 12, 14), 0)
                createTrack(listOf(1, 2, 4, 6, 8, 12, 14), 1)
                createTrack(listOf(1, 2, 4, 6, 9, 13, 14), 2)
                createTrack(listOf(1, 2, 4, 7, 10, 13, 14), 3)

                createWave(12, 3, .05f, 1.0f)
                createWave(12, 5, .06f, 1.1f)
                createWave(12, 7, .07f, 1.1f)
                createWave(10, 12, .08f, 1.1f)
                createWave(10, 20, .08f, 1.2f)
                createWave(10, 40, .09f, 1.2f)
                createWaveHex(15, 50, .09f, 1f, coins = 0)
                createWaveHex(20, 80, .10f, 1f, coins = 1)

                // data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT, Chip.ChipUpgrades.ACC)
                rewardCoins = 3
            }
            17 -> {
                initializeNetwork(45, 55)

                createChip(40, 2, type = Chip.ChipType.ENTRY)
                createChip(20, 5, 1)
                createChip(3, 11, 2)
                createChip(18, 20, 3)
                createChip(35, 20, 4)
                createChip(35, 30, 5)
                createChip(38, 46, 6)
                createChip(8, 30, 7)
                createChip(8, 50, 8)
                createChip(25, 42, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2, mask = 0x0C)
                createLink(2, 3, 3, mask = 0x0C)
                createLink(3, 4, 4, mask = 0x0c)
                createLink(4, 5, 5, mask = 0x03)
                createLink(5, 6, 6, mask = 0x03)
                createLink(6, 999, 7, mask = 0x0C)
                createLink(4, 7, 8, mask = 0x0C)
                createLink(3, 7, 9, mask = 0x0C)
                createLink(7, 999, 10, mask = 0x0C)
                createLink(7, 8, 11, mask = 0x0E)
                createLink(8, 999, 12, mask = 0x06)

                createTrack(listOf(1, 2, 3, 4, 8, 10), 0)
                createTrack(listOf(1, 2, 3, 9, 11, 12), 1)
                createTrack(listOf(1, 2, 3, 4, 5, 6, 7), 2)
                createTrack(listOf(1, 2, 3, 9, 8, 5, 6, 7), 3)
                createTrack(listOf(1, 2, 3, 4, 8, 11, 12), 4)

                createWave(12, 3, .05f, 1.0f)
                createWave(16, 5, .06f, 1.1f)
                createWave(16, 7, .07f, 1.1f)
                createWave(12, 12, .08f, 1.1f)
                createWave(12, 20, .08f, 1.2f)
                createWave(12, 40, .09f, 1.2f)
                createWaveHex(16, 50, .09f, 1f, coins = 0)
                createWaveHex(24, 80, .10f, 1f, coins = 1)

                // data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT, Chip.ChipUpgrades.ACC)
                rewardCoins = 3
            }
            18 -> {
                initializeNetwork(50, 50)

                createChip(30, 45, type = Chip.ChipType.ENTRY)
                createChip(15, 45, 1)
                createChip(5, 30, 2)
                createChip(15, 10, 3)
                createChip(25, 10, 4)
                createChip(30, 20, 5)
                createChip(30, 25, 6)
                createChip(20, 32, ident = 901, type = Chip.ChipType.CPU)
                createChip(40, 10, 7)
                createChip(45, 20, 8)
                createChip(45, 25, 9)
                createChip(35, 32, ident = 902, type = Chip.ChipType.CPU)

                createLink(0, 1, 1)
                createLink(1, 2, 2)
                createLink(2, 3, 3)
                createLink(3, 4, 4)
                createLink(4, 5, 5, mask = 0x03)
                createLink(5, 6, 6, mask = 0x03)
                createLink(6, 901, 901, mask = 0x0C)
                createLink(4, 7, 7, mask = 0x03)
                createLink(7, 8, 8, mask = 0x03)
                createLink(8, 9, 9, mask = 0x03)
                createLink(9, 902, 902, mask = 0x0C)

                createTrack(listOf(1, 2, 3, 4, 5, 6, 901), 0)
                createTrack(listOf(1, 2, 3, 4, 7, 8, 9, 902), 1)

                createWave(16, 5, .06f, 1.1f)
                createWave(16, 7, .07f, 1.1f)
                createWave(12, 12, .08f, 1.2f)
                createWave(12, 20, .08f, 1.2f)
                createWave(12, 40, .09f, 1.4f)
                createWaveHex(16, 50, .10f, 1.4f, coins = 0)
                createWaveHex(24, 80, .11f, 1.4f, coins = 1)
                createWaveHex(24, 127, .12f, 1.4f, coins = 0)

                // data.chipsAllowed = setOf(Chip.ChipUpgrades.SUB, Chip.ChipUpgrades.POWERUP, Chip.ChipUpgrades.SHIFT, Chip.ChipUpgrades.ACC)
                rewardCoins = 3
                type = Type.FINAL
            }
        }
        data.maxWaves = waves.size
        return network
    }
}