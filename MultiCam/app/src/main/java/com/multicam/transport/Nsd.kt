package com.multicam.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.InetAddress

/**
 * Discovery via NSD/mDNS on infrastructure Wi-Fi — the transport the tech spec
 * settled on (and the reason minSdk is 33: NsdManager is broken on 12-).
 *
 * Controller advertises "_multicam._tcp." with its TCP control port; cameras
 * discover and resolve it. The multicast lock is held for the discovery
 * lifetime — several OEMs silently drop mDNS packets without it.
 */
class NsdHelper(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val multicastLock = (context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("multicam-nsd").apply { setReferenceCounted(false) }

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    /** Controller side: advertise the control channel. */
    fun advertise(port: Int, deviceName: String, onEvent: (String) -> Unit) {
        val info = NsdServiceInfo().apply {
            serviceName = "MultiCam-$deviceName"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) = onEvent("advertising as ${i.serviceName}")
            override fun onRegistrationFailed(i: NsdServiceInfo, err: Int) = onEvent("advertise FAILED: $err")
            override fun onServiceUnregistered(i: NsdServiceInfo) = onEvent("advertise stopped")
            override fun onUnregistrationFailed(i: NsdServiceInfo, err: Int) = onEvent("unregister failed: $err")
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    /** Camera side: find a controller, resolve to (address, port). */
    fun discover(onFound: (InetAddress, Int) -> Unit, onEvent: (String) -> Unit) {
        multicastLock.acquire()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = onEvent("searching for controller…")
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.startsWith("_multicam")) return
                onEvent("found ${service.serviceName}, resolving…")
                @Suppress("DEPRECATION") // resolveService: deprecated API 34, still the simplest correct path
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host ?: return onEvent("resolve returned no host")
                        onEvent("controller at ${host.hostAddress}:${resolved.port}")
                        onFound(host, resolved.port)
                    }
                    override fun onResolveFailed(i: NsdServiceInfo, err: Int) = onEvent("resolve failed: $err")
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) = onEvent("lost ${service.serviceName}")
            override fun onDiscoveryStopped(type: String) = onEvent("discovery stopped")
            override fun onStartDiscoveryFailed(type: String, err: Int) = onEvent("discovery FAILED: $err")
            override fun onStopDiscoveryFailed(type: String, err: Int) = onEvent("stop discovery failed: $err")
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        runCatching { multicastLock.release() }
        registration = null
        discovery = null
    }
}
