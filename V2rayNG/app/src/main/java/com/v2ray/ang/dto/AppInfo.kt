package com.v2ray.ang.dto

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    var isSelected: Int,
    val category: com.v2ray.ang.handler.AppCategory = com.v2ray.ang.handler.AppCategory.OTHER,
)