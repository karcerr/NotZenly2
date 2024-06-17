pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven (url = "https://artifactory-external.vkpartner.ru/artifactory/vkid-sdk-andorid/")
        mavenLocal {
            content {
                includeGroup("com.vk.id")
            }
        }
    }
}

rootProject.name = "TagMe"
include(":app")
 