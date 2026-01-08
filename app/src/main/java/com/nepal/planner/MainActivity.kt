package com.nepal.lessonplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(vm: LessonViewModel = viewModel()) {
    val plans by vm.plans.collectAsState()
    var showForm by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("मेरो पाठ योजनाहरू", style = MaterialTheme.typography.headlineMedium)
            
            LazyColumn {
                items(plans) { plan ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text("कक्षा ${plan.className}: ${plan.lessonName}") },
                            supportingContent = { Text(plan.topic) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { /* Edit Logic */ }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { vm.deletePlan(plan.id) }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            }
                        )
                    }
                }
            }
        }
        if (showForm) {
            // Logic to show input form dialog
        }
    }
}
