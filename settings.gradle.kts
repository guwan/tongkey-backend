rootProject.name = "tongkey-backend"

// 依赖解析统一在 settings 中声明，避免与全局 ~/.gradle/init.gradle.kts 仓库注入冲突
dependencyResolutionManagement {
    // 允许 build.gradle.kts 中补充仓库（PREFER_SETTINGS = settings 优先）
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        // 国内镜像（如不需要可删除）
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
