rootProject.name = "payment-service"

val commonPath = if (file("../hoppingmall-common").exists()) "../hoppingmall-common"
    else if (file("/hoppingmall-common").exists()) "/hoppingmall-common"
    else null

commonPath?.let { includeBuild(it) }

val dlqPath = if (file("../hoppingmall-dlq").exists()) "../hoppingmall-dlq"
    else if (file("/hoppingmall-dlq").exists()) "/hoppingmall-dlq"
    else null

dlqPath?.let { includeBuild(it) }

val outboxPath = if (file("../hoppingmall-outbox").exists()) "../hoppingmall-outbox"
    else if (file("/hoppingmall-outbox").exists()) "/hoppingmall-outbox"
    else null

outboxPath?.let { includeBuild(it) }

val cachePath = if (file("../hoppingmall-cache").exists()) "../hoppingmall-cache"
    else if (file("/hoppingmall-cache").exists()) "/hoppingmall-cache"
    else null

cachePath?.let { includeBuild(it) }
