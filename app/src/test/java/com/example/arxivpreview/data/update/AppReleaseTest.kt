package com.example.arxivpreview.data.update

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    @Test fun versionsUseNumericComparisonAndNormalizeMissingParts() {
        assertEquals(ReleaseVersion.parse("v1.0.0"), ReleaseVersion.parse("1.0"))
        assertEquals(ReleaseVersion.parse("1"), ReleaseVersion.parse("1.0.0+12"))
        assertTrue(ReleaseVersion.parse("1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("2.0.0")!! > ReleaseVersion.parse("1.99.99")!!)
    }

    @Test fun invalidAndPrereleaseVersionsAreRejected() {
        listOf("", "latest", "1.2.3-beta", "1.2.3.4", "-1.0", "99999999999999999999999").forEach {
            assertNull(it, ReleaseVersion.parse(it))
        }
    }

    @Test fun existingPreviewAssetNamingIsSupported() {
        val result = AppRelease.parseGithub(release().toString(), "1.0")!!
        assertEquals("1.1.0", result.version)
        assertEquals(42L, result.assetId)
        assertEquals("Release notes", result.notes)
    }

    @Test fun sameAndOlderVersionsAreNotOffered() {
        assertNull(AppRelease.parseGithub(release().toString(), "1.1"))
        assertNull(AppRelease.parseGithub(release().toString(), "1.2.0"))
    }

    @Test fun draftsAndPrereleasesAreIgnored() {
        assertNull(AppRelease.parseGithub(release().put("draft", true).toString(), "1.0"))
        assertNull(AppRelease.parseGithub(release().put("prerelease", true).toString(), "1.0"))
    }

    @Test fun missingApkDoesNotReportUpToDate() {
        assertThrows(IOException::class.java) {
            AppRelease.parseGithub(release().put("assets", JSONArray()).toString(), "1.0")
        }
    }

    @Test fun ambiguousApksAreRejected() {
        val json = release()
        json.getJSONArray("assets").put(asset("arxiv-preview-v1.1.0-arm64.apk"))
        assertThrows(IOException::class.java) { AppRelease.parseGithub(json.toString(), "1.0") }
    }

    @Test fun universalApkIsPreferredOverArchitectureSpecificAssets() {
        val json = release().put("assets", JSONArray().put(asset("arxiv-preview-v1.1.0-arm64.apk"))
            .put(asset("arxiv-preview-v1.1.0-universal.apk")))
        assertTrue(AppRelease.parseGithub(json.toString(), "1.0")!!.downloadUrl.endsWith("-universal.apk"))
    }

    @Test fun unsignedApksAreNeverOffered() {
        val json = release().put("assets", JSONArray().put(asset("arxiv-preview-v1.1.0-unsigned.apk")))
        assertThrows(IOException::class.java) { AppRelease.parseGithub(json.toString(), "1.0") }
    }

    @Test fun checksumIsRequiredAndValidated() {
        listOf("", "null", "sha256:123", "md5:" + "a".repeat(64)).forEach { digest ->
            assertInvalidAsset("digest", digest)
        }
        val json = release()
        json.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + "A".repeat(64))
        assertEquals("a".repeat(64), AppRelease.parseGithub(json.toString(), "1.0")!!.sha256)
    }

    @Test fun untrustedAndInsecureUrlsAreRejected() {
        listOf(
            "http://github.com/${AppRelease.REPOSITORY}/releases/download/v1.1.0/arxiv-preview-v1.1.0-preview.apk",
            "https://example.com/update.apk",
            "https://github.com/attacker/repo/releases/download/v1.1.0/arxiv-preview-v1.1.0-preview.apk",
            "https://github.com/${AppRelease.REPOSITORY}/releases/download/v2.0.0/arxiv-preview-v1.1.0-preview.apk",
        ).forEach { assertInvalidAsset("browser_download_url", it) }
    }

    @Test fun invalidAssetSizesAndIdsAreRejected() {
        listOf(0L, -1L, AppRelease.MAX_APK_BYTES + 1).forEach { assertInvalidAsset("size", it) }
        assertInvalidAsset("id", 0L)
    }

    @Test fun metadataRoundTripRetainsRelease() {
        assertNotNull(AppRelease.parseGithub(JSONObject(release().toString()).toString(), "1.0.0"))
    }

    private fun assertInvalidAsset(key: String, value: Any) {
        val json = release()
        json.getJSONArray("assets").getJSONObject(0).put(key, value)
        assertThrows(IOException::class.java) { AppRelease.parseGithub(json.toString(), "1.0") }
    }

    private fun release() = JSONObject().put("tag_name", "v1.1.0").put("body", "Release notes")
        .put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(asset("arxiv-preview-v1.1.0-preview.apk")))

    private fun asset(name: String) = JSONObject().put("id", 42).put("name", name)
        .put("state", "uploaded").put("size", 1234).put("digest", "sha256:" + "a".repeat(64))
        .put("browser_download_url", "https://github.com/${AppRelease.REPOSITORY}/releases/download/v1.1.0/$name")
}
