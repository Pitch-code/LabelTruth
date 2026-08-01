package com.labeltruth.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.labeltruth.app.domain.model.HealthProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("labeltruth_prefs")

/**
 * The user's health profile, stored only on this device.
 *
 * There is deliberately no account, no cloud sync and no user id. Health data is
 * the most sensitive thing this app touches, so the safest design is to never
 * let it leave the phone.
 */
class ProfileStore(private val context: Context) {

    private val allergensKey = stringSetPreferencesKey("allergens")
    private val dietsKey = stringSetPreferencesKey("diets")
    private val conditionsKey = stringSetPreferencesKey("conditions")
    private val disclaimerKey = booleanPreferencesKey("disclaimer_accepted")
    private val onboardedKey = booleanPreferencesKey("onboarding_complete")
    private val firstNameKey = stringPreferencesKey("first_name")

    /**
     * A first name, used only to address the user in the app.
     *
     * Optional, and never required to use anything. It is written to this
     * device's own preferences and read back by this app alone; there is no
     * account and no server for it to go to.
     */
    val firstName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[firstNameKey].orEmpty()
    }

    suspend fun setFirstName(value: String) {
        val trimmed = value.trim().take(24)
        context.dataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(firstNameKey) else prefs[firstNameKey] = trimmed
        }
    }

    val profile: Flow<HealthProfile> = context.dataStore.data.map { prefs ->
        HealthProfile(
            allergens = prefs[allergensKey] ?: emptySet(),
            diets = prefs[dietsKey] ?: emptySet(),
            conditions = prefs[conditionsKey] ?: emptySet()
        )
    }

    val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[disclaimerKey] ?: false
    }

    suspend fun toggleAllergen(value: String) = toggle(allergensKey, value)
    suspend fun toggleDiet(value: String) = toggle(dietsKey, value)
    suspend fun toggleCondition(value: String) = toggle(conditionsKey, value)

    /** Shown once. Skipping still counts as done, so we never nag. */
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardedKey] ?: false
    }

    suspend fun acceptDisclaimer() {
        context.dataStore.edit { it[disclaimerKey] = true }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[onboardedKey] = true }
    }

    private suspend fun toggle(key: Preferences.Key<Set<String>>, value: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (value in current) current - value else current + value
        }
    }
}
