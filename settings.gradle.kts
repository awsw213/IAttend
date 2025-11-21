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
        // Supabase依赖需要的仓库
        maven("https://repo1.maven.org/maven2/")
        maven("https://jitpack.io")
        // 额外的仓库以确保能找到Supabase
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

rootProject.name = "IAttend"
include(":app")
