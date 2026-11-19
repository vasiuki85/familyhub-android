package com.familyhub.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexDiskImageResolverTest {

    @Test
    fun parseDownloadHref_extractsHrefFromApiResponse() {
        val json = """{"href":"https://downloader.disk.yandex.ru/disk/abc?expires=123","method":"GET","templated":false}"""
        assertEquals(
            "https://downloader.disk.yandex.ru/disk/abc?expires=123",
            parseDownloadHref(json)
        )
    }
}
