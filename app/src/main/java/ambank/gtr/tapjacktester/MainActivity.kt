package ambank.gtr.tapjacktester

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ambank.gtr.tapjacktester.ui.theme.TapjackTesterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ensureOverlayPermission()

        setContent {
            TapjackTesterTheme {
                Scaffold { padding ->
                    OverlayToggle(modifier = Modifier.padding(padding))
                }
            }
        }
    }

    private fun ensureOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}

@Composable
private fun OverlayToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }

    Column(modifier.padding(16.dp)) {
        Button(onClick = {
            enabled = !enabled

            context.startService(
                Intent(context, OverlayService::class.java).apply {
                    action = if (enabled)
                        OverlayService.ACTION_SHOW
                    else
                        OverlayService.ACTION_HIDE
                }
            )
        }) {
            Text(if (enabled) "Disable Overlay" else "Enable Overlay")
        }
    }
}
