package com.kamilake.cbtoggle.watch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume

private const val TAG = "WatchDiscovery"

const val TYPE_PAIRING = "_adb-tls-pairing._tcp"
const val TYPE_CONNECT = "_adb-tls-connect._tcp"

class AdbService(val name: String, val host: String, val port: Int)

/** Finds the wireless debugging services that other devices on the same Wi-Fi advertise over mDNS. */
class WatchDiscovery(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)

    // Older platforms reject a second resolve while one is still running.
    private val resolveLock = Mutex()

    suspend fun find(type: String, timeoutMs: Long, accept: (AdbService) -> Boolean = { true }): AdbService? {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                Log.d(TAG, "found ${info.serviceName} ${info.serviceType}")
                found.trySend(info)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery of $serviceType failed: $errorCode")
                found.close()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            return withTimeoutOrNull(timeoutMs) {
                val local = localAddresses()
                for (info in found) {
                    val service = resolve(info) ?: continue
                    Log.d(TAG, "resolved ${service.name} -> ${service.host}:${service.port}")
                    if (service.host in local) continue
                    if (accept(service)) return@withTimeoutOrNull service
                }
                null
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): AdbService? = resolveLock.withLock {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    cont.resume(hostOf(resolved)?.let { AdbService(resolved.serviceName, it, resolved.port) })
                }

                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve of ${failed.serviceName} failed: $errorCode")
                    cont.resume(null)
                }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): String? {
        val addresses = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        return (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress
    }

    private fun localAddresses(): Set<String> =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .mapNotNull { it.hostAddress }
            .toSet()
}
