/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.openbot.controller.utils

import android.annotation.SuppressLint
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.processors.PublishProcessor
import java.util.*

object EventProcessor {

    val subscriber = Subscriber()

    private val eventProcessor: PublishProcessor<ProgressEvents> =
        PublishProcessor.create()

    class Subscriber {
        private val subscribers: Set<String> =
            LinkedHashSet<String>()

        fun start (name: String, onNext:Consumer<in ProgressEvents>, onError: Consumer<in Throwable>) {
            if (subscribers.contains(name)) {
                return // do not allow multiple subscribers with same name
            }

            eventProcessor.observeOn(AndroidSchedulers.mainThread()).doOnNext(onNext).doOnError(onError).subscribe({},onError )
            (subscribers as LinkedHashSet).add(name)
        }
    }
    val connectionEventFlowable = (eventProcessor as Flowable<ProgressEvents>)

    init {
    }

    fun onNext(e: ProgressEvents) {
        if (eventProcessor.hasSubscribers()) {
            return eventProcessor.onNext(e)
        } else {
            Log.d("EventProcessor:onNext", "----------- No subscribers")
        }
    }

    open class ProgressEvents(var payload: String = "") {

        object Init : ProgressEvents()

        object ConnectionStarted : ProgressEvents()
        object ConnectionSuccessful : ProgressEvents()
        object ConnectionFailed : ProgressEvents()
        object StartAdvertising : ProgressEvents()
        object Disconnecting : ProgressEvents()
        object Disconnected : ProgressEvents()
        object StopAdvertising : ProgressEvents()
        object AdvertisingFailed : ProgressEvents()
        object TemporaryConnectionProblem : ProgressEvents()
        object PhoneOnTable : ProgressEvents()
    }
}