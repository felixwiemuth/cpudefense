package com.example.cpudefense.networkmap

import android.graphics.Rect
import com.example.cpudefense.Game

class Viewport {
    data class Data(
        var gridSize: Rect = Rect(0, 0, 100, 100)
    )

    var viewportWidth: Int = 0
    var viewportHeight: Int = 0
    var screen = Rect()
    var gridSizeX: Int = 0
    var gridSizeY: Int = 0
    var scaleX = 1.0f
    var scaleY = 1.0f
    var isValid = false

    fun setScreenSize(width: Int, height: Int)
    {
        if (width == 0 || height == 0)
            isValid = false
        else
        {
            screen = Rect(0, 0, width, height)
            this.viewportWidth = width - 2 * Game.viewportMargin
            this.viewportHeight = height - 2 * Game.viewportMargin
            calculateScale()
        }
    }

    fun setViewportSize(gridSizeX: Int, gridSizeY: Int)
    {
        this.gridSizeX = gridSizeX
        this.gridSizeY = gridSizeY
        calculateScale()
    }

    private fun calculateScale()
    {
        val width = viewportWidth.toFloat()
        val height = viewportHeight.toFloat()
        if (width == 0f || height == 0f)
            isValid = false
        else if (gridSizeX>0 && gridSizeY>0) {
            scaleX = viewportWidth.toFloat() / gridSizeX
            scaleY = viewportHeight.toFloat() / gridSizeY
            isValid = true
        }
        else
            isValid = false  // screen size is known, but grid size isn't
    }

    fun gridToViewport(gridPos: GridCoord): Pair<Int, Int>
    {
        var posX = gridPos.x * scaleX + Game.viewportMargin
        var posY = gridPos.y * scaleY + Game.viewportMargin
        return Pair(posX.toInt(), posY.toInt())
    }

    fun isInRightHalfOfViewport(posX: Int): Boolean
    {
        return posX > viewportWidth / 2
    }

    fun getRect(): Rect
    {
        return Rect(0, 0, viewportWidth, viewportHeight)
    }

}