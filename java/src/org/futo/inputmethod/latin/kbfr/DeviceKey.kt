package org.futo.inputmethod.latin.kbfr

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

enum class KeyStorage { STRONGBOX, TEE, SOFTWARE }

// kbfr fork: the device key that signs every backend request (spec 7.2). EC P-256 in the Android
// Keystore, StrongBox when available, else the TEE; not exportable, no user authentication per use.
// Uninstalling the app deletes it, which is what makes a re-signed APK unregistered (spec 8).
object DeviceKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "kbfr_device_key"

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    @Synchronized
    fun ensureExists() {
        if (!keyStore().containsAlias(ALIAS)) generate()
    }

    // After this the backend needs the new public key (spec 7.2, tools/register-device.mjs).
    @Synchronized
    fun regenerate() {
        keyStore().deleteEntry(ALIAS)
        generate()
    }

    private fun generate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(strongBox = true)
                return
            } catch (_: StrongBoxUnavailableException) {
                // No StrongBox chip on this phone: the TEE is next best.
            }
        }
        generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private fun privateKey(): PrivateKey = keyStore().getKey(ALIAS, null) as PrivateKey

    // DER SubjectPublicKeyInfo: the value of the backend secret DEVICE_PUBLIC_KEY_SPKI (base64).
    fun publicKeySpki(): ByteArray = keyStore().getCertificate(ALIAS).publicKey.encoded

    fun storage(): KeyStorage {
        val key = privateKey()
        val info = KeyFactory.getInstance(key.algorithm, KEYSTORE).getKeySpec(key, KeyInfo::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyStorage.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT, KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeyStorage.TEE
                else -> KeyStorage.SOFTWARE
            }
        }
        @Suppress("DEPRECATION")
        return if (info.isInsideSecureHardware) KeyStorage.TEE else KeyStorage.SOFTWARE
    }

    // DER-encoded ECDSA signature, as the backend's crypto.verify expects.
    fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey())
        update(data)
        sign()
    }
}
