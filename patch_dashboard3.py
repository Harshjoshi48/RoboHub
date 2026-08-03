with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

old_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
new_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, onNavigateToAIAssistant: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
content = content.replace(old_sig, new_sig)

old_button = """                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, buttonBlue.copy(alpha = 0.3f))
                    ) {
                        Text("Engage AI Assistant", color = buttonBlue, fontWeight = FontWeight.Medium)
                    }"""
new_button = """                    OutlinedButton(
                        onClick = onNavigateToAIAssistant,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, buttonBlue.copy(alpha = 0.3f))
                    ) {
                        Text("Engage AI Assistant", color = buttonBlue, fontWeight = FontWeight.Medium)
                    }"""
content = content.replace(old_button, new_button)

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
