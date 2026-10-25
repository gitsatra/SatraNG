package com.v2ray.ang.contracts

import android.app.Service
import android.net.Network
import android.os.ParcelFileDescriptor

interface ServiceControl {
    /**
     * Gets the service instance.
     * @return The service instance.
     */
    fun getService(): Service

    /**
     * Starts the service.
     */
    fun startService()

    /**
     * Stops the service.
     */
    fun stopService()

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    fun vpnProtect(socket: Int): Boolean

    /**
     * Declares the networks the tunnel runs on top of.
     * Only meaningful for the VPN service, the other run modes have no interface to report.
     *
     * @param networks The upstream networks, null to let the system pick.
     * @return True if the networks were accepted.
     */
    fun setUnderlyingNetworks(networks: Array<Network>?): Boolean = false

    /**
     * Closes the current TUN and establishes a new one while the [VpnService] stays up.
     * App TCP/UDP sockets on the old interface are reset so over-quota keep-alives cannot
     * keep streaming after routing switches to `block`. Proxy-only and root return null.
     *
     * @return The new TUN descriptor, or null when this mode has no VPN interface.
     */
    fun recreateVpnInterfaceForPolicy(): ParcelFileDescriptor? = null
}
