package com.example.smartfoodup

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform