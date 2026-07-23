package com.multicam

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multicam.ui.CameraScreen
import com.multicam.ui.ControllerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A camera/controller must never sleep mid-session.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    var role by remember { mutableStateOf<String?>(null) }
    when (role) {
        null -> RolePicker { role = it }
        "controller" -> ControllerScreen()
        "camera" -> CameraScreen()
    }
}

@Composable
private fun RolePicker(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MultiCam", fontSize = 34.sp)
        Text("Both devices must be on the same Wi-Fi network.", fontSize = 14.sp)
        Button(onClick = { onPick("controller") }, Modifier.fillMaxWidth().height(64.dp)) {
            Text("This is the CONTROLLER (tablet)", fontSize = 16.sp)
        }
        Button(onClick = { onPick("camera") }, Modifier.fillMaxWidth().height(64.dp)) {
            Text("This is a CAMERA (phone)", fontSize = 16.sp)
        }
    }
}
