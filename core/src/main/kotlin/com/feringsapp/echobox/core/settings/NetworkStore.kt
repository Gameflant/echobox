package com.feringsapp.echobox.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 当前选中的网络源：单选，点哪个用哪个。 */
private val Context.networkStore by preferencesDataStore(name = "network")

class NetworkStore(private val context: Context) {

    private val keySelected = stringPreferencesKey("selected_mirror")

    val selectedId: Flow<String> = context.networkStore.data.map { it[keySelected] ?: "direct" }

    suspend fun current(): String = selectedId.first()

    suspend fun select(id: String) {
        context.networkStore.edit { it[keySelected] = id }
    }
}
