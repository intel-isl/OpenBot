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
import org.openbot.controller.utils.SensorReader

class ControllerModeTilt @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ButtonWithBorder(context, attrs, defStyleAttr) {

    init {
        SensorReader.init(context)
        setOnTouchListener(OnTouchListener("{command: {CONTROL_MODE: \"tilt\"}}"))
        subscribe("CONTROL_MODE", ::onDataReceived)
        offState()
    }

    private fun onDataReceived(data: String) {
        setOnOffStateConditions(data)
    }

    override fun setOnOffStateConditions(value: String) {
        if (value == "tilt") onState() else offState()
    }
}
