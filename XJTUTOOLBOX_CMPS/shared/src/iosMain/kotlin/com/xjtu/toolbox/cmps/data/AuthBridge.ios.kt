package com.xjtu.toolbox.cmps.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import platform.Foundation.NSDate
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

internal actual fun currentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@OptIn(ExperimentalForeignApi::class)
internal actual fun encryptCasPassword(password: String, publicKeyPem: String): String {
    val keyBody = publicKeyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
        .replace("-----END RSA PUBLIC KEY-----", "")
        .replace("\\s".toRegex(), "")
    require(keyBody.isNotBlank()) { "CAS RSA 公钥为空" }
    val keyData = NSData.create(base64EncodedString = keyBody, options = 0u)
        ?: error("CAS RSA 公钥 Base64 解析失败")
    val plainData = NSString.create(string = password).dataUsingEncoding(NSUTF8StringEncoding)
        ?: error("CAS 密码编码失败")
    val attrs = NSMutableDictionary.dictionary().apply {
        setObject(kSecAttrKeyTypeRSA, forKey = kSecAttrKeyType)
        setObject(kSecAttrKeyClassPublic, forKey = kSecAttrKeyClass)
        setObject(NSNumber.numberWithInt(2048), forKey = kSecAttrKeySizeInBits)
    }
    return memScoped {
        val keyError = alloc<platform.CoreFoundation.CFErrorRefVar>()
        val key = SecKeyCreateWithData(
            keyData as platform.CoreFoundation.CFDataRef,
            attrs as platform.CoreFoundation.CFDictionaryRef,
            keyError.ptr,
        )
            ?: error("CAS RSA 公钥导入失败")
        val encryptError = alloc<platform.CoreFoundation.CFErrorRefVar>()
        val encrypted = SecKeyCreateEncryptedData(
            key = key,
            algorithm = kSecKeyAlgorithmRSAEncryptionPKCS1,
            plaintext = plainData as platform.CoreFoundation.CFDataRef,
            error = encryptError.ptr,
        ) ?: error("CAS RSA 加密失败")
        "__RSA__${(encrypted as NSData).base64EncodedStringWithOptions(0u)}"
    }
}
