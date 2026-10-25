package com.v2ray.ang.dto

/**
 * HTTP body plus response headers used by subscription clients (3x-ui compatible).
 */
data class UrlContentResult(
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)
