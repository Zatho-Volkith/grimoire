package dev.grimoire.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {
    private val latest = """
        {"tag_name":"v1.2.0","html_url":"https://example.test/rel","body":"notes here","assets":[
          {"name":"grimoire-1.2.36.msi","browser_download_url":"https://example.test/g.msi"},
          {"name":"grimoire_1.2.0_amd64.deb","browser_download_url":"https://example.test/g.deb"}
        ]}
    """.trimIndent()

    @Test
    fun newerReleaseIsReturnedWithMsiAsset() = runTest {
        val release = UpdateChecker({ latest }, currentVersion = "1.0.1").check()
        assertEquals(Release("1.2.0", "notes here", "https://example.test/rel", "https://example.test/g.msi"), release)
    }

    @Test
    fun currentOrOlderReleaseIsNull() = runTest {
        assertNull(UpdateChecker({ latest }, currentVersion = "1.2.0").check())
        assertNull(UpdateChecker({ latest }, currentVersion = "2.0.0").check())
    }

    @Test
    fun releaseWithoutMsiOrNotesStillWorks() = runTest {
        val bare = """{"tag_name":"v9.0.0","html_url":"https://example.test/rel","body":null,"assets":[]}"""
        assertEquals(Release("9.0.0", "", "https://example.test/rel", null), UpdateChecker({ bare }, "1.0.1").check())
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(isNewer("1.10.0", "1.9.0"))
        assertFalse(isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun garbageOrTruncatedVersionsAreNeverNewer() {
        assertFalse(isNewer("nightly", "1.0.0"))
        assertFalse(isNewer("1.0", "0.9.0"))
    }
}
