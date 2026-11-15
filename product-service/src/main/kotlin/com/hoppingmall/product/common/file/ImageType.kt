package com.hoppingmall.product.common.file

import java.io.InputStream

enum class ImageType(val contentType: String, val extensions: Set<String>) {
    JPEG("image/jpeg", setOf("jpg", "jpeg")),
    PNG("image/png", setOf("png")),
    GIF("image/gif", setOf("gif")),
    WEBP("image/webp", setOf("webp"));

    companion object {
        private const val HEADER_LENGTH = 12
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val GIF87A = "GIF87a".toByteArray(Charsets.US_ASCII)
        private val GIF89A = "GIF89a".toByteArray(Charsets.US_ASCII)
        private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
        private val WEBP_MARK = "WEBP".toByteArray(Charsets.US_ASCII)

        fun detect(input: InputStream): ImageType? = detect(input.readNBytes(HEADER_LENGTH))

        fun detect(header: ByteArray): ImageType? = when {
            header.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> JPEG
            header.startsWith(PNG_SIGNATURE) -> PNG
            header.startsWith(GIF87A) || header.startsWith(GIF89A) -> GIF
            header.size >= HEADER_LENGTH && header.startsWith(RIFF) &&
                header.copyOfRange(8, 12).contentEquals(WEBP_MARK) -> WEBP
            else -> null
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
