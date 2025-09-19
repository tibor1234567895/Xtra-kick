package com.github.andreyasadchy.xtra.util

private val AUTH_SCHEMES = setOf("bearer", "oauth")

fun String?.stripAuthPrefix(): String? {
    val value = this?.trim()
    if (value.isNullOrBlank()) {
        return null
    }
    val spaceIndex = value.indexOf(' ')
    if (spaceIndex == -1) {
        return value
    }
    val scheme = value.substring(0, spaceIndex)
    return if (scheme.lowercase() in AUTH_SCHEMES) {
        value.substring(spaceIndex + 1).trim().takeIf { it.isNotBlank() }
    } else {
        value
    }
}
