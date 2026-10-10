package org.futo.inputmethod.latin.kbfr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.futo.inputmethod.latin.uix.getSetting

enum class OnlineBlock {
    APP_NOT_ALLOWED, APP_ALWAYS_BLOCKED, SENSITIVE_FIELD, INCOGNITO, CONTAMINATED, ONLINE_OFF, INTEGRITY, NETWORK_NOT_ALLOWED
}

// kbfr fork: the one rule every online action follows, Warm up included (spec 4, 5.7, 5.8, 8).
object OnlineGate {
    // integrityOk is null only before the first check of this process: the toolbar then shows no
    // lock, and check() always runs a fresh integrity check before sending anything.
    // contaminated: a sensitive clip was pasted into this field (R3), a rule of the field like the policy.
    fun block(
        policy: InputContextPolicy?,
        contaminated: Boolean,
        onlineEnabled: Boolean,
        integrityOk: Boolean?,
        networkAllowed: Boolean,
    ): OnlineBlock? = when {
        policy == null -> OnlineBlock.APP_NOT_ALLOWED
        !policy.onlineAllowed -> when (policy.reason) {
            BlockReason.APP_ALWAYS_BLOCKED -> OnlineBlock.APP_ALWAYS_BLOCKED
            BlockReason.SENSITIVE_FIELD -> OnlineBlock.SENSITIVE_FIELD
            BlockReason.INCOGNITO -> OnlineBlock.INCOGNITO
            BlockReason.APP_NOT_ALLOWED, null -> OnlineBlock.APP_NOT_ALLOWED
        }
        contaminated -> OnlineBlock.CONTAMINATED
        !onlineEnabled -> OnlineBlock.ONLINE_OFF
        integrityOk == false -> OnlineBlock.INTEGRITY
        !networkAllowed -> OnlineBlock.NETWORK_NOT_ALLOWED
        else -> null
    }

    // Right before an online action: fresh integrity check (spec 8) and the current network.
    suspend fun check(context: Context): OnlineBlock? {
        val integrity = KbfrState.refreshIntegrity(context)
        return block(KbfrState.policy, KbfrState.contaminated, context.getSetting(KbfrOnlineEnabled), integrity.ok, networkAllowed(context))
    }

    // "Wi-Fi only" (spec 6): the keyboard never sends over mobile data. A VPN reports the transports
    // of the network under it, so Wi-Fi behind a VPN still counts as Wi-Fi.
    fun networkAllowed(context: Context): Boolean {
        if (!context.getSetting(KbfrWifiOnly)) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
