package com.downflow.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "downflow_settings")

private object Keys {
    val RPC_PORT            = intPreferencesKey("rpc_port")
    val MAX_CONCURRENT      = intPreferencesKey("max_concurrent")
    val MAX_CONNECTIONS     = intPreferencesKey("max_connections")
    val SPEED_LIMIT         = stringPreferencesKey("speed_limit")
    val DOWNLOAD_DIR        = stringPreferencesKey("download_dir")
    val DOWNLOAD_URI        = stringPreferencesKey("download_uri")
    val ENCRYPTED_SECRET    = stringPreferencesKey("encrypted_rpc_secret")
    val SECRET_IV           = stringPreferencesKey("rpc_secret_iv")
}

private const val KEYSTORE_ALIAS = "downflow_rpc_secret_key"
private const val AES_MODE = "AES/GCM/NoPadding"
private const val DEFAULT_RPC_PORT = 6800

data class SettingsSnapshot(
    val rpcPort: Int,
    val rpcSecret: String,
    val downloadDir: String,
    val downloadUri: String,
    val maxConcurrentDownloads: Int,
    val maxConnectionsPerServer: Int,
    val globalSpeedLimit: String,
)

/**
 * Persistent application settings backed by DataStore.
 *
 * The RPC secret is encrypted with AES-256-GCM using a key stored in
 * Android Keystore — it is never in plaintext in SharedPreferences.
 */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    // ─── Flows ───────────────────────────────────────────────────────────────

    val rpcPort: Flow<Int> = dataStore.data.map { it[Keys.RPC_PORT] ?: DEFAULT_RPC_PORT }
    val maxConcurrentDownloads: Flow<Int> = dataStore.data.map { it[Keys.MAX_CONCURRENT] ?: 3 }
    val maxConnectionsPerServer: Flow<Int> = dataStore.data.map { it[Keys.MAX_CONNECTIONS] ?: 16 }
    val globalSpeedLimit: Flow<String> = dataStore.data.map { it[Keys.SPEED_LIMIT] ?: "0" }
    val downloadDir: Flow<String> = dataStore.data.map {
        it[Keys.DOWNLOAD_DIR] ?: defaultDownloadDir()
    }
    val downloadUri: Flow<String> = dataStore.data.map { it[Keys.DOWNLOAD_URI] ?: "" }

    // ─── Snapshot (used by ProcessManager at startup) ────────────────────────

    fun snapshot(): SettingsSnapshot = runBlocking {
        SettingsSnapshot(
            rpcPort = rpcPort.first(),
            rpcSecret = getRpcSecret(),
            downloadDir = downloadDir.first(),
            downloadUri = downloadUri.first(),
            maxConcurrentDownloads = maxConcurrentDownloads.first(),
            maxConnectionsPerServer = maxConnectionsPerServer.first(),
            globalSpeedLimit = globalSpeedLimit.first(),
        )
    }

    // ─── Setters ─────────────────────────────────────────────────────────────

    suspend fun setMaxConcurrentDownloads(value: Int) =
        dataStore.edit { it[Keys.MAX_CONCURRENT] = value.coerceIn(1, 10) }

    suspend fun setMaxConnectionsPerServer(value: Int) =
        dataStore.edit { it[Keys.MAX_CONNECTIONS] = value.coerceIn(1, 16) }

    suspend fun setGlobalSpeedLimit(value: String) =
        dataStore.edit { it[Keys.SPEED_LIMIT] = value }

    suspend fun setDownloadDir(path: String) =
        dataStore.edit { it[Keys.DOWNLOAD_DIR] = path }

    suspend fun setDownloadUri(uri: String) =
        dataStore.edit { it[Keys.DOWNLOAD_URI] = uri }

    // ─── RPC secret management ───────────────────────────────────────────────

    /**
     * Returns the RPC secret, generating and persisting one if none exists.
     * The secret is stored AES-GCM encrypted in DataStore; the key lives in
     * Android Keystore and never leaves the secure hardware element (where available).
     */
    suspend fun getRpcSecret(): String {
        val prefs = dataStore.data.first()
        val encrypted = prefs[Keys.ENCRYPTED_SECRET]
        val ivB64 = prefs[Keys.SECRET_IV]

        if (encrypted != null && ivB64 != null) {
            return try {
                decrypt(encrypted, ivB64)
            } catch (e: Exception) {
                // Key invalidated (e.g. new enrolment) — regenerate
                generateAndPersistSecret()
            }
        }
        return generateAndPersistSecret()
    }

    private suspend fun generateAndPersistSecret(): String {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(raw, Base64.NO_WRAP or Base64.URL_SAFE)
        val (encrypted, iv) = encrypt(secret)
        dataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_SECRET] = encrypted
            prefs[Keys.SECRET_IV] = iv
        }
        return secret
    }

    // ─── Keystore helpers ────────────────────────────────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // background service needs access
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private fun encrypt(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP) to
                Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedB64: String, ivB64: String): String {
        val cipher = Cipher.getInstance(AES_MODE)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(Base64.decode(encryptedB64, Base64.NO_WRAP))
        return String(plain, Charsets.UTF_8)
    }

    private fun defaultDownloadDir(): String =
        context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
}
