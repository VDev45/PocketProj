package com.vishwaravi.pocketproj

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ enforces edge-to-edge for targetSdk 35. Let Compose handle
        // the system-bar insets instead of relying on deprecated window color APIs.
        enableEdgeToEdge()

        sharedText = readSharedText(intent)
        val repository = (application as PocketProjApplication).repository
        setContent {
            PocketAppRoot(repository = repository, sharedText = sharedText)
        }
    }

    /**
     * A share while the activity is already alive arrives here; expose the new text so
     * the router re-opens the Add Link screen with it (the library search UI is passive).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
}