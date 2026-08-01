package com.xjtu.toolbox.cmps.data

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

internal actual fun currentEpochMillis(): Long = System.currentTimeMillis()

internal actual fun encryptCasPassword(password: String, publicKeyPem: String): String {
    val keyBody = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
        .replace("-----END RSA PUBLIC KEY-----", "")
        .replace("\\s".toRegex(), "")
    require(keyBody.isNotBlank()) { "CAS RSA 公钥为空" }
    val keyBytes = Base64.getDecoder().decode(keyBody)
    val publicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(keyBytes))
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
    return "__RSA__${Base64.getEncoder().encodeToString(encrypted)}"
}
