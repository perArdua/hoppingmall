rootProject.name = "hoppingmall-outbox"

val commonPath = if (file("../hoppingmall-common").exists()) "../hoppingmall-common"
    else if (file("/hoppingmall-common").exists()) "/hoppingmall-common"
    else null

commonPath?.let { includeBuild(it) }
