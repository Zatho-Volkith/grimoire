import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
}

group = "dev.grimoire"
version = "0.6.1"

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

sqldelight {
    databases {
        create("GrimoireDatabase") {
            packageName.set("dev.grimoire.db")
            dialect(libs.sqldelight.dialect.sqlite338)
            // Migrations live next to the schema as N.sqm. `databases/N.db` are frozen snapshots of each
            // released schema version; the build replays every migration against them and fails if the
            // result diverges from Grimoire.sq. Regenerate the snapshot for a NEW version with
            // ./gradlew generateCommonMainGrimoireDatabaseSchema — never overwrite a shipped one.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.grimoire.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "grimoire"
            description = "Local-first worldbuilding and writing for fanfic and original fiction"
            vendor = "Grimoire"

            // jpackage requires MAJOR >= 1 for MSI ProductVersions and for the whole macOS
            // .app/DMG, so those use the build counter ("1.0.<build>") while the app keeps the
            // real semver. Deb takes semver as-is.
            val installerVersion = "1.0.${file("VERSION").readText().trim()}"
            windows {
                msiPackageVersion = installerVersion
                menu = true
                shortcut = true
                // Stable across releases so a newer MSI upgrades an older install in place.
                upgradeUuid = "69e2a6ee-3ab2-4907-9f4c-c005a2014847"
            }
            macOS {
                packageVersion = installerVersion // applies to the .app bundle AND the dmg
            }
        }
    }
}
