package com.anthonyla.hotaru.platform.android

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.anthonyla.hotaru.application.ports.ClientInfo
import com.anthonyla.hotaru.application.ports.DeviceIdentity
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.SecureStore
import com.anthonyla.hotaru.domain.DeviceId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@ContributesBinding(AppScope::class)
class AndroidLogger : Logger {
    override fun info(tag: String, message: () -> String) { Log.i("Hotaru/$tag", message()) }
    override fun warn(tag: String, message: () -> String, error: Throwable?) { Log.w("Hotaru/$tag", message(), error) }
    override fun error(tag: String, message: () -> String, error: Throwable?) { Log.e("Hotaru/$tag", message(), error) }
}

@Singleton
@ContributesBinding(AppScope::class)
class AndroidDeviceIdentity(context: Context, override val client: ClientInfo) : DeviceIdentity {
    private val prefs = context.getSharedPreferences("hotaru-device", Context.MODE_PRIVATE)
    override val baseDeviceId: DeviceId = DeviceId(
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() },
    )
    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"
}

/** AES-GCM under an Android Keystore key; ciphertext (IV first) in SharedPreferences. Tokens never sit in plain text. */
@Singleton
@ContributesBinding(AppScope::class)
class KeystoreSecureStore(context: Context) : SecureStore {
    private val prefs = context.getSharedPreferences("hotaru-secure", Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null) as SecretKey? ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        val text = prefs.getString(key, null) ?: return@withContext null
        val bytes = Base64.getDecoder().decode(text)
        // Android can lose a Keystore key while the prefs survive; then the token reads as absent and the user signs in again.
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, this@KeystoreSecureStore.key, GCMParameterSpec(128, bytes, 0, IV_SIZE)) }
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    override suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, this@KeystoreSecureStore.key) }
        val payload = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(key, Base64.getEncoder().encodeToString(payload)).apply()
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) { prefs.edit().remove(key).apply() }

    companion object {
        private const val ALIAS = "hotaru-tokens"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12      // Keystore's GCM IV length
    }
}
