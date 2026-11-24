package com.custom.tvlauncher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LeagueConfig(
    val id: String,
    val name: String
)

object LeaguePresets {
    val list = listOf(
        LeagueConfig("4328", "Premier League"),
        LeagueConfig("4335", "La Liga"),
        LeagueConfig("4332", "Serie A"),
        LeagueConfig("4331", "Bundesliga"),
        LeagueConfig("4334", "Ligue 1"),
        LeagueConfig("4480", "UEFA Champions League")
    )
}

class LeagueSettings(private val context: Context) {
    private object Keys {
        val LEAGUE_ID = stringPreferencesKey("football_league_id")
        val LEAGUE_NAME = stringPreferencesKey("football_league_name")
    }

    val leagueFlow: Flow<LeagueConfig> = context.dataStore.data.map { prefs ->
        LeagueConfig(
            id = prefs[Keys.LEAGUE_ID] ?: LeaguePresets.list.first().id,
            name = prefs[Keys.LEAGUE_NAME] ?: LeaguePresets.list.first().name
        )
    }

    suspend fun saveLeague(config: LeagueConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LEAGUE_ID] = config.id
            prefs[Keys.LEAGUE_NAME] = config.name
        }
    }
}
