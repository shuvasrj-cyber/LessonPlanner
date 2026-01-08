package com.nepal.planner

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.vertexai.FirebaseVertexAI
import kotlinx.coroutines.tasks.await

class LessonRepository {
    private val db = FirebaseFirestore.getInstance()
    private val model = FirebaseVertexAI.getInstance().generativeModel("gemini-1.5-flash")

    // Logic to ask AI for a Nepali plan
    suspend fun generatePlan(input: LessonPlan): String {
        val prompt = "तपाईं नेपालको एक शिक्षक हुनुहुन्छ। कक्षा ${input.className}, पाठ ${input.lessonName} को लागि नेपालीमा पाठ योजना बनाउनुहोस्।"
        return model.generateContent(prompt).text ?: ""
    }

    // CRUD Operations
    suspend fun save(plan: LessonPlan) = db.collection("plans").add(plan).await()
    suspend fun update(plan: LessonPlan) = db.collection("plans").document(plan.id).set(plan).await()
    suspend fun delete(id: String) = db.collection("plans").document(id).delete().await()
    
    fun getHistory() = db.collection("plans").orderBy("date")
}
