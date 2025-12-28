package com.theblankstate.libri.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.ShelvesRepository
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.Shelf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class ShelvesUiState {
    object Loading : ShelvesUiState()
    data class Success(val shelves: List<Shelf>) : ShelvesUiState()
    data class Error(val message: String) : ShelvesUiState()
}

sealed class ShelfDetailUiState {
    object Loading : ShelfDetailUiState()
    data class Success(val shelf: Shelf, val books: List<LibraryBook>) : ShelfDetailUiState()
    data class Error(val message: String) : ShelfDetailUiState()
}

class ShelvesViewModel : ViewModel() {
    private val repository = ShelvesRepository()

    private val _shelvesUiState = MutableStateFlow<ShelvesUiState>(ShelvesUiState.Loading)
    val shelvesUiState: StateFlow<ShelvesUiState> = _shelvesUiState.asStateFlow()

    private val _shelfDetailUiState = MutableStateFlow<ShelfDetailUiState>(ShelfDetailUiState.Loading)
    val shelfDetailUiState: StateFlow<ShelfDetailUiState> = _shelfDetailUiState.asStateFlow()

    private val _selectedShelf = MutableStateFlow<Shelf?>(null)
    val selectedShelf: StateFlow<Shelf?> = _selectedShelf.asStateFlow()

    private val _shelvesForBook = MutableStateFlow<List<Shelf>>(emptyList())
    val shelvesForBook: StateFlow<List<Shelf>> = _shelvesForBook.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _allShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val allShelves: StateFlow<List<Shelf>> = _allShelves.asStateFlow()

    /**
     * Load all shelves for a user
     */
    fun loadShelves(uid: String) {
        viewModelScope.launch {
            try {
                _shelvesUiState.value = ShelvesUiState.Loading
                repository.getUserShelves(uid).collect { shelves ->
                    _allShelves.value = shelves
                    _shelvesUiState.value = ShelvesUiState.Success(shelves)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to load shelves", e)
                _shelvesUiState.value = ShelvesUiState.Error(e.message ?: "Failed to load shelves")
            }
        }
    }

    /**
     * Load a specific shelf with its books
     */
    fun loadShelfDetail(uid: String, shelfId: String) {
        viewModelScope.launch {
            try {
                _shelfDetailUiState.value = ShelfDetailUiState.Loading
                
                // Get shelf info
                val shelf = repository.getShelf(uid, shelfId)
                if (shelf == null) {
                    _shelfDetailUiState.value = ShelfDetailUiState.Error("Shelf not found")
                    return@launch
                }
                
                _selectedShelf.value = shelf
                
                // Get books in shelf
                repository.getBooksInShelf(uid, shelfId).collect { books ->
                    _shelfDetailUiState.value = ShelfDetailUiState.Success(shelf, books)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to load shelf detail", e)
                _shelfDetailUiState.value = ShelfDetailUiState.Error(e.message ?: "Failed to load shelf")
            }
        }
    }

    /**
     * Create a new shelf
     */
    fun createShelf(
        uid: String,
        name: String,
        description: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Check for duplicate name
                if (_allShelves.value.any { it.name.equals(name, ignoreCase = true) }) {
                    onError("A shelf with this name already exists")
                    return@launch
                }

                val shelf = Shelf(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                    dateCreated = System.currentTimeMillis(),
                    bookCount = 0
                )

                val result = repository.createShelf(uid, shelf)
                if (result.isSuccess) {
                    _operationMessage.value = "Shelf created successfully"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to create shelf"
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to create shelf", e)
                onError(e.message ?: "Failed to create shelf")
            }
        }
    }

    /**
     * Delete a shelf
     */
    fun deleteShelf(
        uid: String,
        shelfId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.deleteShelf(uid, shelfId)
                if (result.isSuccess) {
                    _operationMessage.value = "Shelf deleted successfully"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to delete shelf"
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to delete shelf", e)
                onError(e.message ?: "Failed to delete shelf")
            }
        }
    }

    /**
     * Update shelf name and/or description
     */
    fun updateShelf(
        uid: String,
        shelf: Shelf,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Check for duplicate name (excluding current shelf)
                if (_allShelves.value.any { it.id != shelf.id && it.name.equals(shelf.name, ignoreCase = true) }) {
                    onError("A shelf with this name already exists")
                    return@launch
                }

                val result = repository.updateShelf(uid, shelf)
                if (result.isSuccess) {
                    _operationMessage.value = "Shelf updated successfully"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to update shelf"
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to update shelf", e)
                onError(e.message ?: "Failed to update shelf")
            }
        }
    }

    /**
     * Add a book to a shelf
     */
    fun addBookToShelf(
        uid: String,
        bookId: String,
        shelfId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Check if book is already in shelf
                if (repository.isBookInShelf(uid, bookId, shelfId)) {
                    onError("Book is already in this shelf")
                    return@launch
                }

                val result = repository.addBookToShelf(uid, bookId, shelfId)
                if (result.isSuccess) {
                    _operationMessage.value = "Book added to shelf"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to add book to shelf"
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to add book to shelf", e)
                onError(e.message ?: "Failed to add book to shelf")
            }
        }
    }

    /**
     * Add a book to multiple shelves at once
     */
    fun addBookToShelves(
        uid: String,
        bookId: String,
        shelfIds: List<String>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                var successCount = 0
                var errorOccurred = false

                for (shelfId in shelfIds) {
                    // Skip if already in shelf
                    if (repository.isBookInShelf(uid, bookId, shelfId)) {
                        continue
                    }

                    val result = repository.addBookToShelf(uid, bookId, shelfId)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        errorOccurred = true
                    }
                }

                if (successCount > 0) {
                    _operationMessage.value = "Book added to $successCount shelf(s)"
                    onSuccess()
                } else if (errorOccurred) {
                    onError("Failed to add book to shelves")
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to add book to shelves", e)
                onError(e.message ?: "Failed to add book to shelves")
            }
        }
    }

    /**
     * Remove a book from a shelf
     */
    fun removeBookFromShelf(
        uid: String,
        bookId: String,
        shelfId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.removeBookFromShelf(uid, bookId, shelfId)
                if (result.isSuccess) {
                    _operationMessage.value = "Book removed from shelf"
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to remove book from shelf"
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to remove book from shelf", e)
                onError(e.message ?: "Failed to remove book from shelf")
            }
        }
    }

    /**
     * Load shelves that contain a specific book
     */
    fun loadShelvesForBook(uid: String, bookId: String) {
        viewModelScope.launch {
            try {
                repository.getShelvesForBook(uid, bookId).collect { shelves ->
                    _shelvesForBook.value = shelves
                }
            } catch (e: Exception) {
                Log.e("ShelvesViewModel", "Failed to load shelves for book", e)
                _shelvesForBook.value = emptyList()
            }
        }
    }

    /**
     * Clear operation message
     */
    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    /**
     * Clear selected shelf
     */
    fun clearSelectedShelf() {
        _selectedShelf.value = null
    }
}
