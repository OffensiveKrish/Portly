package com.portly.feature.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewKindTest {

    @Test
    fun `resolvePreviewKind maps extensions and mime types accurately`() {
        assertEquals(PreviewKind.IMAGE, resolvePreviewKind("photo.jpg", "image/jpeg"))
        assertEquals(PreviewKind.VIDEO, resolvePreviewKind("clip.mp4", "video/mp4"))
        assertEquals(PreviewKind.AUDIO, resolvePreviewKind("song.flac", "audio/flac"))
        assertEquals(PreviewKind.PDF, resolvePreviewKind("doc.pdf", "application/pdf"))
        assertEquals(PreviewKind.CSV, resolvePreviewKind("data.csv", "text/csv"))
        assertEquals(PreviewKind.TEXT, resolvePreviewKind("Main.kt", "application/octet-stream"))
        assertEquals(PreviewKind.ZIP, resolvePreviewKind("bundle.zip", "application/zip"))
        assertEquals(PreviewKind.EXTERNAL_DOC, resolvePreviewKind("deck.pptx", "application/vnd.ms-powerpoint"))
    }
}
