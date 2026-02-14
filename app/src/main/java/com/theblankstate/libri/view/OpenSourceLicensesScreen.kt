package com.theblankstate.libri.view

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    val libraries = listOf(
        OpenSourceLibrary(
            name = "Libri",
            author = "The Blank State Team",
            license = "Apache License 2.0",
            url = "https://github.com/Harry0M/libri?tab=Apache-2.0-1-ov-file"
        ),
        OpenSourceLibrary(
            name = "AndroidX Libraries",
            author = "The Android Open Source Project",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose",
            author = "The Android Open Source Project",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/compose"
        ),
        OpenSourceLibrary(
            name = "Firebase Android SDK",
            author = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/firebase/firebase-android-sdk"
        ),
        OpenSourceLibrary(
            name = "Retrofit",
            author = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://github.com/square/retrofit"
        ),
        OpenSourceLibrary(
            name = "OkHttp",
            author = "Square, Inc.",
            license = "Apache License 2.0",
            url = "https://github.com/square/okhttp"
        ),
        OpenSourceLibrary(
            name = "Coil",
            author = "Coil Contributors",
            license = "Apache License 2.0",
            url = "https://github.com/coil-kt/coil"
        ),
        OpenSourceLibrary(
            name = "Kotlin Coroutines",
            author = "JetBrains",
            license = "Apache License 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        OpenSourceLibrary(
            name = "Material Components for Android",
            author = "Google LLC",
            license = "Apache License 2.0",
            url = "https://github.com/material-components/material-components-android"
        ),
        OpenSourceLibrary(
            name = "Google Sign-In",
            author = "Google LLC",
            license = "Apache License 2.0",
            url = "https://developers.google.com/identity/sign-in/android"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "We are grateful to the authors of the following open source software components which are used in this application:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(libraries) { library ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = library.name,
                                fontWeight = FontWeight.SemiBold
                            ) 
                        },
                        supportingContent = {
                            Column {
                                Text(text = library.author)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = library.license,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        trailingContent = {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = "View License",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(library.url))
                            context.startActivity(intent)
                        }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Libri is Open Source!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "You can contribute to Libri on GitHub. Join us in building the best open source e-reader platform.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
