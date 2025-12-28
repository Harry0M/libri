package com.theblankstate.libri.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.libri.datamodel.Shelf
import com.theblankstate.libri.view.components.CreateShelfDialog
import com.theblankstate.libri.viewModel.ShelvesUiState
import com.theblankstate.libri.viewModel.ShelvesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShelvesScreen(
    uid: String?,
    onShelfClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ShelvesViewModel = viewModel()
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedShelf by remember { mutableStateOf<Shelf?>(null) }
    var showOptionsSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.shelvesUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load shelves on first composition
    LaunchedEffect(uid) {
        uid?.let { viewModel.loadShelves(it) }
    }

    // Show operation messages
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Shelves") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uid != null) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Shelf")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ShelvesUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ShelvesUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { uid?.let { viewModel.loadShelves(it) } }) {
                            Text("Retry")
                        }
                    }
                }
                is ShelvesUiState.Success -> {
                    if (state.shelves.isEmpty()) {
                        // Empty state
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No shelves yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Create your first shelf to organize your books!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Shelves grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.shelves, key = { it.id }) { shelf ->
                                ShelfCard(
                                    shelf = shelf,
                                    onClick = { onShelfClick(shelf.id) },
                                    onLongClick = {
                                        selectedShelf = shelf
                                        showOptionsSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create shelf dialog
    if (showCreateDialog) {
        CreateShelfDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                uid?.let {
                    viewModel.createShelf(
                        uid = it,
                        name = name,
                        description = description,
                        onSuccess = { showCreateDialog = false },
                        onError = { error ->
                            // Show error in snackbar
                            kotlinx.coroutines.MainScope().launch {
                                snackbarHostState.showSnackbar(error)
                            }
                        }
                    )
                }
            }
        )
    }

    // Edit shelf dialog
    if (showEditDialog && selectedShelf != null) {
        CreateShelfDialog(
            onDismiss = {
                showEditDialog = false
                selectedShelf = null
            },
            onConfirm = { name, description ->
                uid?.let { userId ->
                    selectedShelf?.let { shelf ->
                        viewModel.updateShelf(
                            uid = userId,
                            shelf = shelf.copy(name = name, description = description),
                            onSuccess = {
                                showEditDialog = false
                                selectedShelf = null
                            },
                            onError = { error ->
                                kotlinx.coroutines.MainScope().launch {
                                    snackbarHostState.showSnackbar(error)
                                }
                            }
                        )
                    }
                }
            },
            initialName = selectedShelf?.name ?: "",
            initialDescription = selectedShelf?.description ?: "",
            title = "Edit Shelf",
            confirmButtonText = "Save"
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && selectedShelf != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedShelf = null
            },
            title = { Text("Delete Shelf?") },
            text = {
                Text("Are you sure you want to delete \"${selectedShelf?.name}\"? Books in this shelf will remain in your library.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        uid?.let { userId ->
                            selectedShelf?.let { shelf ->
                                viewModel.deleteShelf(
                                    uid = userId,
                                    shelfId = shelf.id,
                                    onSuccess = {
                                        showDeleteDialog = false
                                        selectedShelf = null
                                    },
                                    onError = { error ->
                                        kotlinx.coroutines.MainScope().launch {
                                            snackbarHostState.showSnackbar(error)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    selectedShelf = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Options Bottom Sheet - show actions for selected shelf (View, Edit, Delete)
    if (showOptionsSheet && selectedShelf != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showOptionsSheet = false
                selectedShelf = null
            },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = selectedShelf?.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        // View shelf
                        selectedShelf?.let { shelf ->
                            showOptionsSheet = false
                            onShelfClick(shelf.id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Shelf")
                }
                TextButton(
                    onClick = {
                        // Edit
                        showOptionsSheet = false
                        showEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Shelf")
                }
                TextButton(
                    onClick = {
                        // Delete
                        showOptionsSheet = false
                        showDeleteDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Shelf", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfCard(
    shelf: Shelf,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Column {
                Text(
                    shelf.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${shelf.bookCount} book${if (shelf.bookCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                if (shelf.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        shelf.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    
}
