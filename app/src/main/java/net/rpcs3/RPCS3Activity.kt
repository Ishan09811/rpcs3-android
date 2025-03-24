package net.rpcs3

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import android.util.Log
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.isInvisible
import net.rpcs3.databinding.ActivityRpcs3Binding
import net.rpcs3.dialogs.AlertDialogQueue
import net.rpcs3.overlay.State
import kotlin.concurrent.thread

class RPCS3Activity : Activity() {
    private lateinit var binding: ActivityRpcs3Binding
    private lateinit var unregisterUsbEventListener: () -> Unit
    private var gamePadState: State = State()
  
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRpcs3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        unregisterUsbEventListener = listenUsbEvents(this)
        enableFullScreenImmersive()

        binding.oscToggle.setOnClickListener { 
            binding.padOverlay.isInvisible = !binding.padOverlay.isInvisible 
            binding.oscToggle.setImageResource(if (binding.padOverlay.isInvisible) R.drawable.ic_osc_off else R.drawable.ic_show_osc)
        }

        thread {
            if (RPCS3.getState() != EmulatorState.Stopped) {
                Log.w("RPCS3 State", RPCS3.getState().name)

                if (RPCS3.getState() != EmulatorState.Stopping) {
                    RPCS3.instance.kill()
                }

                while (RPCS3.getState() != EmulatorState.Stopped) {
                    Thread.sleep(300)
                }
            }

            Log.w("RPCS3 State", RPCS3.getState().name)

            val bootResult = RPCS3.boot(intent.getStringExtra("path")!!)

            if (bootResult != BootResult.NoErrors) {
                AlertDialogQueue.showDialog("Boot Failed", "Error: ${bootResult.name}")
                finish()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_UP.bit
            KeyEvent.KEYCODE_DPAD_DOWN -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_DOWN.bit
            KeyEvent.KEYCODE_DPAD_LEFT -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_LEFT.bit
            KeyEvent.KEYCODE_DPAD_RIGHT -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_RIGHT.bit
            KeyEvent.KEYCODE_BUTTON_A -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_CROSS.bit
            KeyEvent.KEYCODE_BUTTON_B -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_CIRCLE.bit
            KeyEvent.KEYCODE_BUTTON_X -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_SQUARE.bit
            KeyEvent.KEYCODE_BUTTON_Y -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_TRIANGLE.bit
            KeyEvent.KEYCODE_BUTTON_L1 -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_L1.bit
            KeyEvent.KEYCODE_BUTTON_R1 -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_R1.bit
            KeyEvent.KEYCODE_BUTTON_L2 -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_L2.bit
            KeyEvent.KEYCODE_BUTTON_R2 -> gamePadState.digital2 = gamePadState.digital2 or Digital2Flags.CELL_PAD_CTRL_R2.bit
            KeyEvent.KEYCODE_BUTTON_START -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_START.bit
            KeyEvent.KEYCODE_BUTTON_SELECT -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_SELECT.bit
            KeyEvent.KEYCODE_BUTTON_THUMBL -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_L3.bit
            KeyEvent.KEYCODE_BUTTON_THUMBR -> gamePadState.digital1 = gamePadState.digital1 or Digital1Flags.CELL_PAD_CTRL_R3.bit
        }
        sendGamepadData()
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_UP.bit.inv()
            KeyEvent.KEYCODE_DPAD_DOWN -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_DOWN.bit.inv()
            KeyEvent.KEYCODE_DPAD_LEFT -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_LEFT.bit.inv()
            KeyEvent.KEYCODE_DPAD_RIGHT -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_RIGHT.bit.inv()
            KeyEvent.KEYCODE_BUTTON_A -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_CROSS.bit.inv()
            KeyEvent.KEYCODE_BUTTON_B -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_CIRCLE.bit.inv()
            KeyEvent.KEYCODE_BUTTON_X -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_SQUARE.bit.inv()
            KeyEvent.KEYCODE_BUTTON_Y -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_TRIANGLE.bit.inv()
            KeyEvent.KEYCODE_BUTTON_L1 -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_L1.bit.inv()
            KeyEvent.KEYCODE_BUTTON_R1 -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_R1.bit.inv()
            KeyEvent.KEYCODE_BUTTON_L2 -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_L2.bit.inv()
            KeyEvent.KEYCODE_BUTTON_R2 -> gamePadState.digital2 = gamePadState.digital2 and Digital2Flags.CELL_PAD_CTRL_R2.bit.inv()
            KeyEvent.KEYCODE_BUTTON_START -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_START.bit.inv()
            KeyEvent.KEYCODE_BUTTON_SELECT -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_SELECT.bit.inv()
            KeyEvent.KEYCODE_BUTTON_THUMBL -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_L3.bit.inv()
            KeyEvent.KEYCODE_BUTTON_THUMBR -> gamePadState.digital1 = gamePadState.digital1 and Digital1Flags.CELL_PAD_CTRL_R3.bit.inv()
        }
        sendGamepadData()
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        event?.let {
            gamePadState.leftStickX = (it.getAxisValue(MotionEvent.AXIS_X) * 127).toInt()
            gamePadState.leftStickY = (it.getAxisValue(MotionEvent.AXIS_Y) * 127).toInt()
            gamePadState.rightStickX = (it.getAxisValue(MotionEvent.AXIS_Z) * 127).toInt()
            gamePadState.rightStickY = (it.getAxisValue(MotionEvent.AXIS_RZ) * 127).toInt()
        }
        sendGamepadData()
        return super.onGenericMotionEvent(event)
    }

    private fun sendGamepadData() {
        RPCS3.instance.overlayPadData(gamePadState.digital1, gamePadState.digital2, gamePadState.leftStickX, gamePadState.leftStickY, gamePadState.rightStickX, gamePadState.rightStickY)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUsbEventListener()
    }

    private fun enableFullScreenImmersive() {
        with(window) {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            val insetsController = WindowInsetsControllerCompat(this, decorView)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyInsetsToPadOverlay()
    }

    private fun applyInsetsToPadOverlay() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.padOverlay) { view, windowInsets ->
            // I don't think we need `displayCutout` insets here as well
            // Since there is hardly any overlay overlapping with it
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
                topMargin = insets.top
                bottomMargin = insets.bottom
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableFullScreenImmersive()
    }
}
