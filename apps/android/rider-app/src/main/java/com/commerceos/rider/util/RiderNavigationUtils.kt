package com.commerceos.rider.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object RiderNavigationUtils {

    /**
     * Launches external Google Maps in turn-by-turn navigation mode.
     * Falls back to generic geo: URI if Google Maps is not installed.
     */
    fun launchExternalMaps(context: Context, lat: Double?, lng: Double?, label: String) {
        if (lat == null || lng == null || lat == 0.0 || lng == 0.0) {
            Toast.makeText(context, "Location coordinates unavailable for $label", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "No navigation app found on device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens system phone dialer with pre-populated phone number.
     */
    fun dialPhoneNumber(context: Context, phone: String?) {
        val cleanPhone = phone?.filter { it.isDigit() || it == '+' }
        if (cleanPhone.isNullOrBlank() || cleanPhone.length < 5) {
            Toast.makeText(context, "Phone number unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open phone dialer", Toast.LENGTH_SHORT).show()
        }
    }
}
