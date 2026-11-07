package com.vishwaravi.pocketproj

/**
 * Splits a raw comma-separated tag string into trimmed, non-blank tag names.
 * Used for filtering, tag menus, and card rendering.
 */
internal fun parseTags(raw: String): List<String> =
    raw.split(",").map(String::trim).filter(String::isNotBlank)