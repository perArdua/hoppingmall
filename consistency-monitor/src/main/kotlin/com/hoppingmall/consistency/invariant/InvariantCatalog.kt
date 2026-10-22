package com.hoppingmall.consistency.invariant

import com.hoppingmall.consistency.config.ConsistencyProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class InvariantCatalog(
    resourceLoader: ResourceLoader,
    properties: ConsistencyProperties
) {
    val definitions: List<InvariantDefinition> = resourceLoader.getResource(properties.sqlResource)
        .inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        .let { InvariantSqlParser.parse(it, properties.params) }
}
