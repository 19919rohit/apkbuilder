package com.neunix.appstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/* ---------------------------------------------------
   DATA MODELS
--------------------------------------------------- */

data class StoreApp(
    val id: String,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val description: String,
    val logoUrl: String,
    val screenshots: List<String>
)

enum class AppStatus {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    INSTALLED
}

data class AppUiState(
    val app: StoreApp,
    var status: AppStatus
)

/* ---------------------------------------------------
   MAIN ACTIVITY
--------------------------------------------------- */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                StoreScreen()
            }
        }
    }
}

/* ---------------------------------------------------
   STORE SCREEN
--------------------------------------------------- */

@Composable
fun StoreScreen() {

    val apps = remember {
        mutableStateListOf(
            AppUiState(dummyApps[0], AppStatus.NOT_INSTALLED),
            AppUiState(dummyApps[1], AppStatus.UPDATE_AVAILABLE),
            AppUiState(dummyApps[2], AppStatus.INSTALLED)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neunix Store", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->

        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps) { state ->
                AppCard(
                    state = state,
                    onAction = {
                        state.status = AppStatus.INSTALLED
                    }
                )
            }
        }
    }
}

/* ---------------------------------------------------
   APP CARD
--------------------------------------------------- */

@Composable
fun AppCard(
    state: AppUiState,
    onAction: () -> Unit
) {

    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            /* HEADER */
            Row(verticalAlignment = Alignment.CenterVertically) {

                AsyncImage(
                    model = state.app.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(state.app.name, fontWeight = FontWeight.Bold)
                    Text(
                        "v${state.app.versionName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = onAction,
                    enabled = state.status != AppStatus.INSTALLED
                ) {
                    Text(
                        when (state.status) {
                            AppStatus.NOT_INSTALLED -> "Install"
                            AppStatus.UPDATE_AVAILABLE -> "Update"
                            AppStatus.INSTALLED -> "Installed"
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            /* DESCRIPTION */
            Text(
                text = state.app.description,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            /* SCREENSHOTS */
            LazyRow {
                items(state.app.screenshots) { shot ->
                    AsyncImage(
                        model = shot,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 220.dp, height = 120.dp)
                            .padding(end = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------
   DUMMY DATA
--------------------------------------------------- */

val dummyApps = listOf(

    StoreApp(
        id = "notes",
        name = "Neunix Notes",
        packageName = "com.neunix.notes",
        versionName = "1.2.0",
        versionCode = 12,
        description = "A fast, minimal and offline notes app built by Neunix. Clean UI, no ads.",
        logoUrl = "https://cdn-icons-png.flaticon.com/512/1828/1828919.png",
        screenshots = listOf(
            "https://picsum.photos/400/800?1",
            "https://picsum.photos/400/800?2",
            "https://picsum.photos/400/800?3"
        )
    ),

    StoreApp(
        id = "player",
        name = "Neunix Music",
        packageName = "com.neunix.music",
        versionName = "2.0.1",
        versionCode = 20,
        description = "Lightweight music player with folder support and no internet required.",
        logoUrl = "https://cdn-icons-png.flaticon.com/512/727/727245.png",
        screenshots = listOf(
            "https://picsum.photos/400/800?4",
            "https://picsum.photos/400/800?5"
        )
    ),

    StoreApp(
        id = "scanner",
        name = "Neunix Scanner",
        packageName = "com.neunix.scanner",
        versionName = "1.0.0",
        versionCode = 1,
        description = "Scan documents, IDs and export PDFs instantly. Simple and secure.",
        logoUrl = "https://cdn-icons-png.flaticon.com/512/833/833314.png",
        screenshots = listOf(
            "https://picsum.photos/400/800?6",
            "https://picsum.photos/400/800?7",
            "https://picsum.photos/400/800?8"
        )
    )
)