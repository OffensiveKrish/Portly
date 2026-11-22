pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Portly"

include(":app")

include(":core:core-model")
include(":core:core-common")
include(":core:core-security")
include(":core:core-database")
include(":core:core-datastore")
include(":core:core-ui")

include(":transfer:transfer-engine")
include(":network:network-manager")

include(":protocol:protocol-ftp-client")
include(":protocol:protocol-ftps-client")
include(":protocol:protocol-sftp-client")
include(":protocol:protocol-webdav-client")
include(":protocol:protocol-smb-client")
include(":protocol:protocol-ftp-server")
include(":protocol:protocol-ftps-server")
include(":protocol:protocol-http-server")
include(":protocol:protocol-https-server")

include(":feature:feature-home")
include(":feature:feature-filemanager")
include(":feature:feature-connections")
include(":feature:feature-servers")
include(":feature:feature-transfers")
include(":feature:feature-history")
include(":feature:feature-settings")
include(":feature:feature-preview")
include(":feature:feature-archive")
