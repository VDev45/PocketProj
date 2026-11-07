package com.vishwaravi.pocketproj

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test
    fun emptyAndBlankInput_returnsEmptyList() {
        assertEquals(emptyList<String>(), parseTags(""))
        assertEquals(emptyList<String>(), parseTags("   "))
        assertEquals(emptyList<String>(), parseTags(",,  ,,"))
    }

    @Test
    fun singleTag_returnsItTrimmed() {
        assertEquals(listOf("design"), parseTags("  design  "))
    }

    @Test
    fun multipleTags_trimsAndDropsEmpties() {
        assertEquals(listOf("design", "rust", "react"), parseTags(" design, rust ,,react ,"))
    }

    @Test
    fun allBlankEntries_returnsEmptyList() {
        assertEquals(emptyList<String>(), parseTags(" , ,"))
    }
}