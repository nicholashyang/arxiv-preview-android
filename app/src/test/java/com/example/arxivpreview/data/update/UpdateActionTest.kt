package com.example.arxivpreview.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateActionTest {
    @Test fun legacyAutomaticWorkNeverDownloads() {
        assertEquals(UpdateAction.CHECK, UpdateAction.fromWorkerInput(automatic = true, downloadOnly = false))
        assertEquals(UpdateAction.CHECK, UpdateAction.fromWorkerInput(automatic = true, downloadOnly = true))
    }

    @Test fun manualWorkPreservesExplicitDownloadIntent() {
        assertEquals(UpdateAction.CHECK, UpdateAction.fromWorkerInput(automatic = false, downloadOnly = false))
        assertEquals(UpdateAction.DOWNLOAD, UpdateAction.fromWorkerInput(automatic = false, downloadOnly = true))
    }
}
