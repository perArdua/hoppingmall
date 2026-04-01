rootProject.name = "payment-service"

val commonPath = if (file("../hoppingmall-common").exists()) "../hoppingmall-common"
    else if (file("/hoppingmall-common").exists()) "/hoppingmall-common"
    else null

commonPath?.let { includeBuild(it) }

val dlqPath = if (file("../hoppingmall-dlq").exists()) "../hoppingmall-dlq"
    else if (file("/hoppingmall-dlq").exists()) "/hoppingmall-dlq"
    else null

dlqPath?.let { includeBuild(it) }
