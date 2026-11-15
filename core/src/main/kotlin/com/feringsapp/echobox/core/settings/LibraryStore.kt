package com.feringsapp.echobox.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 收藏与搜索历史都放这一个 DataStore，避免为两个字符串集合再开一个文件。 */
private val Context.libraryStore by preferencesDataStore(name = "library")

class LibraryStore(private val context: Context) {

    private val keyFavorites = stringSetPreferencesKey("favorites")
    private val keyHistory = stringPreferencesKey("search_history")

    val favorites: Flow<Set<String>> = context.libraryStore.data.map { it[keyFavorites].orEmpty() }

    val searchHistory: Flow<List<String>> = context.libraryStore.data.map { prefs ->
        prefs[keyHistory].orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
    }

    suspend fun toggleFavorite(trackId: String) {
        context.libraryStore.edit { prefs ->
            val current = prefs[keyFavorites].orEmpty()
            prefs[keyFavorites] = if (trackId in current) current - trackId else current + trackId
        }
    }

    suspend fun isFavorite(trackId: String): Boolean = favorites.first().contains(trackId)

    suspend fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.libraryStore.edit { prefs ->
            val old = prefs[keyHistory].orEmpty().split('\n').filter { it.isNotBlank() }
            val merged = (listOf(trimmed) + old.filterNot { it == trimmed }).take(10)
            prefs[keyHistory] = merged.joinToString("\n")
        }
    }

    suspend fun clearHistory() {
        context.libraryStore.edit { it.remove(keyHistory) }
    }
}
