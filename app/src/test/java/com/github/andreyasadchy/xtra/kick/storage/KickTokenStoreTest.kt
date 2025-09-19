package com.github.andreyasadchy.xtra.kick.storage

import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.kick.auth.KickTokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickTokenStoreTest {

    @Test
    fun `update persists the latest token payload`() {
        val preferences = InMemorySharedPreferences()
        val store = KickTokenStore(preferences)
        val expectedExpiresLowerBound = System.currentTimeMillis() + 60_000

        val response = KickTokenResponse(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 60,
            refreshToken = "refresh-token",
            scope = "chat:read chat:write"
        )

        store.update(response)

        assertEquals("access-token", store.accessToken)
        assertEquals("refresh-token", store.refreshToken)
        assertEquals("Bearer", store.tokenType)
        assertEquals(setOf("chat:read", "chat:write"), store.scopes)
        val expiresAt = store.expiresAtMillis
        assertNotNull(expiresAt)
        val expectedExpiresUpperBound = expectedExpiresLowerBound + 1_500
        assertTrue(expiresAt!! in expectedExpiresLowerBound..expectedExpiresUpperBound)
    }

    @Test
    fun `clear removes persisted credentials`() {
        val preferences = InMemorySharedPreferences()
        val store = KickTokenStore(preferences)

        store.update(
            KickTokenResponse(
                accessToken = "access-token",
                tokenType = "Bearer",
                expiresIn = 30,
                refreshToken = "refresh-token",
                scope = "chat:read"
            )
        )

        store.clear()

        assertNull(store.accessToken)
        assertNull(store.refreshToken)
        assertNull(store.tokenType)
        assertTrue(store.scopes.isEmpty())
        assertNull(store.expiresAtMillis)
    }

    @Test
    fun `migrateLegacyTokensIfNeeded copies plaintext entries into encrypted preferences`() {
        val legacyPreferences = InMemorySharedPreferences().apply {
            edit()
                .putString("access_token", "legacy-access")
                .putString("refresh_token", "legacy-refresh")
                .putString("token_type", "Bearer")
                .putString("scopes", "chat:read chat:write")
                .putLong("expires_at", 42L)
                .apply()
        }
        val encryptedPreferences = InMemorySharedPreferences()

        KickTokenStore.migrateLegacyTokensIfNeeded(legacyPreferences, encryptedPreferences)

        assertEquals("legacy-access", encryptedPreferences.getString("access_token", null))
        assertEquals("legacy-refresh", encryptedPreferences.getString("refresh_token", null))
        assertEquals("Bearer", encryptedPreferences.getString("token_type", null))
        assertEquals("chat:read chat:write", encryptedPreferences.getString("scopes", null))
        assertEquals(42L, encryptedPreferences.getLong("expires_at", -1))
        assertTrue(encryptedPreferences.getBoolean(KickTokenStore.KEY_MIGRATION_COMPLETE, false))

        assertFalse(legacyPreferences.contains("access_token"))
        assertFalse(legacyPreferences.contains("refresh_token"))
        assertFalse(legacyPreferences.contains("token_type"))
        assertFalse(legacyPreferences.contains("scopes"))
        assertFalse(legacyPreferences.contains("expires_at"))
    }

    @Test
    fun `migrateLegacyTokensIfNeeded skips migration when already completed`() {
        val legacyPreferences = InMemorySharedPreferences().apply {
            edit()
                .putString("access_token", "new-access")
                .apply()
        }
        val encryptedPreferences = InMemorySharedPreferences().apply {
            edit().putBoolean(KickTokenStore.KEY_MIGRATION_COMPLETE, true).apply()
        }

        KickTokenStore.migrateLegacyTokensIfNeeded(legacyPreferences, encryptedPreferences)

        assertNull(encryptedPreferences.getString("access_token", null))
        assertTrue(legacyPreferences.contains("access_token"))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners += listener
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners -= listener
        }

        private inner class EditorImpl : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = value
                }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = values?.toSet()
                }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = value
                }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = value
                }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = value
                }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) {
                    updates[key] = value
                }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) {
                    removals += key
                }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearRequested = true
                return this
            }

            override fun commit(): Boolean {
                if (clearRequested) {
                    val keys = values.keys.toList()
                    values.clear()
                    keys.forEach { notifyListeners(it) }
                    clearRequested = false
                }

                removals.forEach { key ->
                    if (values.remove(key) != null) {
                        notifyListeners(key)
                    }
                }

                updates.forEach { (key, value) ->
                    if (value == null) {
                        if (values.remove(key) != null) {
                            notifyListeners(key)
                        }
                    } else {
                        values[key] = value
                        notifyListeners(key)
                    }
                }

                updates.clear()
                removals.clear()
                return true
            }

            override fun apply() {
                commit()
            }

            private fun notifyListeners(key: String) {
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}
