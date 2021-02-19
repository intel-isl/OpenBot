package org.openbot.controller

object ConnectionFactory {
    fun get () :ILocalConnection {
        // return NearbyConnection
        // return WiFiDirectConnection
        return NetworkServiceConnection
    }
}