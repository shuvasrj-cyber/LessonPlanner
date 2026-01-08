package com.example.lessonplanner

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ==============================
// 1. DATA LAYER (ROOM DATABASE)
// ==============================

@Entity(tableName = "lesson_plans")
data class LessonPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val className: String,
    val date: String,
    val period: String,
    val description: String,
    val generatedPlan: String // The AI Output
)

@Dao
interface LessonPlanDao {
    @Query("SELECT * FROM lesson_plans ORDER BY id DESC")
    fun getAllPlans(): Flow<List<LessonPlan>>

    @Insert
    suspend fun insertPlan(plan: LessonPlan)
}

@Database(entities = [LessonPlan::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonPlanDao(): LessonPlanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "lesson_db").build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ==============================
// 2. VIEWMODEL (LOGIC)
// ==============================

class LessonViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.lessonPlanDao()
    
    // REPLACE WITH YOUR ACTUAL API KEY
    private val apiKey = "gen-lang-client-0028939263"
    
    // Initialize Gemini Model
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey
    )

    val savedPlans: Flow<List<LessonPlan>> = dao.getAllPlans()
    
    var isLoading by mutableStateOf(false)
    var currentGeneratedPlan by mutableStateOf("")
    var generationError by mutableStateOf<String?>(null)

    fun generateLessonPlan(topic: String, className: String, desc: String) {
        viewModelScope.launch {
            isLoading = true
            generationError = null
            try {
                val prompt = "Create a concise lesson plan for a class named '$className'. " +
                        "The topic is '$topic'. Context/Description: $desc. " +
                        "Include Objectives, Materials, and a Step-by-Step guide."
                
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
}

// ==============================
// 3. UI LAYER (JETPACK COMPOSE)
// ==============================

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

@Composable
fun CreatePlanScreen(viewModel: LessonViewModel) {
    // Form State
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
        Text("New Lesson Plan", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(value = className, onValueChange = { className = it }, label = { Text("Class Name (e.g. Grade 5 Science)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period (e.g. 2nd Period)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = description, 
            onValueChange = { description = it }, 
            label = { Text("Description/Specific Needs") }, 
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 5
        )

        Button(
            onClick = { viewModel.generateLessonPlan(topic, className, description) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading && topic.isNotEmpty()
        ) {
            Text(if (viewModel.isLoading) "Generating..." else "Generate with AI")
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
        }

        viewModel.generationError?.let {
            Text(text = it, color = Color.Red)
        }

        if (viewModel.currentGeneratedPlan.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Generated Plan:", fontWeight = FontWeight.Bold)
                    Text(viewModel.currentGeneratedPlan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = {
                        val newPlan = LessonPlan(
                            topic = topic,
                            className = className,
                            date = date,
                            period = period,
                            description = description,
                            generatedPlan = viewModel.currentGeneratedPlan
                        )
                        viewModel.saveLessonPlan(newPlan)
                        viewModel.clearCurrentPlan()
                        // Optional: clear form fields here
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

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { 
            Text("Saved Plans", style = MaterialTheme.typography.headlineMedium) 
        }
        items(plans) { plan ->
            LessonPlanItem(plan)
        }
    }
}

@Composable
fun LessonPlanItem(plan: LessonPlan) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = plan.topic, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "${plan.className} | ${plan.date}", style = MaterialTheme.typography.bodySmall)
                }
                Text(text = plan.period, style = MaterialTheme.typography.labelLarge)
            }
            
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "AI Plan:", fontWeight = FontWeight.Bold)
                Text(text = plan.generatedPlan, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = "Tap to view details...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
