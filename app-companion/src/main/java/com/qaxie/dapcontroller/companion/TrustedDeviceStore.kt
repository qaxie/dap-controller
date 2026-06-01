package com.qaxie.dapcontroller.companion

import android.content.Context

object TrustedDeviceStore {
    private const val PREFS_NAME = "companion_prefs"
    private const val KEY_TRUSTED_ADDRESS = "trusted_device_address"
    private const val KEY_TRUSTED_NAME = "trusted_device_name"

    fun getTrustedAddress(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRUSTED_ADDRESS, null)

    fun getTrustedName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRUSTED_NAME, null)

    fun setTrustedDevice(context: Context, address: String, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRUSTED_ADDRESS, address)
            .putString(KEY_TRUSTED_NAME, name)
            .commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_TRUSTED_ADDRESS).remove(KEY_TRUSTED_NAME).commit()
    }
}
