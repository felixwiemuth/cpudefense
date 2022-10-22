package com.example.cpudefense.gameElements

import android.graphics.*
import android.view.MotionEvent
import com.example.cpudefense.Game
import com.example.cpudefense.networkmap.Network
import com.example.cpudefense.networkmap.Viewport
import com.example.cpudefense.scale
import com.example.cpudefense.setCenter

class Cpu(network: Network, gridX: Int, gridY: Int): Chip(network, gridX, gridY)
{
    data class CpuData
    (
        var hits: Int
        )

    var cpuData = CpuData(hits = 0)
    override var actualRect: Rect? = null

    override var bitmap: Bitmap? = network.theGame.cpuImage
    private val maxAnimationCount: Int = 32
    private var animationCount: Int = 0

    init {
        data.range = 1.0f
        chipData.type = ChipType.CPU
        actualRect = Rect(0, 0, 100, 100)
    }

    /*
    fun attackersInRange(): List<Attacker> {
        return distanceToVehicle.keys.filter { attackerInRange(it as Attacker) }
            .map { it as Attacker }
    }
    */

    override fun update() {
        super.update()
        val attackers = attackersInRange()
        if (attackers.isNotEmpty())
        {
            scoreHit()
            animationCount = maxAnimationCount
            attackers[0].remove()
        }
    }

    override fun display(canvas: Canvas, viewport: Viewport)
    {
        actualRect?.let { rect ->
            rect.setCenter(viewport.gridToViewport(posOnGrid))
            val paint = Paint()
            bitmap?.let()
            { canvas.drawBitmap(it, null, rect, paint) }

            if (animationCount>0)
            {
                var animationRect = Rect(rect)
                animationRect.scale((1.2f - (animationCount/maxAnimationCount.toFloat())))
                paint.color = Color.RED
                paint.alpha = (animationCount * 255)/maxAnimationCount
                animationCount--
                canvas.drawRect(animationRect, paint)
            }
        }
    }

    fun scoreHit()
    /** function that gets called when an attacker reaches the CPU
     */
    {
        cpuData.hits++
        theNetwork?.theGame?.removeOneLife()
    }

    override fun onDown(event: MotionEvent): Boolean {
        /* pause the game when touched */
        if (actualRect?.contains(event.x.toInt(), event.y.toInt()) ?: false)  {
            theNetwork?.let { it.theGame.state.phase = Game.GamePhase.PAUSED }
            return true
        }
        else
            return false
    }
}