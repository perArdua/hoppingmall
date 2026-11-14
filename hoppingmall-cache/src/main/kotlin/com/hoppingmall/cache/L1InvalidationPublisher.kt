package com.hoppingmall.cache

interface L1InvalidationPublisher {
    fun publishEvict(cacheName: String, key: Any)
    fun publishClear(cacheName: String)
}
