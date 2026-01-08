package com.example.lessonplanner

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.OutputStream

// ==============================
// 1. DATA LAYER
// ==============================

@Entity(tableName = "lesson_plans")
data class LessonPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val className: String,
    val date: String,
    val period: String,
    val teacherName: String,     // NEW
    val principalName: String,   // NEW
    val totalStudents: String,   // NEW
    val description: String,
    val generatedPlan: String
)

@Dao
interface LessonPlanDao {
    @Query("SELECT * FROM lesson_plans ORDER BY id DESC")
    fun getAllPlans(): Flow<List<LessonPlan>>

    @Insert
    suspend fun insertPlan(plan: LessonPlan)
}

// Version 2, fall back to destructive migration if version mismatches
@Database(entities = [LessonPlan::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonPlanDao(): LessonPlanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "lesson_db")
                    .fallbackToDestructiveMigration() // Wipe data if schema changes
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ==============================
// 2. VIEWMODEL
// ==============================

class LessonViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.lessonPlanDao()
    
    // PASTE YOUR API KEY HERE
    private val apiKey = "YOUR_GEMINI_API_KEY_HERE"
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey
    )

    val savedPlans: Flow<List<LessonPlan>> = dao.getAllPlans()
    
    var isLoading by mutableStateOf(false)
    var currentGeneratedPlan by mutableStateOf("")
    var generationError by mutableStateOf<String?>(null)

    fun generateLessonPlan(
        topic: String, 
        className: String, 
        teacher: String,
        principal: String,
        students: String,
        desc: String
    ) {
        viewModelScope.launch {
            isLoading = true
            generationError = null
            try {
                // Modified prompt for NEPALI output
                val prompt = """
                    Act as an expert Nepali teacher. Create a detailed lesson plan in the NEPALI language (Devanagari script).
                    
                    Details:
                    - Teacher Name: $teacher
                    - Principal Name: $principal
                    - Class: $className
                    - Total Students: $students
                    - Topic: $topic
                    - Context/Specific Needs: $desc
                    
                    Structure the lesson plan clearly with:
                    1. General Objectives (साधारण उद्देश्यहरू)
                    2. Specific Objectives (विशिष्ट उद्देश्यहरू)
                    3. Teaching Materials (शैक्षिक सामग्रीहरू)
                    4. Teaching Activities (शिक्षण क्रियाकलाप) - Step by step
                    5. Evaluation (मूल्याङ्कन)
                    6. Homework (गृहकार्य)
                    
                    Output strictly in Nepali Language.
                """.trimIndent()
                
                val response = generativeModel.generateContent(prompt)
                currentGeneratedPlan = response.text ?: "No content generated."
            } catch (e: Exception) {
                generationError = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun saveLessonPlan(plan: LessonPlan) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlan(plan)
        }
    }
    
    fun clearCurrentPlan() {
        currentGeneratedPlan = ""
    }

    // EXPORT FUNCTION (HTML -> DOC)
    // This allows Microsoft Word to open it with formatting and Unicode support
    fun exportToDocx(context: Context, plan: LessonPlan) {
        try {
            val fileName = "Lesson_${plan.topic.take(10).replace(" ", "_")}.doc"
            
            // Create rich content
            val content = """
                <html>
                <head><meta charset='UTF-8'></head>
                <body>
                    <h1 style='text-align:center;'>पाठ योजना (Lesson Plan)</h1>
                    <table border='1' width='100%' cellpadding='5'>
                        <tr><td><b>शिक्षकको नाम:</b> ${plan.teacherName}</td><td><b>प्रधानाध्यापक:</b> ${plan.principalName}</td></tr>
                        <tr><td><b>कक्षा:</b> ${plan.className}</td><td><b>मिति:</b> ${plan.date}</td></tr>
                        <tr><td><b>विषय:</b> ${plan.topic}</td><td><b>विद्यार्थी संख्या:</b> ${plan.totalStudents}</td></tr>
                    </table>
                    <hr>
                    <div style='font-family: Arial, sans-serif;'>
                        ${plan.generatedPlan.replace("\n", "<br>")}
                    </div>
                </body>
                </html>
            """.trimIndent()

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/msword")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            
            uri?.let {
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.write(content.toByteArray())
                outputStream?.close()
                Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(context, "Error creating file", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Export Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

// ==============================
// 3. UI LAYER
// ==============================

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LessonPlannerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonPlannerApp() {
    val viewModel: LessonViewModel = viewModel()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create") },
                    label = { Text("Create") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Saved") },
                    label = { Text("Saved") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) {
                CreatePlanScreen(viewModel)
            } else {
                SavedPlansScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(viewModel: LessonViewModel) {
    // Form Inputs
    var teacherName by remember { mutableStateOf("") }
    var principalName by remember { mutableStateOf("") }
    var totalStudents by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Nepali Lesson Planner", style = MaterialTheme.typography.headlineMedium)

        // Teacher Info Section
        Text("School Details", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Teacher Name") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = principalName, onValueChange = { principalName = it }, label = { Text("Principal Name") }, modifier = Modifier.weight(1f))
        }
        
        // Class Info Section
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = className, onValueChange = { className = it }, label = { Text("Class") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = totalStudents, onValueChange = { totalStudents = it }, label = { Text("Total Students") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }

        // Topic Section
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic (पाठको शीर्षक)") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period") }, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(
            value = description, 
            onValueChange = { description = it }, 
            label = { Text("Description/Notes (विवरण)") }, 
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 5
        )

        Button(
            onClick = { viewModel.generateLessonPlan(topic, className, teacherName, principalName, totalStudents, description) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading && topic.isNotEmpty()
        ) {
            Text(if (viewModel.isLoading) "Generating (Nepali)..." else "Generate Lesson Plan")
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            Text("AI is writing in Nepali...", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        }

        viewModel.generationError?.let {
            Text(text = it, color = Color.Red)
        }

        if (viewModel.currentGeneratedPlan.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Generated Plan:", fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(viewModel.currentGeneratedPlan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = {
                        val newPlan = LessonPlan(
                            topic = topic,
                            className = className,
                            teacherName = teacherName,
                            principalName = principalName,
                            totalStudents = totalStudents,
                            date = date,
                            period = period,
                            description = description,
                            generatedPlan = viewModel.currentGeneratedPlan
                        )
                        viewModel.saveLessonPlan(newPlan)
                        viewModel.clearCurrentPlan()
                    }) {
                        Text("Save to Database")
                    }
                }
            }
        }
    }
}

@Composable
fun SavedPlansScreen(viewModel: LessonViewModel) {
    val plans by viewModel.savedPlans.collectAsState(initial = emptyList())
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { 
            Text("Saved Plans", style = MaterialTheme.typography.headlineMedium) 
        }
        items(plans) { plan ->
            LessonPlanItem(plan, onExport = { viewModel.exportToDocx(context, plan) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonPlanItem(plan: LessonPlan, onExport: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = plan.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Class: ${plan.className} | Teacher: ${plan.teacherName}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.Share, contentDescription = "Export DOC")
                }
            }
            
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Principal: ${plan.principalName} | Students: ${plan.totalStudents}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = plan.generatedPlan, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = "Tap to view details...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
