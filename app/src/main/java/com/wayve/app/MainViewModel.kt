package com.wayve.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Wayve Song app
 * Simplified version that only handles basic state management
 */
class MainViewModel(private val context: Context) : ViewModel() {
    
    // Placeholder method for backlog state (not used in new design but kept for compatibility)
    fun saveBacklogState() {
        // No-op - backlog functionality removed in redesign
    }
    
    override fun onCleared() {
        super.onCleared()
        saveBacklogState()
    }
}

