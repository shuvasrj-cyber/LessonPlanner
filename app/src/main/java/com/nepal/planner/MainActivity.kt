@Composable
fun LessonApp(viewModel: LessonViewModel) {
    val plans by viewModel.plans.collectAsState()
    var editingPlan by remember { mutableStateOf<LessonPlan?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("मेरो पाठ योजनाहरू (Previous Plans)", style = MaterialTheme.typography.headlineMedium)
        
        // The History List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(plans) { plan ->
                LessonCard(
                    plan = plan,
                    onEdit = { editingPlan = it },
                    onDelete = { viewModel.deletePlan(it.id) }
                )
            }
        }

        // Generate Button
        Button(onClick = { /* Logic to trigger AI and save */ }) {
            Text("नयाँ योजना बनाउनुहोस्")
        }
    }

    // Edit Dialog
    if (editingPlan != null) {
        EditDialog(plan = editingPlan!!, onDismiss = { editingPlan = null }, onSave = { viewModel.updatePlan(it) })
    }
}
