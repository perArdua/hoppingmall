rootProject.name = "order-service"

val commonPath = if (file("../hoppingmall-common").exists()) "../hoppingmall-common"
    else if (file("/hoppingmall-common").exists()) "/hoppingmall-common"
    else null

commonPath?.let { includeBuild(it) }

val idempotencyPath = if (file("../hoppingmall-idempotency").exists()) "../hoppingmall-idempotency"
    else if (file("/hoppingmall-idempotency").exists()) "/hoppingmall-idempotency"
    else null

idempotencyPath?.let { includeBuild(it) }

val dlqPath = if (file("../hoppingmall-dlq").exists()) "../hoppingmall-dlq"
    else if (file("/hoppingmall-dlq").exists()) "/hoppingmall-dlq"
    else null

dlqPath?.let { includeBuild(it) }

val outboxPath = if (file("../hoppingmall-outbox").exists()) "../hoppingmall-outbox"
    else if (file("/hoppingmall-outbox").exists()) "/hoppingmall-outbox"
    else null

outboxPath?.let { includeBuild(it) }
