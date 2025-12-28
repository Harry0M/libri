package com.theblankstate.libri.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.libri.data.OnboardingData
import com.theblankstate.libri.viewModel.EditPreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPreferencesScreen(
    onBackClick: () -> Unit,
    viewModel: EditPreferencesViewModel = viewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val selectedLanguages by viewModel.selectedLanguages.collectAsState()
    val selectedAuthors by viewModel.selectedAuthors.collectAsState()
    val selectedGenres by viewModel.selectedGenres.collectAsState()
    val availableAuthors by viewModel.availableAuthors.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCurrentPreferences()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    Button(onClick = { viewModel.previousStep() }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = {
                        if (currentStep == 2) {
                            viewModel.savePreferences(onSuccess = onBackClick)
                        } else {
                            viewModel.nextStep()
                        }
                    },
                    enabled = when (currentStep) {
                        0 -> selectedLanguages.isNotEmpty()
                        1 -> selectedAuthors.isNotEmpty()
                        2 -> selectedGenres.isNotEmpty()
                        else -> false
                    } && !isSaving
                ) {
                    if (isSaving && currentStep == 2) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (currentStep == 2) "Save" else "Next")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = when (currentStep) {
                    0 -> "Choose Languages (Max 4)"
                    1 -> "Choose Authors (Max 40)"
                    2 -> "Choose Genres (Max 5)"
                    else -> ""
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (currentStep) {
                0 -> LanguageSelection(
                    languages = OnboardingData.languages,
                    selected = selectedLanguages,
                    onToggle = { viewModel.toggleLanguage(it) }
                )
                1 -> AuthorSelection(
                    authors = availableAuthors,
                    selected = selectedAuthors,
                    onToggle = { viewModel.toggleAuthor(it) }
                )
                2 -> GenreSelection(
                    genres = OnboardingData.genres,
                    selected = selectedGenres,
                    onToggle = { viewModel.toggleGenre(it) }
                )
            }
        }
    }
}
