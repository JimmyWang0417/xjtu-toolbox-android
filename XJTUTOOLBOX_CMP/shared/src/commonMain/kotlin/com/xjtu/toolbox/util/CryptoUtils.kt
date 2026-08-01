package com.xjtu.toolbox.util

/** 跨平台 RSA 加密 */
expect object CryptoUtils {
    fun rsaEncrypt(data: ByteArray, publicKeyPem: String): ByteArray
    fun sha256(input: String): String
    /** AES/ECB/NoPadding 加密单个 16 字节块（用于 WebVPN CFB128 模式） */
    fun aesEcbEncryptBlock(key: ByteArray, block: ByteArray): ByteArray
}

/** 跨平台 Base64 编解码 */
expect object Base64Utils {
    fun encode(data: ByteArray): String
    fun decode(str: String): ByteArray
}

/** 跨平台 URL 编码 */
expect fun urlEncode(value: String): String
