dependencies {
    // Jetpack Compose (UI)
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Room Database (Storage)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // For coroutines
    ksp("androidx.room:room-compiler:$room_version") // OR kapt

    // Google AI SDK (Gemini)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
