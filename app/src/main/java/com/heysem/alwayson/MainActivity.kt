package com.heysem.alwayson

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * Full-screen black "always on display".
 *
 * Needs no permissions:
 *  - FLAG_KEEP_SCREEN_ON keeps the screen awake only while this screen is open.
 *  - Battery level comes from the sticky ACTION_BATTERY_CHANGED broadcast.
 *  - Next alarm comes from AlarmManager.nextAlarmClock.
 *  - setShowWhenLocked lets it stay visible on top of the lock screen.
 */
class MainActivity : Activity() {

    private lateinit var aod: AodView
    private val handler = Handler(Looper.getMainLooper())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBattery(intent)
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshNow()
    }

    private val minuteTick = object : Runnable {
        override fun run() {
            refreshNow()
            // Re-arm exactly at the start of the next minute.
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000L - (now % 60_000L) + 50L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)

        aod = AodView(this).apply {
            onCloseTapped = { finishAndRemoveTask() }
            onScreenTapped = { toggleDim() }
            onLongPressed = { showBackgroundMenu() }
            background = Backgrounds.load(this@MainActivity)
        }
        setContentView(aod)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { applyBattery(it) }
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        })
        handler.removeCallbacks(minuteTick)
        minuteTick.run()
    }

    override fun onPause() {
        handler.removeCallbacks(minuteTick)
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(timeReceiver) }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Long-press menu. Uses the system photo picker, which needs no storage permission. */
    private fun showBackgroundMenu() {
        val options = arrayOf("Mountain lake", "Pick my own photo", "Pure black (saves most battery)")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Background")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { Backgrounds.setMode(this, Backgrounds.MODE_MOUNTAIN); aod.background = Backgrounds.load(this) }
                    1 -> pickPhoto()
                    2 -> { Backgrounds.setMode(this, Backgrounds.MODE_BLACK); aod.background = null }
                }
            }
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickPhoto() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        runCatching { startActivityForResult(intent, REQ_PHOTO) }
    }

    @Deprecated("Plain Activity API; fine for this tiny app")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK || uri == null) return
        // Shrinking and blurring is quick, but keep it off the UI thread anyway.
        Thread {
            val ok = Backgrounds.importPhoto(this, uri)
            runOnUiThread { if (ok) aod.background = Backgrounds.load(this) }
        }.start()
    }

    private fun refreshNow() {
        val alarm = (getSystemService(ALARM_SERVICE) as AlarmManager).nextAlarmClock
        aod.nextAlarmMillis = alarm?.triggerTime
        aod.tick()
    }

    private fun applyBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level >= 0 && scale > 0) aod.batteryPercent = level * 100 / scale
        aod.charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        aod.invalidate()
    }

    /** Tap anywhere: switch between very dim and the phone's normal brightness. */
    private fun toggleDim() {
        val lp = window.attributes
        lp.screenBrightness =
            if (lp.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) 0.02f
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        aod.dimmed = lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        aod.invalidate()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    companion object {
        private const val REQ_PHOTO = 41
    }
}
