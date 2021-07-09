package org.openbot.controller

import android.util.Log

class PhoneSensorToDualDriveConverter  {
    private val g = 9.81f

    fun convert(
        azimuth: Float?,
        pitch: Float?,
        roll: Float?
    ): DualDriveValues {
        var leftSpeed = 0f
        var rightSpeed = 0f
        var forwardSpeed = 0f

        if (inDeadZone(roll!!)) {
            return DualDriveValues(0f, 0f)
        }

        // get forward speed
        forwardSpeed = roll.div(g) // could be negative

        // adjust for turning
        leftSpeed = forwardSpeed + (pitch?.div(g/2) ?: 0f) * forwardSpeed
        rightSpeed = forwardSpeed - (pitch?.div(g/2) ?: 0f) * forwardSpeed

        var values = DualDriveValues(leftSpeed, rightSpeed)

        Log.i(
            "Converting",
            "azimuth: $azimuth, pitch: $pitch, roll: $roll -> left: ${values.left}, right: ${values.right}"
        )

        return values
    }

    inner class DualDriveValues(var left: Float, var right: Float) {
        private val MAX = 1.0f
        private val MIN = -1.0f

        init {
            left = clean(left)
            right = clean(right)
        }

        private fun clean(value: Float): Float {
            var ret = value

            if (value > MAX) {
                ret = MAX
            }
            if (value < MIN) {
                ret = MIN
            }
            return ret.round(3)
        }

        private fun Float.round(decimals: Int): Float {
            var multiplier = 1.0f
            repeat(decimals) { multiplier *= 10 }
            return kotlin.math.round(this * multiplier) / multiplier
        }

        fun reset() {
            left = 0f
            right = 0f
        }
    }

    fun inDeadZone(roll: Float): Boolean {
        return isWithin(roll, 0f, 1f)
    }

    private fun isWithin(value: Float?, desiredValue: Float?, tolerance: Float?): Boolean {
        return value!! in (desiredValue!! - tolerance!!)..(desiredValue + tolerance)
    }
}
