/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:57 p.m.
 */

package org.openbot.controller.customComponents

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import org.openbot.controller.ConnectionSelector
import org.openbot.controller.PhoneSensorToDualDriveConverter
import org.openbot.controller.utils.EventProcessor
import org.openbot.controller.utils.SensorReader
import org.openbot.controller.utils.Utils
import java.util.*
import kotlin.math.absoluteValue

class DriveModeTiltLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val tag: String = "DriveModeTiltLayout"
    private val sensorSampler: SensorSampler = SensorSampler()
    private val g = 9.81f
    private val phoneOnTableChecker = PhoneOnTableChecker()
    private val phoneAccelerometerToDualDriveConverted =
        PhoneSensorToDualDriveConverter()

    init {
    }

    fun start() {
        SensorReader.start()
        sensorSampler.start()
        show()
    }

    fun stop() {
        // ConnectionSelector.getConnection().sendMessage("{driveCmd: {l:0, r:0}}")
        sensorSampler.stop()
        SensorReader.stop(context)
        hide()
    }

    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = INVISIBLE
    }

    inner class PhoneOnTableChecker {

        private var lastTransmitted = 0L
        private var lastAzimuth: Float? = null
        private var lastPitch: Float? = null
        private var lastRoll: Float? = null
        private val minTimeOnTableMs = 1000L

        fun phoneOnTable(azimuth: Float?, pitch: Float?, roll: Float?): Boolean {
            if (Utils.isWithin(roll?.absoluteValue, g, 1.0f)
                && Utils.isWithin(pitch, lastPitch, 0.2f)
                && Utils.isWithin(azimuth, lastAzimuth, 0.2f)
            ) {
                if (lastTransmitted == 0L) {
                    lastTransmitted = System.currentTimeMillis()
                }
                if (System.currentTimeMillis() > (lastTransmitted + minTimeOnTableMs)) {
                    return true
                }
            } else {
                lastTransmitted = 0L
            }

            lastPitch = pitch
            lastRoll = roll
            lastAzimuth = azimuth

            return false
        }
    }

    inner class SensorSampler {
        private lateinit var timer: Timer

        fun start() {
            if (this::timer.isInitialized) {
                timer.cancel()
            }

            timer = Timer()
            var isRunning = false

            val task = object : TimerTask() {
                override fun run() {
                    val azimuth = SensorReader.values.azimuth
                    val pitch = SensorReader.values.pitch
                    val roll = SensorReader.values.roll

                    if (phoneOnTableChecker.phoneOnTable(azimuth, pitch, roll)) {
                        timer.cancel()
                        stop()

                        val event: EventProcessor.ProgressEvents =
                            EventProcessor.ProgressEvents.PhoneOnTable
                        EventProcessor.onNext(event)

                        return
                    }

                    isRunning = true
                    val sliderValues = phoneAccelerometerToDualDriveConverted.convert(azimuth, pitch, roll)

                    val msg = "{driveCmd: {l:${sliderValues.left}, r:${sliderValues.right}}}"
                    ConnectionSelector.getConnection().sendMessage(msg)
                }
            }

            timer.schedule(task, 0, 50 /*MS*/)
        }

        fun stop() {
            if (this::timer.isInitialized) {
                timer.cancel()
            }
        }
    }
}
