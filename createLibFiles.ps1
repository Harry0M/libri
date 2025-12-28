# LibraryScreen.kt - Part 1
$libScreen1 = @"
package com.example.learncompose.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.learncompose.data.UserPreferencesRepository
import com.example.learncompose.datamodel.LibraryBook
import com.example.learncompose.datamodel.ReadingStatus
import com.example.learncompose.viewModel.LibraryUiState
import com.example.learncompose.viewModel.LibraryViewModel
import com.example.learncompose.viewModel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onAddBookClick: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val googleUser = userPreferencesRepository.getGoogleUser()
    val uid = googleUser.third ?: return
    
    val uiState by viewModel.uiState.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    
    LaunchedEffect(uid) {
        viewModel.loadLibrary(uid)
    }
    
    Scaffold(
        topBar = {
            if (showSearchBar) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchClose = {
                        showSearchBar = false
                        viewModel.setSearchQuery("")
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                \"My Library\",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            filterStatus?.let {
                                Text(
                                    it.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, \"Search\")
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, \"Filter\")
                        }
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Default.Sort, \"Sort\")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBookClick,
                icon = { Icon(Icons.Default.Add, \"Add Book\") },
                text = { Text(\"Add Book\") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is LibraryUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is LibraryUiState.Success -> {
                    if (state.books.isEmpty()) {
                        EmptyLibraryView(onAddBookClick)
                    } else {
                        LibraryGrid(
                            books = state.books,
                            onBookClick = onBookClick
                        )
                    }
                }
            }
        }
    }
    
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            FilterBottomSheet(
                currentFilter = filterStatus,
                onFilterSelected = {
                    viewModel.setFilterStatus(it)
                    showFilterSheet = false
                }
            )
        }
    }
    
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false }
        ) {
            SortBottomSheet(
                currentSort = sortOption,
                onSortSelected = {
                    viewModel.setSortOption(it)
                    showSortSheet = false
                }
            )
        }
    }
}
"@
Set-Content -Path "C:\Users\palha\Desktop\learn\app\src\main\java\com\example\learncompose\view\LibraryScreen_Part1.txt" -Value $libScreen1 -Encoding UTF8
