package com.xjtu.toolbox.util

import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Kotlin Common 跨平台浮点数格式化工具
 * 替代 JVM-only 的 String.format("%.Nf", value)
 */
fun Double.formatDecimal(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToLong()
    val isNegative = rounded < 0
    val abs = rounded.absoluteValue
    val intPart = abs / factor.toLong()
    val fracPart = abs % factor.toLong()
    val fracStr = fracPart.toString().padStart(decimals, '0')
    return buildString {
        if (isNegative) append('-')
        append(intPart)
        if (decimals > 0) {
            append('.')
            append(fracStr)
        }
    }
}

fun Float.formatDecimal(decimals: Int): String = this.toDouble().formatDecimal(decimals)
