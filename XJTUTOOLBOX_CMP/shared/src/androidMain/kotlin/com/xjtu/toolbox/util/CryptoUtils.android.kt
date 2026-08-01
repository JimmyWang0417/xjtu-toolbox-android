package com.xjtu.toolbox.util

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

actual object CryptoUtils {
    actual fun rsaEncrypt(data: ByteArray, publicKeyPem: String): ByteArray {
        val keyStr = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.decode(keyStr, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")

        val publicKey = try {
            keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
        } catch (_: Exception) {
            // PKCS#1 → X.509 wrapping
            val pkcs1Header = byteArrayOf(
                0x30.toByte(), 0x82.toByte(), 0x00.toByte(), 0x00.toByte(),
                0x30.toByte(), 0x0D.toByte(),
                0x06.toByte(), 0x09.toByte(),
                0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(),
                0xF7.toByte(), 0x0D.toByte(), 0x01.toByte(), 0x01.toByte(),
                0x01.toByte(),
                0x05.toByte(), 0x00.toByte(),
                0x03.toByte(), 0x82.toByte(), 0x00.toByte(), 0x00.toByte()
            )
            val bitStringContent = byteArrayOf(0x00.toByte()) + keyBytes
            val bitStringLen = bitStringContent.size
            pkcs1Header[pkcs1Header.size - 2] = ((bitStringLen shr 8) and 0xFF).toByte()
            pkcs1Header[pkcs1Header.size - 1] = (bitStringLen and 0xFF).toByte()
            val inner = pkcs1Header.sliceArray(4 until pkcs1Header.size) + bitStringContent
            val totalLen = inner.size
            val x509Bytes = byteArrayOf(
                0x30.toByte(), 0x82.toByte(),
                ((totalLen shr 8) and 0xFF).toByte(),
                (totalLen and 0xFF).toByte()
            ) + inner
            keyFactory.generatePublic(X509EncodedKeySpec(x509Bytes))
        }

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    actual fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    actual fun aesEcbEncryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }
}

actual object Base64Utils {
    actual fun encode(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    actual fun decode(str: String): ByteArray = Base64.decode(str, Base64.DEFAULT)
}

actual fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
