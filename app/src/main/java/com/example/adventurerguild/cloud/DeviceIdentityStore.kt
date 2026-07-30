package com.example.adventurerguild.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class DeviceIdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloudflare_identity", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val userId: String
        get() = preferences.getString(USER_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(USER_ID, it).apply()
        }

    fun inheritUserId(userId: String) {
        require(userId.isNotBlank())
        preferences.edit().putString(USER_ID, userId).apply()
    }

    val publicKeyBase64: String
        get() = Base64.encodeToString(keyPair().public.encoded, Base64.NO_WRAP)

    val deviceId: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyPair().public.encoded)
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

    fun sign(message: ByteArray): String {
        val derSignature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair().private)
            update(message)
            sign()
        }
        return Base64.encodeToString(derToP1363(derSignature, 32), Base64.NO_WRAP)
    }

    private fun keyPair(): KeyPair {
        val existingPrivate = keyStore.getKey(KEY_ALIAS, null)
        val existingPublic = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        if (existingPrivate != null && existingPublic != null) {
            return KeyPair(existingPublic, existingPrivate as java.security.PrivateKey)
        }
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKeyPair()
        }
    }

    private fun derToP1363(der: ByteArray, componentSize: Int): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "Invalid ECDSA signature" }
        var offset = 1
        offset += encodedLengthSize(der, offset)
        require(der[offset++] == 0x02.toByte()) { "Invalid ECDSA R value" }
        val rLengthSize = encodedLengthSize(der, offset)
        val rLength = decodedLength(der, offset)
        offset += rLengthSize
        val r = der.copyOfRange(offset, offset + rLength)
        offset += rLength
        require(der[offset++] == 0x02.toByte()) { "Invalid ECDSA S value" }
        val sLengthSize = encodedLengthSize(der, offset)
        val sLength = decodedLength(der, offset)
        offset += sLengthSize
        val s = der.copyOfRange(offset, offset + sLength)
        return fixedUnsigned(r, componentSize) + fixedUnsigned(s, componentSize)
    }

    private fun encodedLengthSize(bytes: ByteArray, offset: Int): Int {
        val first = bytes[offset].toInt() and 0xff
        return if (first < 0x80) 1 else 1 + (first and 0x7f)
    }

    private fun decodedLength(bytes: ByteArray, offset: Int): Int {
        val first = bytes[offset].toInt() and 0xff
        if (first < 0x80) return first
        val count = first and 0x7f
        var length = 0
        repeat(count) { index -> length = (length shl 8) or (bytes[offset + 1 + index].toInt() and 0xff) }
        return length
    }

    private fun fixedUnsigned(value: ByteArray, size: Int): ByteArray {
        val unsigned = value.dropWhile { it == 0.toByte() }.toByteArray()
        require(unsigned.size <= size) { "ECDSA component is too large" }
        return ByteArray(size - unsigned.size) + unsigned
    }

    private companion object {
        const val KEY_ALIAS = "adventurer_guild_cloud_identity_v1"
        const val USER_ID = "user_id"
    }
}
