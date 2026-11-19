package com.familyhub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalImageUrlUtilsTest {

    @Test
    fun normalizeForStorage_acceptsYandexShareLink() {
        val url = "https://disk.yandex.ru/i/abc123"
        assertEquals(url, ExternalImageUrlUtils.normalizeForStorage(url))
        assertTrue(ExternalImageUrlUtils.isYandexDiskPublicUrl(url))
    }

    @Test
    fun normalizeForStorage_acceptsDirectHttpsImage() {
        val url = "https://example.com/avatar.png"
        assertEquals(url, ExternalImageUrlUtils.normalizeForStorage(url))
        assertFalse(ExternalImageUrlUtils.isYandexDiskPublicUrl(url))
        assertFalse(ExternalImageUrlUtils.isYandexDiskPublicUrl("https://evil-yandex.ru/avatar.png"))
    }

    @Test
    fun normalizeForStorage_keepsLocalContentUri() {
        val url = "content://com.familyhub/avatar"
        assertEquals(url, ExternalImageUrlUtils.normalizeForStorage(url))
    }

    @Test
    fun normalizeForStorage_rejectsInvalidScheme() {
        assertNull(ExternalImageUrlUtils.normalizeForStorage("ftp://example.com/a.png"))
    }
}
