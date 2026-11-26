package com.sreevachan.attendance.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class KeySetupOutput(
    val recoveryCode: String,
    val rawDataKey: ByteArray,
    val dataKey: SecretKey,
    val meta: CryptoMeta,
)

/**
 * AES-256-GCM + PBKDF2-HMAC-SHA256 implementation matching docs/index.html byte-for-byte
 * so encrypted Firestore backups are interoperable across Android, iOS, and Web.
 */
object CryptoHelper {
    const val PASS_ITERS = 310_000
    const val REC_ITERS = 100_000
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    private fun rnd(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun keyFromRaw(raw: ByteArray): SecretKey = SecretKeySpec(raw, "AES")

    private fun deriveKek(secret: String, salt: ByteArray, iters: Int): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), salt, iters, 256)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: SecretKey, plainBytes: ByteArray): String {
        val iv = rnd(IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plainBytes)
        val out = ByteArray(IV_LEN + ct.size)
        System.arraycopy(iv, 0, out, 0, IV_LEN)
        System.arraycopy(ct, 0, out, IV_LEN, ct.size)
        return b64(out)
    }

    fun unseal(key: SecretKey, sealedB64: String): ByteArray {
        val all = unb64(sealedB64)
        require(all.size > IV_LEN) { "Invalid sealed payload" }
        val iv = all.copyOfRange(0, IV_LEN)
        val ct = all.copyOfRange(IV_LEN, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    fun newRecoveryCode(): String =
        rnd(16).joinToString("") { "%02X".format(it) }

    fun formatRecoveryCode(code: String): String =
        code.chunked(4).joinToString("-")

    fun normalizeRecoveryInput(input: String): String =
        input.uppercase().replace(Regex("[^0-9A-F]"), "")

    fun setupKeys(passphrase: String): KeySetupOutput {
        val salt = rnd(16)
        val recSalt = rnd(16)
        val code = newRecoveryCode()

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, rng)
        val dk = keyGen.generateKey()
        val raw = dk.encoded

        val passKek = deriveKek(passphrase, salt, PASS_ITERS)
        val recKek = deriveKek(code, recSalt, REC_ITERS)

        val wrapPass = seal(passKek, raw)
        val wrapRec = seal(recKek, raw)

        return KeySetupOutput(
            recoveryCode = code,
            rawDataKey = raw,
            dataKey = dk,
            meta = CryptoMeta(
                salt = b64(salt),
                iters = PASS_ITERS,
                wrapPass = wrapPass,
                recSalt = b64(recSalt),
                wrapRec = wrapRec,
            ),
        )
    }

    fun unlockWith(secret: String, meta: CryptoMeta, isRecovery: Boolean): Pair<SecretKey, ByteArray> {
        val salt = unb64(if (isRecovery) meta.recSalt else meta.salt)
        val iters = if (isRecovery) REC_ITERS else meta.iters
        val wrapped = if (isRecovery) meta.wrapRec else meta.wrapPass
        val kek = deriveKek(secret, salt, iters)
        val raw = unseal(kek, wrapped)
        return keyFromRaw(raw) to raw
    }
}
