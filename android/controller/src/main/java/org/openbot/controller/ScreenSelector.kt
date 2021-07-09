/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.openbot.controller

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import org.openbot.controller.databinding.ActivityFullscreenBinding

data class ScreenSelector (val binding: ActivityFullscreenBinding) {

    init {
        binding.mainScreen.setupDoubleTap(::showButtons)
        subscribe("CONTROL_MODE", ::onDataReceived)
    }

    private fun hideButtons() {
        binding.botSetupButtons.hide()
    }

    private fun hideSliders() {
        binding.driveModeSlidersLayout.hide()
        binding.controlModeTiltLayout.hide()
    }

    private fun showButtons() {
        binding.controlModeTiltLayout.stop()
        binding.botSetupButtons.show()
        hideSliders()
    }

    fun hideControls() {
        binding.mainScreen.hide()
        binding.splashScreen.show()
    }

    fun showControls() {
        binding.splashScreen.hide()
        binding.mainScreen.show()

        showButtons()
    }

    @SuppressLint("CheckResult")
    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        StatusEventBus.addSubject(subject)
        StatusEventBus.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)
        })
    }

    private fun onDataReceived(data: String) {
        if (data == "sliders") {
            binding.driveModeSlidersLayout.show()
            binding.controlModeTiltLayout.stop()
        }
        if (data == "tilt") {
            binding.driveModeSlidersLayout.hide()
            binding.controlModeTiltLayout.start()
            binding.doubleTapMessage.start()
        }

        binding.botSetupButtons.hide()
    }
}
