// This "plugins" block defines versions for the whole project
plugins {
    // Android Application Plugin (use a version compatible with Gradle 9+)
    id("com.android.application") version "8.7.0" apply false
    
    // Kotlin Android Plugin
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    
    // Google Services Plugin (for Firebase)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
