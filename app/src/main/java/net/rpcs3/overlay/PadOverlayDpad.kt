package net.rpcs3.overlay

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.roundToInt

private enum class DpadButton(val bit: Int) {
    Top(1 shl 0), Left(1 shl 1), Right(1 shl 2), Bottom(1 shl 3);
}

private class DpadState(var mask: Int = 0) {
    fun isActive(btn: DpadButton): Boolean {
        return (mask and btn.bit) == btn.bit
    }

    fun setBtn(btn: DpadButton) {
        mask = mask or btn.bit
    }

    fun clear() {
        mask = 0
    }
}

class PadOverlayDpad(
    private val context: Context,
    resources: Resources,
    private val buttonWidth: Int,
    private val buttonHeight: Int,
    private val area: Rect,
    private val digitalIndex: Int,
    imgTop: Bitmap,
    private val topBit: Int,
    imgLeft: Bitmap,
    private val leftBit: Int,
    imgRight: Bitmap,
    private val rightBit: Int,
    imgBottom: Bitmap,
    private val bottomBit: Int,
    private val multitouch: Boolean
) {
    private val drawableTop = imgTop.toDrawable(resources)
    private val drawableLeft = imgLeft.toDrawable(resources)
    private val drawableRight = imgRight.toDrawable(resources)
    private val drawableBottom = imgBottom.toDrawable(resources)
    private val locked = arrayOf(-1, -1)
    private val btnState = arrayOf(DpadState(), DpadState())
    private val digitalBits = arrayOf(0, 0)
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("PadOverlayPrefs", Context.MODE_PRIVATE) }
    private var offsetX = 0
    private var offsetY = 0
    var idleAlpha: Int = 255
    var dragging: Boolean = false

    init {
        loadSavedPosition()
    }

    fun contains(x: Int, y: Int) = area.contains(x, y)

    fun startDragging(x: Int, y: Int) {
        dragging = true
        offsetX = x - area.left
        offsetY = y - area.top
    }

    fun updatePosition(x: Int, y: Int, force: Boolean = false) {
        if (!dragging && !force) return

        val newLeft = x - offsetX
        val newTop = y - offsetY
        val newRight = newLeft + area.width()
        val newBottom = newTop + area.height()

        area.set(newLeft, newTop, newRight, newBottom)
        updateBounds()
        
        prefs.edit()
            .putInt("dpad_x", area.left)
            .putInt("dpad_y", area.top)
            .apply()
    }

    fun stopDragging() {
        dragging = false
    }

    fun setScale(percent: Int) {
        val scaleFactor = percent / 100f
        val newWidth = (1024 * scaleFactor).roundToInt()
        val newHeight = (1024 * scaleFactor).roundToInt()
        val centerX = area.centerX()
        val centerY = area.centerY()

        area.set(centerX - newWidth / 2, centerY - newHeight / 2, centerX + newWidth / 2, centerY + newHeight / 2)
        updateBounds()

        prefs.edit()
            .putInt("${topBit.toString()}_x", area.left)
            .putInt("${topBit.toString()}_y", area.top)
            .putInt("${topBit.toString()}_scale", percent)
            .apply()
    }

    fun setOpacity(percent: Int) {
        idleAlpha = (255 * percent / 100).coerceIn(0, 255)
        prefs.edit().putInt("dpad_opacity", idleAlpha).apply()
    }

    fun resetConfigs() {
        prefs.edit().clear().apply()
        area.set(100, 100, 250, 250)
        setOpacity(50)
    }

    private fun loadSavedPosition() {
        val x = prefs.getInt("${topBit.toString()}_x", area.left)
        val y = prefs.getInt("${topBit.toString()}_y", area.top)
        val scale = prefs.getInt("${topBit.toString()}_scale", 50)
        updatePosition(x, y, force = true)
        setScale(scale)
    }

    /*fun measureDefaultScale(): Int {
        // TODO: implement me
    }*/

    fun getInfo(): Triple<String, Int, Int> {
        return Triple("Dpad", prefs.getInt("${topBit.toString()}_scale", 50), 50)
    }

    private fun updateBounds() {
        drawableTop.setBounds(
            area.centerX() - buttonWidth / 2,
            area.top,
            area.centerX() + buttonWidth / 2,
            area.top + buttonHeight,
        )

        drawableBottom.setBounds(
            area.centerX() - buttonWidth / 2,
            area.bottom - buttonHeight,
            area.centerX() + buttonWidth / 2,
            area.bottom,
        )

        drawableLeft.setBounds(
            area.left,
            area.centerY() - buttonWidth / 2,
            area.left + buttonHeight,
            area.centerY() + buttonWidth / 2,
        )

        drawableRight.setBounds(
            area.right - buttonHeight,
            area.centerY() - buttonWidth / 2,
            area.right,
            area.centerY() + buttonWidth / 2,
        )
    }

    fun onTouch(event: MotionEvent, pointerIndex: Int, padState: State): Boolean {
        val action = event.actionMasked
        var hit = false

        for (touchIndex in 0..1) {
            if (!multitouch && touchIndex > 0) {
                break
            }

            var activePointerIndex = pointerIndex

            if (locked[touchIndex] != -1 && action == MotionEvent.ACTION_MOVE) {
                activePointerIndex = -1
                for (i in 0..<event.pointerCount) {
                    if (locked[touchIndex] == event.getPointerId(i)) {
                        activePointerIndex = i
                        break
                    }
                }

                if (activePointerIndex == -1) {
                    continue
                }
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || (action == MotionEvent.ACTION_MOVE && locked[touchIndex] != -1)) {
                if (action != MotionEvent.ACTION_MOVE) {
                    if (locked[touchIndex] == -1) {
                        locked[touchIndex] = event.getPointerId(pointerIndex)
                    } else if (locked[touchIndex] != event.getPointerId(pointerIndex)) {
                        continue
                    }
                }

                val x = event.getX(activePointerIndex)
                val y = event.getY(activePointerIndex)

                val leftDistance = x - area.left
                val topDistance = y - area.top
                val bottomDistance = area.bottom - y
                val rightDistance = area.right - x
                val distanceWidth = area.width() / 3.5

                val left = leftDistance < distanceWidth
                val right = !left && rightDistance < distanceWidth
                val top = topDistance < distanceWidth
                val bottom = !top && bottomDistance < distanceWidth

                hit = true

                digitalBits[touchIndex] = 0
                btnState[touchIndex].clear()

                if (top) {
                    btnState[touchIndex].setBtn(DpadButton.Top)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or topBit
                }

                if (left) {
                    btnState[touchIndex].setBtn(DpadButton.Left)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or leftBit
                }

                if (right) {
                    btnState[touchIndex].setBtn(DpadButton.Right)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or rightBit
                }

                if (bottom) {
                    btnState[touchIndex].setBtn(DpadButton.Bottom)
                    digitalBits[touchIndex] = digitalBits[touchIndex] or bottomBit
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (locked[touchIndex] != -1 && (action == MotionEvent.ACTION_CANCEL || event.getPointerId(
                        pointerIndex
                    ) == locked[touchIndex])
                ) {
                    hit = true
                    digitalBits[touchIndex] = 0
                    btnState[touchIndex].clear()
                    locked[touchIndex] = -1
                }
            }

            if (hit) {
                break
            }
        }

        padState.digital[digitalIndex] =
            (padState.digital[digitalIndex] and (leftBit or rightBit or topBit or bottomBit).inv()) or digitalBits[0] or digitalBits[1]

        return hit || area.contains(
            event.getX(pointerIndex).toInt(), event.getY(pointerIndex).toInt()
        )
    }
    
    fun getBounds(): Rect {
        return drawableTop.bounds
    }
    
    fun draw(canvas: Canvas) {
        drawableLeft.alpha =
            if (btnState[0].isActive(DpadButton.Left) || btnState[1].isActive(DpadButton.Left)) 255 else idleAlpha
        drawableLeft.draw(canvas)

        drawableRight.alpha =
            if (btnState[0].isActive(DpadButton.Right) || btnState[1].isActive(DpadButton.Right)) 255 else idleAlpha
        drawableRight.draw(canvas)

        drawableBottom.alpha =
            if (btnState[0].isActive(DpadButton.Bottom) || btnState[1].isActive(DpadButton.Bottom)) 255 else idleAlpha
        drawableBottom.draw(canvas)

        drawableTop.alpha =
            if (btnState[0].isActive(DpadButton.Top) || btnState[1].isActive(DpadButton.Top)) 255 else idleAlpha
        drawableTop.draw(canvas)
    }
}
