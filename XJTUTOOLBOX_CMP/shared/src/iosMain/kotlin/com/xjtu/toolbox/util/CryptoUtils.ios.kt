package com.xjtu.toolbox.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES128
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

actual object CryptoUtils {
    @OptIn(ExperimentalForeignApi::class)
    actual fun rsaEncrypt(data: ByteArray, publicKeyPem: String): ByteArray {
        val keyStr = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyData = Base64Utils.decode(keyStr)
        val nsKeyData = keyData.toNSData()

        val attrs = mapOf<Any?, Any?>(
            kSecAttrKeyTypeRSA to true,
            kSecAttrKeyClassPublic to true
        )

        memScoped {
            val error = alloc<kotlinx.cinterop.COpaquePointerVar>()
            val secKey = SecKeyCreateWithData(
                nsKeyData as kotlinx.cinterop.CFTypeRef,
                attrs as kotlinx.cinterop.CFDictionaryRef,
                error.ptr
            ) ?: throw RuntimeException("Failed to create RSA public key")

            val nsData = data.toNSData()
            val encrypted = SecKeyCreateEncryptedData(
                secKey,
                kSecKeyAlgorithmRSAEncryptionPKCS1,
                nsData as kotlinx.cinterop.CFTypeRef,
                error.ptr
            ) ?: throw RuntimeException("RSA encryption failed")

            return (encrypted as NSData).toByteArray()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun sha256(input: String): String {
        val data = input.encodeToByteArray()
        val hash = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { pinned ->
            hash.usePinned { hashPinned ->
                CC_SHA256(pinned.addressOf(0), data.size.toUInt(), hashPinned.addressOf(0).reinterpret())
            }
        }
        return hash.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun aesEcbEncryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        val output = ByteArray(16)
        memScoped {
            val dataOutMoved = alloc<kotlinx.cinterop.ULongVar>()
            key.usePinned { keyPinned ->
                block.usePinned { blockPinned ->
                    output.usePinned { outputPinned ->
                        val status = CCCrypt(
                            kCCEncrypt,
                            kCCAlgorithmAES128,
                            kCCOptionECBMode.toUInt(),
                            keyPinned.addressOf(0),
                            key.size.toULong(),
                            null,
                            blockPinned.addressOf(0),
                            block.size.toULong(),
                            outputPinned.addressOf(0),
                            output.size.toULong(),
                            dataOutMoved.ptr
                        )
                        if (status != kCCSuccess) {
                            throw RuntimeException("AES ECB encryption failed: $status")
                        }
                    }
                }
            }
        }
        return output
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return memScoped {
            usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        val result = ByteArray(size)
        result.usePinned { pinned ->
            kotlinx.cinterop.memcpy(pinned.addressOf(0), bytes, length)
        }
        return result
    }
}

actual object Base64Utils {
    actual fun encode(data: ByteArray): String {
        val nsData = data.let { bytes ->
            if (bytes.isEmpty()) NSData()
            else bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        return nsData.base64EncodedStringWithOptions(0u)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun decode(str: String): ByteArray {
        val nsData = NSData.create(base64EncodedString = str, options = 0u) ?: return ByteArray(0)
        val size = nsData.length.toInt()
        if (size == 0) return ByteArray(0)
        val result = ByteArray(size)
        result.usePinned { pinned ->
            kotlinx.cinterop.memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        return result
    }
}

actual fun urlEncode(value: String): String {
    return (value as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
        platform.Foundation.NSCharacterSet.URLQueryAllowedCharacterSet
    ) ?: value
}
