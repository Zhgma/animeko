plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
}
kotlin {
    wasmJs { browser() }
    sourceSets.commonMain {
        kotlin.srcDir("../../app/shared/app-lang/src/commonMain/kotlin")
        kotlin.include("Lang.kt", "me/him188/ani/app/ui/lang/**")
        dependencies { api(compose.components.resources); implementation(compose.ui) }
    }
}
compose.resources {
    publicResClass = true
    packageOfResClass = "me.him188.ani.app.ui.lang"
    customDirectory("commonMain", provider { layout.projectDirectory.dir("../../app/shared/app-lang/src/androidMain/res") })
}
