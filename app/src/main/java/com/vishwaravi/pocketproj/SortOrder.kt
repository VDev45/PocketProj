package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.data.LinkEntity

/**
 * Library sort orders, matching the product design's Sort ▾ menu
 * (Newest First is the default), plus a pure, unit-tested sort helper.
 */
enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    TITLE_A_Z("Title A-Z"),
    DOMAIN_A_Z("Domain A-Z")
}

internal fun sortLinks(links: List<LinkEntity>, order: SortOrder): List<LinkEntity> = when (order) {
    SortOrder.NEWEST_FIRST -> links.sortedByDescending { it.createdAt }
    SortOrder.OLDEST_FIRST -> links.sortedBy { it.createdAt }
    SortOrder.TITLE_A_Z -> links.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SortOrder.DOMAIN_A_Z -> links.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.domain })
}