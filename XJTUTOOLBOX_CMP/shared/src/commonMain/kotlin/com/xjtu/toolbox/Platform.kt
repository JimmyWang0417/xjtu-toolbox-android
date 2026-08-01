package com.xjtu.toolbox

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
