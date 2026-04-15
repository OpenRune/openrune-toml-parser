plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "openrune-toml-parser"

include(":toml-core")
include(":toml-annotations")
include(":toml-rsconfig")
include(":konbini")