package com.example.arxivpreview.data.update

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Numeric release versions: 1, 1.0 and v1.0.0 denote the same version. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\+[0-9A-Za-z.-]+)?$")
                .matchEntire(value.trim()) ?: return null
            val parts = (1..3).map { index ->
                match.groupValues[index].ifEmpty { "0" }.toLongOrNull() ?: return null
            }
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class AppRelease(
    val version: String,
    val notes: String,
    val assetId: Long,
    val downloadUrl: String,
    val bytes: Long,
    val sha256: String,
) {
    companion object {
        const val REPOSITORY = "nicholashyang/arxiv-preview-android"
        const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val MAX_APK_BYTES = 250L * 1024 * 1024

        /** Only a stable, newer release with an unambiguous, verifiable APK is offered. */
        fun parseGithub(json: String, installedVersion: String): AppRelease? {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
            val tag = root.getString("tag_name")
            val remote = ReleaseVersion.parse(tag)
                ?: throw IOException("The release has an unsupported version: $tag")
            val installed = ReleaseVersion.parse(installedVersion)
                ?: throw IOException("The installed app has an unsupported version.")
            if (remote <= installed) return null
            val assets = root.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                val name = it.optString("name")
                name.startsWith("arxiv-preview-") && name.endsWith(".apk") &&
                    !name.contains("unsigned", ignoreCase = true) && it.optString("state") == "uploaded"
            }
            val preferred = apks.filter {
                it.getString("name") in setOf("arxiv-preview-$tag.apk", "arxiv-preview-$tag-universal.apk")
            }
            val apk = preferred.singleOrNull() ?: apks.singleOrNull()
                ?: throw IOException("The latest release does not contain a single compatible APK.")
            val url = apk.getString("browser_download_url").toHttpUrlOrNull()
            val expectedPath = REPOSITORY.split('/') + listOf("releases", "download", tag, apk.getString("name"))
            if (url == null || !url.isHttps || url.host != "github.com" || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty() || url.pathSegments != expectedPath
            ) throw IOException("The release APK has an untrusted download address.")
            val digest = apk.optString("digest")
            if (!Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) {
                throw IOException("The release APK is missing a SHA-256 checksum.")
            }
            val size = apk.getLong("size")
            val id = apk.getLong("id")
            if (size !in 1..MAX_APK_BYTES || id <= 0) throw IOException("Invalid release APK metadata.")
            return AppRelease(
                version = tag.removePrefix("v").removePrefix("V"),
                notes = root.optString("body").take(8_000),
                assetId = id,
                downloadUrl = url.toString(),
                bytes = size,
                sha256 = digest.substringAfter(':').lowercase(),
            )
        }
    }
}
