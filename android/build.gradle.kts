// Top-level build file — adapted from the proven DevxyzCode scaffold.
// ONE deliberate deviation from the template: AGP 8.5.2 cannot target
// Android 16 (API 36), so this project uses AGP 8.7.3. Gradle 8.9,
// Kotlin 2.0.20 and JDK 17 are unchanged.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
