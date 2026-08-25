package com.commerceos.android.rider

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.CommerceColors

/**
 * External Launcher Screen for Standalone Commerce OS Rider App.
 * Launches the dedicated standalone package com.commerceos.rider.
 */
@Composable
fun RiderAppScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Commerce OS Rider App",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            Text(
                "The Rider App is installed as a separate, standalone Android application (com.commerceos.rider).",
                fontSize = 14.sp,
                color = CommerceColors.TextMuted
            )

            Button(
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.commerceos.rider")
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Rider app (com.commerceos.rider) not found on device.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Open Standalone Rider App 🚀", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Back to Storefront")
            }
        }
    }
}
