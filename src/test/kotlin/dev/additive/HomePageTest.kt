package dev.additive

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class HomePageTest {
    @Test
    fun `homepage has the additive title`() {
        val page = assertNotNull(javaClass.getResource("/static/index.html"))

        assertContains(page.readText(), "<title>additive</title>")
    }
}
