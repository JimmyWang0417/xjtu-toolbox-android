package com.xjtu.toolbox.util

/**
 * WebVPN URL 加密工具
 * 使用 AES-128-CFB 加密域名，将普通 URL 转换为 WebVPN 代理 URL
 * 算法与 XJTUToolBox Python 版本完全一致
 */
object WebVpnUtil {

    private const val INSTITUTION = "webvpn.xjtu.edu.cn"
    private val KEY = "wrdvpnisthebest!".encodeToByteArray()
    private val IV = "wrdvpnisthebest!".encodeToByteArray()
    private val IV_HEX = IV.joinToString("") { b -> (b.toInt() and 0xFF).let { "${HEX_CHARS[it shr 4]}${HEX_CHARS[it and 0x0F]}" } }
    private const val HEX_CHARS = "0123456789abcdef"

    const val WEBVPN_LOGIN_URL = "https://webvpn.xjtu.edu.cn/login?cas_login=true"

    /**
     * AES-128-CFB 加密（segment_size=128）
     * 使用 expect/actual 的 aesEcbEncryptBlock 作为原语
     */
    private fun cfb128Encrypt(plaintext: ByteArray): ByteArray {
        val result = ByteArray(plaintext.size)
        var feedback = IV.copyOf()
        var offset = 0

        while (offset < plaintext.size) {
            val encrypted = CryptoUtils.aesEcbEncryptBlock(KEY, feedback)
            val blockLen = minOf(16, plaintext.size - offset)
            for (j in 0 until blockLen) {
                result[offset + j] = (plaintext[offset + j].toInt() xor encrypted[j].toInt()).toByte()
            }
            if (offset + 16 <= result.size) {
                feedback = result.copyOfRange(offset, offset + 16)
            }
            offset += 16
        }

        return result
    }

    /**
     * 加密域名为十六进制字符串
     */
    private fun encryptHostname(hostname: String): String {
        val encrypted = cfb128Encrypt(hostname.encodeToByteArray())
        return encrypted.joinToString("") { b -> (b.toInt() and 0xFF).let { "${HEX_CHARS[it shr 4]}${HEX_CHARS[it and 0x0F]}" } }
    }

    /**
     * 将普通 URL 转换为 WebVPN 代理 URL
     */
    fun getVpnUrl(url: String): String {
        val parts = url.split("://", limit = 2)
        if (parts.size < 2) return url

        val protocol = parts[0]
        val rest = parts[1]

        val segments = rest.split("/", limit = 2)
        val hostPort = segments[0]
        val path = if (segments.size > 1) segments[1] else ""

        val domain = hostPort.split(":")[0]
        val port = if (":" in hostPort) "-${hostPort.split(":")[1]}" else ""

        val encryptedDomain = encryptHostname(domain)

        return "https://$INSTITUTION/$protocol$port/$IV_HEX$encryptedDomain/$path"
    }

    /**
     * 判断 URL 是否已是 WebVPN URL
     */
    fun isWebVpnUrl(url: String): Boolean =
        url.startsWith("https://$INSTITUTION") || url.startsWith("http://$INSTITUTION")

    /**
     * AES-128-CFB 解密（与加密对称）
     */
    private fun cfb128Decrypt(ciphertext: ByteArray): ByteArray {
        val result = ByteArray(ciphertext.size)
        var feedback = IV.copyOf()
        var offset = 0

        while (offset < ciphertext.size) {
            val encrypted = CryptoUtils.aesEcbEncryptBlock(KEY, feedback)
            val blockLen = minOf(16, ciphertext.size - offset)
            for (j in 0 until blockLen) {
                result[offset + j] = (ciphertext[offset + j].toInt() xor encrypted[j].toInt()).toByte()
            }
            if (offset + 16 <= ciphertext.size) {
                feedback = ciphertext.copyOfRange(offset, offset + 16)
            }
            offset += 16
        }
        return result
    }

    /**
     * 从十六进制字符串解密域名
     */
    private fun decryptHostname(hex: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return cfb128Decrypt(bytes).decodeToString()
    }

    /**
     * 将 WebVPN 代理 URL 还原为原始 URL
     */
    fun getOriginalUrl(vpnUrl: String): String? {
        if (!isWebVpnUrl(vpnUrl)) return null

        val path = vpnUrl.removePrefix("https://$INSTITUTION/").removePrefix("http://$INSTITUTION/")
        if (path.isBlank()) return null

        val segments = path.split("/", limit = 3)
        if (segments.size < 2) return null

        val protocolPort = segments[0]
        val hexPart = segments[1]
        val restPath = if (segments.size > 2) segments[2] else ""

        val protocol: String
        val port: String
        if ("-" in protocolPort) {
            val idx = protocolPort.indexOf("-")
            protocol = protocolPort.substring(0, idx)
            port = ":${protocolPort.substring(idx + 1)}"
        } else {
            protocol = protocolPort
            port = ""
        }

        if (hexPart.length <= 32) return null
        val encryptedHex = hexPart.substring(32)

        return try {
            val domain = decryptHostname(encryptedHex)
            val pathSuffix = if (restPath.isNotEmpty()) "/$restPath" else ""
            "$protocol://$domain$port$pathSuffix"
        } catch (_: Exception) {
            null
        }
    }
}
