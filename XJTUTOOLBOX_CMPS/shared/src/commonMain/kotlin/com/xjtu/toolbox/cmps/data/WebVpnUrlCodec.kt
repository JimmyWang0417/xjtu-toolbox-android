package com.xjtu.toolbox.cmps.data

class WebVpnUrlCodec(
    private val webVpnHost: String = "webvpn.xjtu.edu.cn",
) {
    fun convert(input: String, reversed: Boolean, webVpnReady: Boolean = false): WebVpnConversionState {
        val raw = input.trim()
        if (raw.isBlank()) return WebVpnConversionState(input, "", reversed, webVpnReady, "请先输入网址")
        val converted = if (reversed) originalUrl(raw).orEmpty() else vpnUrl(raw)
        return WebVpnConversionState(
            inputUrl = raw,
            convertedUrl = converted,
            reversed = reversed,
            webVpnReady = webVpnReady,
            error = if (converted.isBlank()) "无法解析此 WebVPN 网址，请确认格式正确" else null,
        )
    }

    fun vpnUrl(input: String): String {
        val normalized = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        if (isWebVpnUrl(normalized)) return normalized
        val protocol = normalized.substringBefore("://", "https")
        val rest = normalized.substringAfter("://", normalized)
        val hostPort = rest.substringBefore("/")
        val suffix = rest.substringAfter("/", "")
        val protocolPort = protocol + hostPort.substringAfter(":", "").takeIf { ":" in hostPort }?.let { "-$it" }.orEmpty()
        val host = hostPort.substringBefore(":")
        val domainToken = host.toWebVpnDomainToken()
        return "https://$webVpnHost/$protocolPort/$domainToken/${suffix}".trimEnd('/')
    }

    fun originalUrl(input: String): String? {
        if (!isWebVpnUrl(input)) return null
        val tail = input
            .removePrefix("https://$webVpnHost/")
            .removePrefix("http://$webVpnHost/")
        val parts = tail.split("/", limit = 3)
        if (parts.size < 2) return null
        val protocolPart = parts[0]
        val protocol = protocolPart.substringBefore("-")
        val port = protocolPart.substringAfter("-", "").takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()
        val host = parts[1].fromWebVpnDomainToken()
        val path = parts.getOrNull(2).orEmpty()
        return "$protocol://$host$port${if (path.isBlank()) "" else "/$path"}"
    }

    fun isWebVpnUrl(input: String): Boolean =
        input.startsWith("https://$webVpnHost") || input.startsWith("http://$webVpnHost")

    private fun String.toWebVpnDomainToken(): String =
        "plain-" + encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

    private fun String.fromWebVpnDomainToken(): String {
        val hex = removePrefix("plain-")
        if (hex.length % 2 != 0) return this
        return runCatching {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
        }.getOrDefault(this)
    }
}
