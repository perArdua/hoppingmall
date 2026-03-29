package com.hoppingmall.common.config

import org.hibernate.cfg.AvailableSettings
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.stereotype.Component

class TraceCommentStatementInspector : StatementInspector {

    override fun inspect(sql: String): String {
        val traceId = MDC.get("traceId") ?: return sql
        val userId = MDC.get("userId")
        val comment = buildString {
            append("/* traceId=$traceId")
            if (userId != null) append(" userId=$userId")
            append(" */")
        }
        return "$comment $sql"
    }
}

@Component
class TraceCommentHibernateCustomizer : HibernatePropertiesCustomizer {
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.STATEMENT_INSPECTOR] = TraceCommentStatementInspector()
    }
}
