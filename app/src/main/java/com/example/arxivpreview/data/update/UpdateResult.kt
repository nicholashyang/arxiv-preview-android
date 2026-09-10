package com.example.arxivpreview.data.update

/** Detection never downloads. Download completion is a separate notification event. */
sealed interface UpdateResult {
    data object NoChange : UpdateResult
    data class Available(val release: AppRelease) : UpdateResult
    data class Downloaded(val release: AppRelease) : UpdateResult
}

val AppRelease.reminderKey: String get() = "$version:$assetId:$sha256"

/** Old automatic work must never download, even if it carries a legacy download flag. */
enum class UpdateAction {
    CHECK, DOWNLOAD;

    companion object {
        fun fromWorkerInput(automatic: Boolean, downloadOnly: Boolean): UpdateAction =
            if (!automatic && downloadOnly) DOWNLOAD else CHECK
    }
}
