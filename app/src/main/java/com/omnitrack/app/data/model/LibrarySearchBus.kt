package com.omnitrack.app.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LibrarySearchBus {
    private val _pendingQuery = MutableStateFlow<String?>(null)
    val pendingQuery: StateFlow<String?> = _pendingQuery.asStateFlow()

    fun requestSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            _pendingQuery.value = clean
        }
    }

    fun consume() {
        _pendingQuery.value = null
    }
}
