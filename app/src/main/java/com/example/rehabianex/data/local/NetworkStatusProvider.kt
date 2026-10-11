package com.example.rehabianex.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun interface NetworkStatusProvider {
    fun isOnline(): Boolean
}

class AndroidNetworkStatusProvider(context: Context) : NetworkStatusProvider {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
