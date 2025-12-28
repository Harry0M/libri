package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data_retrieval.DownloadsRepository
import com.theblankstate.libri.datamodel.DownloadedBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DownloadsRepository(application)
    
    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks

    init {
        refreshDownloads()
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            _downloadedBooks.value = repository.getDownloadedBooks()
        }
    }

    fun removeBook(bookId: String) {
        viewModelScope.launch {
            repository.removeBook(bookId)
            refreshDownloads()
        }
    }
}
