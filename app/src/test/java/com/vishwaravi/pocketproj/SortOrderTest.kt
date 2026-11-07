package com.vishwaravi.pocketproj

import com.vishwaravi.pocketproj.data.LinkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SortOrderTest {

    private fun link(id: Long, title: String, domain: String, createdAt: Long) =
        LinkEntity(id = id, url = "https://$domain", title = title, domain = domain, createdAt = createdAt)

    private val links = listOf(
        link(1, "alpha", "b.example.com", 100),
        link(2, "Beta", "a.example.com", 300),
        link(3, "alpha again", "c.example.com", 200)
    )

    @Test
    fun newestFirst_ordersByCreatedAtDescending() {
        val result = sortLinks(links, SortOrder.NEWEST_FIRST)
        assertEquals(listOf("Beta", "alpha again", "alpha"), result.map { it.title })
    }

    @Test
    fun oldestFirst_ordersByCreatedAtAscending() {
        val result = sortLinks(links, SortOrder.OLDEST_FIRST)
        assertEquals(listOf("alpha", "alpha again", "Beta"), result.map { it.title })
    }

    @Test
    fun titleAZ_isCaseInsensitive() {
        val result = sortLinks(links, SortOrder.TITLE_A_Z)
        assertEquals(listOf("alpha", "alpha again", "Beta"), result.map { it.title })
    }

    @Test
    fun domainAZ_ordersByDomainCaseInsensitively() {
        val result = sortLinks(links, SortOrder.DOMAIN_A_Z)
        assertEquals(listOf("Beta", "alpha", "alpha again"), result.map { it.title })
    }

    @Test
    fun anyOrder_keepsEmptyListEmpty() {
        assertEquals(emptyList<LinkEntity>(), sortLinks(emptyList(), SortOrder.TITLE_A_Z))
    }
}