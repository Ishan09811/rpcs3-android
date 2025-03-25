package net.rpcs3.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import net.rpcs3.Digital1Flags
import net.rpcs3.Digital2Flags
import net.rpcs3.R
import net.rpcs3.RPCS3
import kotlin.math.min

private const val idleAlpha = (0.3 * 255).toInt()

data class State(
    val digital: IntArray = IntArray(2),
    var leftStickX: Int = 127,
    var leftStickY: Int = 127,
    var rightStickX: Int = 127,
    var rightStickY: Int = 127
)

class PadOverlay(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs) {
    private val buttons: Array<PadOverlayButton>
    private val dpad: PadOverlayDpad
    private val triangleSquareCircleCross: PadOverlayDpad
    private val state = State()
    private val leftStick: PadOverlayStick
    private val rightStick: PadOverlayStick
    private val floatingSticks = arrayOf<PadOverlayStick?>(null, null)
    private val sticks = mutableListOf<PadOverlayStick>()

    init {
        val metrics = context!!.resources.displayMetrics
        val totalWidth = metrics.widthPixels
        val totalHeight = metrics.heightPixels
        val sizeHint = min(totalHeight, totalWidth)
        val buttonSize = sizeHint / 10

        val btnAreaW = buttonSize * 3
        val btnAreaH = buttonSize * 3

        val btnAreaX = totalWidth - btnAreaW - buttonSize
        val btnAreaY = totalHeight - btnAreaH - buttonSize / 2
        val btnDistance = buttonSize / 8

        val dpadW = buttonSize * 3 - btnDistance / 2
        val dpadH = buttonSize * 3 - btnDistance / 2

        val dpadAreaX = buttonSize
        val dpadAreaY = btnAreaY

        val startSelectSize = (buttonSize * 1.5).toInt()
        val btnStartX = totalWidth / 2 + buttonSize * 2
        val btnStartY = buttonSize / 2
        val btnSelectX = totalWidth / 2 - startSelectSize - buttonSize * 2
        val btnSelectY = btnStartY

        val btnL2X = buttonSize
        val btnL2Y = buttonSize

        val btnL1X = btnL2X
        val btnL1Y = btnL2Y + buttonSize + buttonSize / 2

        val btnR2X = totalWidth - buttonSize * 2
        val btnR2Y = btnL2Y

        val btnR1X = btnR2X
        val btnR1Y = btnR2Y + buttonSize + buttonSize / 2

        dpad = createDpad(
            dpadAreaX, dpadAreaY, dpadW, dpadH,
            dpadW / 2,
            dpadH / 2 - dpadH / 20,
            0,
            R.drawable.dpad_top,
            Digital1Flags.CELL_PAD_CTRL_UP.bit,
            R.drawable.dpad_left,
            Digital1Flags.CELL_PAD_CTRL_LEFT.bit,
            R.drawable.dpad_right,
            Digital1Flags.CELL_PAD_CTRL_RIGHT.bit,
            R.drawable.dpad_bottom,
            Digital1Flags.CELL_PAD_CTRL_DOWN.bit,
            false
        )

        triangleSquareCircleCross = createDpad(
            btnAreaX - buttonSize / 2, btnAreaY, buttonSize * 3, buttonSize * 3,
            buttonSize,
            buttonSize,
            1,
            R.drawable.triangle,
            Digital2Flags.CELL_PAD_CTRL_TRIANGLE.bit,
            R.drawable.square,
            Digital2Flags.CELL_PAD_CTRL_SQUARE.bit,
            R.drawable.circle,
            Digital2Flags.CELL_PAD_CTRL_CIRCLE.bit,
            R.drawable.cross,
            Digital2Flags.CELL_PAD_CTRL_CROSS.bit,
            true
        )

        leftStick = PadOverlayStick(
            resources,
            true,
            BitmapFactory.decodeResource(resources, R.drawable.left_stick_background),
            BitmapFactory.decodeResource(resources, R.drawable.left_stick)
        )
        rightStick = PadOverlayStick(
            resources,
            false,
            BitmapFactory.decodeResource(resources, R.drawable.right_stick_background),
            BitmapFactory.decodeResource(resources, R.drawable.right_stick)
        )

        leftStick.setBounds(0, 0, buttonSize * 2, buttonSize * 2)
        leftStick.alpha = idleAlpha
        rightStick.setBounds(0, 0, buttonSize * 2, buttonSize * 2)
        rightStick.alpha = idleAlpha


        val l3r3Size = (buttonSize * 1.5).toInt()
        val l3 = PadOverlayStick(
            resources,
            true,
            decodeScaledBitmap(R.drawable.left_stick_background, buttonSize * 2, buttonSize * 2),
            BitmapFactory.decodeResource(resources, R.drawable.l3),
            pressDigitalIndex = 0,
            pressBit = Digital1Flags.CELL_PAD_CTRL_L3.bit
        )
        l3.alpha = idleAlpha
        l3.setBounds(
            totalWidth / 2 - buttonSize * 2 - l3r3Size,
            (totalHeight - buttonSize * 2.3).toInt(),
            totalWidth / 2 - buttonSize * 2,
            totalHeight - (buttonSize * 2.3).toInt() + l3r3Size
        )

        val r3 = PadOverlayStick(
            resources,
            false,
            decodeScaledBitmap(R.drawable.right_stick_background, buttonSize * 2, buttonSize * 2),
            BitmapFactory.decodeResource(resources, R.drawable.r3),
            pressDigitalIndex = 0,
            pressBit = Digital1Flags.CELL_PAD_CTRL_R3.bit
        )
        r3.alpha = idleAlpha
        r3.setBounds(
            totalWidth / 2 + buttonSize * 2,
            totalHeight - (buttonSize * 2.3).toInt(),
            totalWidth / 2 + buttonSize * 2 + l3r3Size,
            totalHeight - (buttonSize * 2.3).toInt() + l3r3Size
        )

        sticks += l3
        sticks += r3

        buttons = arrayOf(
            createButton(
                R.drawable.start,
                btnStartX,
                btnStartY,
                startSelectSize,
                startSelectSize,
                Digital1Flags.CELL_PAD_CTRL_START,
                Digital2Flags.None
            ),
            createButton(
                R.drawable.select,
                btnSelectX,
                btnSelectY,
                startSelectSize,
                startSelectSize,
                Digital1Flags.CELL_PAD_CTRL_SELECT,
                Digital2Flags.None
            ),

            createButton(
                R.drawable.l1,
                btnL1X,
                btnL1Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_L1
            ),
            createButton(
                R.drawable.l2,
                btnL2X,
                btnL2Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_L2
            ),
            createButton(
                R.drawable.r1,
                btnR1X,
                btnR1Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_R1
            ),
            createButton(
                R.drawable.r2,
                btnR2X,
                btnR2Y,
                startSelectSize,
                startSelectSize,
                Digital1Flags.None,
                Digital2Flags.CELL_PAD_CTRL_R2
            ),
        )

        setWillNotDraw(false)
        requestFocus()

        setOnTouchListener { _, motionEvent ->
            var hit = false

            val action = motionEvent.actionMasked
            val pointerIndex =
                if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) motionEvent.actionIndex else 0
            val x = motionEvent.getX(pointerIndex).toInt()
            val y = motionEvent.getY(pointerIndex).toInt()
            val force =
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE

            if (force || dpad.contains(x, y)) {
                hit = dpad.onTouch(motionEvent, pointerIndex, state)
            }

            if (force || (!hit && triangleSquareCircleCross.contains(x, y))
            ) {
                hit = triangleSquareCircleCross.onTouch(motionEvent, pointerIndex, state)
            }

            buttons.forEach { button ->
                if (force || (!hit && button.contains(x, y))) {
                    hit = button.onTouch(motionEvent, pointerIndex, state)
                }
            }

            if (force || !hit) {
                for (i in sticks.indices) {
                    if (!force && (!sticks[i].contains(x, y) || floatingSticks[i] != null)) {
                        continue
                    }

                    val touchResult = sticks[i].onTouch(motionEvent, pointerIndex, state)
                    hit = if (touchResult < 0) {
                        true
                    } else {
                        touchResult == 1
                    }
                }
            }

            if (force || !hit) {
                for (i in floatingSticks.indices) {
                    val stick = floatingSticks[i] ?: continue
                    val touchResult = stick.onTouch(motionEvent, pointerIndex, state)
                    if (touchResult < 0) {
                        floatingSticks[i] = null
                        hit = true
                    } else {
                        hit = touchResult == 1
                    }
                }
            }

            RPCS3.instance.overlayPadData(
                state.digital[0],
                state.digital[1],
                state.leftStickX,
                state.leftStickY,
                state.rightStickX,
                state.rightStickY
            )

            if (!hit && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
                val xInFloatingArea = x > buttonSize * 2 && x < totalWidth - buttonSize * 2
                val yInFloatingArea = y > buttonSize && y < totalHeight - buttonSize
                var inFloatingArea = xInFloatingArea && yInFloatingArea
                if (!inFloatingArea && yInFloatingArea) {
                    if (x > buttonSize && x <= buttonSize * 2) {
                        inFloatingArea = true
                    }

                    if (x <= totalWidth - buttonSize && x >= totalWidth - buttonSize * 2) {
                        inFloatingArea = true
                    }
                }

                if (inFloatingArea) {
                    val stickIndex = if (x <= totalWidth / 2) 0 else 1
                    val stick = if (stickIndex == 0) leftStick else rightStick

                    if (floatingSticks[stickIndex] == null && !sticks[stickIndex].isActive()) {
                        floatingSticks[stickIndex] = stick
                        stick.onAdd(motionEvent, pointerIndex)
                        hit = true
                    }
                }
            }

            if (hit || force) {
                invalidate()
            }

            hit || performClick()
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        buttons.forEach { button -> button.draw(canvas) }
        dpad.draw(canvas)
        triangleSquareCircleCross.draw(canvas)
        sticks.forEach { it.draw(canvas) }
        floatingSticks.forEach { it?.draw(canvas) }
    }

    private fun createButton(
        resourceId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        digital1: Digital1Flags,
        digital2: Digital2Flags
    ): PadOverlayButton {
        val resources = context!!.resources
        val bitmap = decodeScaledBitmap(resourceId, width, height)
        val result = PadOverlayButton(resources, bitmap, digital1.bit, digital2.bit)
        result.setBounds(x, y, x + width, y + height)
        result.alpha = idleAlpha
        return result
    }

    private fun createDpad(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        buttonWidth: Int,
        buttonHeight: Int,
        digital: Int,
        upResource: Int, upBit: Int,
        leftResource: Int, leftBit: Int,
        rightResource: Int, rightBit: Int,
        downResource: Int, downBit: Int,
        multitouch: Boolean
    ): PadOverlayDpad {
        val resources = context!!.resources
        val upBitmap = decodeScaledBitmap(upResource, buttonWidth, buttonHeight)
        val leftBitmap = decodeScaledBitmap(leftResource, buttonWidth, buttonHeight)
        val rightBitmap = decodeScaledBitmap(rightResource, buttonWidth, buttonHeight)
        val downBitmap = decodeScaledBitmap(downResource, buttonWidth, buttonHeight)

        val result = PadOverlayDpad(
            resources, buttonWidth, buttonHeight, Rect(x, y, x + width, y + height), digital,
            upBitmap, upBit,
            leftBitmap, leftBit,
            rightBitmap, rightBit,
            downBitmap, downBit,
            multitouch
        )

        result.idleAlpha = idleAlpha
        return result
    }

    private fun decodeScaledBitmap(resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        val resources = context!!.resources
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeResource(resources, resId, this)
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
            inJustDecodeBounds = false
            inMutable = true
        }
        return BitmapFactory.decodeResource(resources, resId, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (width, height) = options.outWidth to options.outHeight
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
