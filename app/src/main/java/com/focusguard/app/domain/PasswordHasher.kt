package com.focusguard.app.domain

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordHasher @Inject constructor() {
    private val random = SecureRandom()
    private val iterations = 180_000
    private val keyLengthBits = 256

    data class HashResult(val saltBase64: String, val hashBase64: String)

    fun hash(secret: CharArray): HashResult {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(secret, salt)
        secret.fill('\u0000')
        return HashResult(
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun verify(secret: CharArray, saltBase64: String, expectedBase64: String): Boolean {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val actual = derive(secret, salt)
        secret.fill('\u0000')
        val expected = Base64.decode(expectedBase64, Base64.NO_WRAP)
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    fun generateRecoveryCode(): String {
        val value = random.nextInt(90_000_000) + 10_000_000
        return value.toString()
    }

    private fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
