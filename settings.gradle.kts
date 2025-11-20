plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "vertex-backend"

include("common")
include("module-file")
include("module-blog")
include("app-bootstrap")
include("module-search")