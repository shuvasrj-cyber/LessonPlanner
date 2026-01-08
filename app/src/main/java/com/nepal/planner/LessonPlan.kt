package com.nepal.planner

data class LessonPlan(
    val id: String = "", // Document ID from Firestore
    val className: String = "",
    val lessonName: String = "",
    val topic: String = "",
    val subtopics: String = "",
    val content: String = "", // Nepali AI text
    val date: Long = System.currentTimeMillis()
)
