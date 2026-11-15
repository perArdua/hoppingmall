package com.hoppingmall.product.common.file.storage

import java.io.InputStream

interface FileStorage {
    fun put(key: String, content: InputStream, size: Long, contentType: String)
    fun delete(key: String)
}
