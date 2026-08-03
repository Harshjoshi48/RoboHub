with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

old_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
new_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, onNavigateToAIAssistant: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
content = content.replace(old_sig, new_sig)

old_cards = """            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionCard(Icons.Outlined.PanTool, "Hand Control", Modifier.weight(1f), cardBg, darkText)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToReports)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.Gesture, "Gesture Studio", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToGestureStudio)
            }"""

new_cards = """            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionCard(Icons.Outlined.PanTool, "Hand Control", Modifier.weight(1f), cardBg, darkText)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToReports)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionCard(Icons.Outlined.Gesture, "Gesture Studio", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToGestureStudio)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(androidx.compose.material.icons.Icons.Outlined.SmartToy, "AI Assistant", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToAIAssistant)
            }"""
content = content.replace(old_cards, new_cards)

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
