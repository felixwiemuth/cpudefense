package com.example.cpudefense.gameElements

import android.graphics.*
import android.view.MotionEvent
import com.example.cpudefense.*
import com.example.cpudefense.effects.Explodable
import com.example.cpudefense.effects.Fadable
import com.example.cpudefense.effects.Fader
import com.example.cpudefense.networkmap.Network
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.utils.center
import com.example.cpudefense.utils.displayTextCenteredInRect
import com.example.cpudefense.utils.scaleAndSetCenter
import com.example.cpudefense.utils.setCenter
import kotlin.math.log2
import kotlin.random.Random


open class Attacker(network: Network, type: Representation = Representation.BINARY,
                    number: ULong = 1u, speed: Float = 1.0f):
    Vehicle(network), Explodable, Fadable {
    enum class Representation { UNDEFINED, BINARY, HEX, DECIMAL, FLOAT }

    data class Data(
        var representation: Representation,
        var number: ULong,
        var binaryDigits: Int,
        var hexDigits: Int,
        var bits: Int,
        var isCoin: Boolean = false,
        var vehicle: Vehicle.Data
    )

    var attackerData = Data( representation = type, number = number, binaryDigits = 0, hexDigits = 0,
        bits = 0, vehicle = super.data
    )
    private var numberBitmap: Bitmap = Bitmap.createBitmap(100, 32, Bitmap.Config.ARGB_8888)
    var actualRect = Rect()
    private var oldNumber: ULong = 0U
    var oldNumberBitmap: Bitmap? = null
    var immuneTo: Chip? = null
    var animationCount = 0
    private val animationCountMax = 8
    private val numberFontSize = 24f * theNetwork.theGame.globalResolutionFactor
    var displacement = Pair(Random.nextInt(5)-1, Random.nextInt(7)-2) // small shift in display to avoid over-crowding on the screen
    private val paintBitmap = Paint()
    var scale: Float = 1.0f

    init {
        this.data.speed = speed
        if (attackerData.bits == 0)
            calculateNumberOfDigits()
        makeNumber(this)
    }

    private fun calculateNumberOfDigits()
            /** determine how many binary or hex digits the value must have,
             * given its number
             */
    {
        if (attackerData.representation == Representation.UNDEFINED)
            attackerData.representation = if (attackerData.number >= 32u) Representation.HEX else Representation.BINARY
        if (attackerData.representation == Representation.BINARY)
        {
            attackerData.binaryDigits = log2(attackerData.number.toFloat()).toInt() + 1
            if (attackerData.binaryDigits < 1)
                attackerData.binaryDigits = 1
            else if (attackerData.binaryDigits > 16)
                attackerData.binaryDigits = 16
            else while (!maskBinary.containsKey(attackerData.binaryDigits))
                attackerData.binaryDigits++  // adjust 'digits' to nearest allowed value
            maskBinary[attackerData.binaryDigits]?.let { mask ->
                attackerData.number = attackerData.number and mask }
        }
        else
        {
            attackerData.hexDigits = log16(attackerData.number.toFloat()).toInt() + 1
            if (attackerData.hexDigits < 2)
                attackerData.hexDigits = 2
            else if (attackerData.hexDigits > 8)
                attackerData.hexDigits = 8
            else while (!maskHex.containsKey(attackerData.hexDigits))
                attackerData.hexDigits++  // adjust 'digits' to nearest allowed value
            maskHex[attackerData.hexDigits]?.let { mask ->
                attackerData.number = attackerData.number and mask }
        }
        attackerData.bits = attackerData.binaryDigits + 4 * attackerData.hexDigits
    }

    fun provideData(): Data
    {
        attackerData.vehicle.gridPos = posOnGrid?.asPair() ?: Pair(0f, 0f)
        /* get the ident of the current link */
        // val keys = theNetwork.links.filterValues { it == onLink }.keys
        attackerData.vehicle.linkId = onLink?.data?.ident ?: -1
        return attackerData
    }

    fun changeNumberTo(newNumber: ULong) {
        oldNumber = attackerData.number
        oldNumberBitmap = numberBitmap
        animationCount = animationCountMax
        attackerData.number = newNumber
        if (newNumber>oldNumber)
            calculateNumberOfDigits()
        makeNumber(this)
    }

    fun invertNumber()
    {
        var n: ULong = attackerData.number
        n = n.inv()
        if (attackerData.representation == Representation.BINARY)
            maskBinary[attackerData.binaryDigits]?.let { mask -> n = n and mask }
        else
            maskHex[attackerData.hexDigits]?.let { mask -> n = n and mask }
        changeNumberTo(n)
    }

    open fun onShot(type: Chip.ChipType, power: Int): Boolean
            /** function that gets called when a the attacker gets "hit".
             * @param type the chip's type that effectuates the attack
             * @param power strength (amount) of the shot
             * @return true if the attacker gets destroyed, false otherwise
             */
    {
        when (type)
        {
            Chip.ChipType.SUB ->
            {
                val newNumber =  attackerData.number.toLong() - power
                if (newNumber < 0)
                {
                    theNetwork.theGame.gameActivity.theGameView.theEffects?.explode(this)
                    theNetwork.theGame.scoreBoard.addCash(attackerData.bits)
                    return true
                }
                else
                    changeNumberTo(newNumber.toULong())
            }
            Chip.ChipType.SHR ->
            {
                val factor: UInt = powerOfTwo[power] ?: 1u
                changeNumberTo((attackerData.number / factor))
            }
            Chip.ChipType.MEM ->
            {
                theNetwork.theGame.gameActivity.theGameView.theEffects?.fade(this)
                theNetwork.theGame.scoreBoard.addCash(attackerData.bits)
                return false // remove() is done after fading
            }
            Chip.ChipType.ADD ->
            {
                val newNumber =  attackerData.number.toLong() + power
                changeNumberTo(newNumber.toULong())
            }
            Chip.ChipType.SHL ->
            {
                val factor: UInt = powerOfTwo[power] ?: 1u
                changeNumberTo((attackerData.number * factor))

            }
            else -> return false
        }
        return false
    }

    override fun getPositionOnScreen(): Pair<Int, Int>
            /** given grid coordinates, calculate the actual pixel coordinates.
             * @return The position as pair of pixels (x, y),
             * or (0, 0) if the viewport is undefined or invalid.
             */
    {
        posOnGrid?.let { return theNetwork.theGame.viewport.gridToViewport(it) }
        /* else, if posOnGrid == null: */
        return Pair(0, 0)
    }

    override fun remove()
    {
        onLink?.let {
            it.node1.distanceToVehicle.remove(this)
            it.node2.distanceToVehicle.remove(this)
            theNetwork.vehicles.remove(this)
        }
    }

    fun createBitmap(text: String)
    {
        // define colours
        val textPaint = Paint()
        val blurPaint = Paint()
        val blurMaskFilter = BlurMaskFilter(11f, BlurMaskFilter.Blur.OUTER)
        if (attackerData.representation == Representation.BINARY) {
            textPaint.color = theNetwork.theGame.resources.getColor(R.color.attackers_foreground_bin)
            blurPaint.color = theNetwork.theGame.resources.getColor(R.color.attackers_glow_bin)
        }
        else {
            textPaint.color = theNetwork.theGame.resources.getColor(R.color.attackers_foreground_hex)
            blurPaint.color = theNetwork.theGame.resources.getColor(R.color.attackers_glow_hex)
        }

        textPaint.textSize = numberFontSize
        textPaint.alpha = 255
        // paint.typeface = MONOSPACE
        textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        numberBitmap = Bitmap.createBitmap(bounds.width()+8, bounds.height()+10, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(numberBitmap)
        val rect = Rect(0, 0, numberBitmap.width, numberBitmap.height)

        /* draw the actual (non-blurred) text */
        rect.displayTextCenteredInRect(canvas, text, textPaint)
        val alpha: Bitmap = numberBitmap.extractAlpha()

        /* create a transparent black background to have more contrast */
        val paint = Paint()
        paint.color = theNetwork.theGame.resources.getColor(R.color.attackers_background)
        // canvas.drawRect(rect, paint)

        /* use blurred image to create glow */
        blurPaint.maskFilter = blurMaskFilter
        blurPaint.style = Paint.Style.FILL
        val blurCanvas = Canvas(numberBitmap)
        blurCanvas.drawBitmap(alpha, 0f, 0f, blurPaint)

        textPaint.color = theNetwork.theGame.resources.getColor(R.color.attackers_foreground_bin)
        textPaint.maskFilter = null
    }

    override fun update() {
        super.update()
        endNode?.notify(this, distanceToNextNode)
        startNode?.notify(this, -distanceFromLastNode)

        // animation, if any
        if (animationCount>0)
            animationCount--
    }

    override fun display(canvas: Canvas, viewport: Viewport) {
        if (posOnGrid == null)
            return
        actualRect = Rect(0, 0, numberBitmap.width, numberBitmap.height)
        actualRect.scaleAndSetCenter(getPositionOnScreen(), scale)
        actualRect.offset(displacement.first, displacement.second)

        if (animationCount>0)
            oldNumberBitmap?.let {
                val divider = numberBitmap.height * animationCount / animationCountMax
                val newSource = Rect(0, 0, numberBitmap.width, numberBitmap.height-divider)
                val oldSource = Rect(0, numberBitmap.height-divider, numberBitmap.width, numberBitmap.height)
                val newTarget =  Rect(0, divider, numberBitmap.width, numberBitmap.height)
                newTarget.offsetTo(actualRect.left, actualRect.top+divider)
                val oldTarget = Rect(0, 0, numberBitmap.width, divider)
                oldTarget.offsetTo(actualRect.left, actualRect.top)
                canvas.drawBitmap(numberBitmap, newSource, newTarget, paintBitmap)
                canvas.drawBitmap(it, oldSource, oldTarget, paintBitmap)
        }
        else
            canvas.drawBitmap(numberBitmap, null, actualRect, paintBitmap)
    }

    override fun fadeDone(type: Fader.Type) {
        remove()
    }

    override fun setOpacity(opacity: Float) {
        scale = opacity
        paintBitmap.alpha = (255 * opacity).toInt()
    }

    fun onDown(event: MotionEvent): Boolean {
        val boundingRecSize = 50
        val boundingRect = Rect(0, 0, boundingRecSize, boundingRecSize)
        boundingRect.setCenter(actualRect.center())
        if (boundingRect.contains(event.x.toInt(), event.y.toInt())) // gesture is inside this object
        {
            invertNumber()
            return true
        }
        else
            return false
    }


    companion object {
        fun makeNumber(attacker: Attacker): String
        {
            val text: String
            if (attacker.attackerData.representation == Representation.BINARY)
                text = attacker.attackerData.number.toString(radix=2).padStart(attacker.attackerData.binaryDigits, '0')
            else
                text = "x" + attacker.attackerData.number.toString(radix=16).uppercase().padStart(attacker.attackerData.hexDigits, '0')
            attacker.createBitmap(text)
            return text
        }

        fun log16(v: Float): Float
        {
            val l16 = log2(16f)
            return log2(v) / l16
        }

        val powerOfTwo: HashMap<Int, UInt> = hashMapOf(
            0 to 1u, 1 to 2u, 2 to 4u, 3 to 8u, 4 to 16u,
            5 to 32u, 6 to 64u, 7 to 128u, 8 to 256u)

        val maskBinary: HashMap<Int, ULong> = hashMapOf(
            1 to 0x01uL, 2 to 0x03uL,  4 to 0x0FuL,
            6 to 0x3FuL, 8 to 0xFFuL,
            12 to 0x0FFFuL, 16 to 0x7FFFuL, 32 to 0xFFFFuL, 64 to 0xFFFFFFFFuL )

        val maskHex: HashMap<Int, ULong> = hashMapOf(
            1 to 0x0FuL, 2 to 0xFFuL,  4 to 0xFFFFuL,
            6 to 0xFFFFFFuL, 8 to 0xFFFFFFuL,
            12 to 0xFFFFFFFFFFFFuL, 16 to 0xFFFFFFFFFFFFFFFFuL)

        fun createFromData(stage: Stage, data: Data): Attacker
        {
            val attacker = Attacker(stage.network, data.representation, data.number, data.vehicle.speed)
            attacker.data = data.vehicle
            attacker.attackerData = data
            attacker.onTrack = stage.tracks[data.vehicle.trackId]
            attacker.setOntoLink(stage.network.links[data.vehicle.linkId], stage.chips[data.vehicle.startNodeId])
            attacker.setCurrentSpeed()
            return attacker
        }
    }
}
