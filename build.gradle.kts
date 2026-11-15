plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Kotlin formatting and lint (docs/conventions.md, "포맷 · 린트"). The rules themselves are in .editorconfig.
spotless {
    // Only files that differ from origin/main are formatted and checked, so the existing code is not rewritten
    // in one sweep. A file is brought up to the rules the first time someone changes it.
    ratchetFrom("origin/main")
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// Git runs the hooks in .githooks (format on commit, check on push) once core.hooksPath points there.
// Every build makes sure it does, so nobody has to remember to install them.
val gitDir = rootDir.resolve(".git")
val installGitHooks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Points git at the versioned hooks in .githooks."
    onlyIf { gitDir.exists() }
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(installGitHooks) }
}
