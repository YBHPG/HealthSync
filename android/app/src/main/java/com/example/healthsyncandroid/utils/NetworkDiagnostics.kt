package com.example.healthsyncandroid.utils

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkDiagnostics {
    fun getLocalIPv4Address(): String? {
        try {
            val ips = mutableListOf<String>()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        ips.add("${networkInterface.name}: ${address.hostAddress}")
                    }
                }
            }
            return if (ips.isEmpty()) null else ips.joinToString("\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
