package com.v2ray.ang.handler

/**
 * Pure decision for whether a policy reload must recreate the VPN TUN.
 * APP/CATEGORY over-quota and blocked UIDs that still transfer need a socket kill;
 * DOMAIN-only soft reload is enough.
 */
internal object PolicyEnforcement {
    fun needsSocketKill(matchesApps: Boolean, stillFlowingBlocked: Boolean): Boolean =
        matchesApps || stillFlowingBlocked
}
