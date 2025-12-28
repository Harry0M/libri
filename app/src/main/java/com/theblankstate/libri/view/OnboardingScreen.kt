package com.theblankstate.libri.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.libri.data.OnboardingData
import com.theblankstate.libri.viewModel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val selectedLanguages by viewModel.selectedLanguages.collectAsState()
    val selectedAuthors by viewModel.selectedAuthors.collectAsState()
    val selectedGenres by viewModel.selectedGenres.collectAsState()
    val availableAuthors by viewModel.availableAuthors.collectAsState()

    Scaffold(
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
                            viewModel.nextStep()
                            onOnboardingComplete()
                        } else {
                            viewModel.nextStep()
                        }
                    },
                    enabled = when (currentStep) {
                        0 -> selectedLanguages.isNotEmpty()
                        1 -> selectedAuthors.isNotEmpty()
                        2 -> selectedGenres.isNotEmpty()
                        else -> false
                    }
                ) {
                    Text(if (currentStep == 2) "Finish" else "Next")
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

@Composable
fun LanguageSelection(
    languages: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyColumn {
        items(languages) { language ->
            SelectionItem(
                text = language,
                isSelected = selected.contains(language),
                onClick = { onToggle(language) }
            )
        }
    }
}

@Composable
fun AuthorSelection(
    authors: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(authors) { author ->
            SelectionItem(
                text = author,
                isSelected = selected.contains(author),
                onClick = { onToggle(author) }
            )
        }
    }
}

@Composable
fun GenreSelection(
    genres: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(genres) { genre ->
            SelectionItem(
                text = genre,
                isSelected = selected.contains(genre),
                onClick = { onToggle(genre) }
            )
        }
    }
}

@Composable
fun SelectionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
