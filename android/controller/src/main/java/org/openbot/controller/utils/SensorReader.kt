package org.openbot.controller.utils

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

object SensorReader {
    private var mSensorManager: SensorManager? = null
    private var mSensorAccelerometer: Sensor? = null
    private const val TAG: String = "SensorReader"
    private var eventListener: SensorEventListener? = null

    private const val g = 9.8f

    class Values {
        var azimuth: Float? = 0f
        var pitch: Float? = 0f
        var roll: Float? = 0f

        fun reset () {
            azimuth = 0f
            pitch = 0f
            roll = 0f
        }
    }
    val values: Values = Values()

    fun init(context: Context) {
        mSensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager

        mSensorAccelerometer = mSensorManager!!.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )

        eventListener = EventListener()
    }

    fun start() {
        (eventListener as EventListener).start()
    }

    fun stop(context: Context) {
        values.reset()
        (eventListener as EventListener).stop(context)
    }

    class EventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            values.azimuth = event?.values?.get(0)
            values.pitch = event?.values?.get(1)
            values.roll = event?.values?.get(2)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        fun start() {

            if (mSensorAccelerometer != null) {
                mSensorManager?.registerListener(
                    this, mSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }

        fun stop(context: Context) {

            // Unregister all sensor listeners in this callback so they don't
            // continue to use resources when the app is stopped.
            mSensorManager?.unregisterListener(this)
        }
    }
}