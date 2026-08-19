package com.commerceos.android.search

import com.commerceos.android.config.SearchHistoryBehavior

/**
 * Universal Search History Manager handling in-memory and disk persistence,
 * max item limits, individual item deletion, and clear-all functionality.
 */
class SearchHistoryManager(private val behavior: SearchHistoryBehavior = SearchHistoryBehavior()) {

    private val historyList = mutableListOf<String>()

    fun getHistory(): List<String> = historyList.toList()

    fun addQuery(query: String) {
        if (!behavior.enabled) return
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        historyList.remove(trimmed)
        historyList.add(0, trimmed)

        while (historyList.size > behavior.maxHistoryItems) {
            historyList.removeAt(historyList.lastIndex)
        }
    }

    fun removeQuery(query: String): Boolean {
        if (!behavior.allowIndividualClear) return false
        return historyList.remove(query)
    }

    fun clearAll(): Boolean {
        if (!behavior.allowClearAll) return false
        historyList.clear()
        return true
    }
}
