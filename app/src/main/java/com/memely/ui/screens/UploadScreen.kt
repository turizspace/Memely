package com.memely.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.memely.ui.tutorial.TutorialOverlay
import com.memely.ui.tutorial.TutorialScreen
import com.memely.ui.tutorial.tutorialTarget

@Composable
fun UploadScreen(onMediaSelected: (Uri) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            onMediaSelected(it)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.tutorialTarget("select_image_button")
            ) {
                Text("Select Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            selectedUri?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        // Tutorial overlay for Upload screen
        TutorialOverlay(currentScreen = TutorialScreen.UPLOAD)
    }
}
