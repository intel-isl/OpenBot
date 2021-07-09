/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:56 p.m.
 */

package org.openbot.controller.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.openbot.controller.ConnectionSelector
import org.openbot.controller.StatusEventBus

open class ButtonWithBorder @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        strokeWidth = 2
        cornerRadius = 20
        setTextColor(Color.rgb(0xf6, 0xf6, 0xf6)) // colorPrimary
        strokeColor = ColorStateList.valueOf(Color.rgb(0xf6,0xf6,0xf6)) // colorPrimary
    }

    protected override fun offState() {
        backgroundTintList = ColorStateList.valueOf(Color.rgb(0x40, 0x40, 0x41)) // colorPrimaryDark
    }

    protected override fun onState() {
        backgroundTintList = ColorStateList.valueOf(Color.BLACK) // colorPrimary
    }
}
