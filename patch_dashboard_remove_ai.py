with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

old_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, onNavigateToAIAssistant: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
new_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, onNavigateToGestureStudio: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
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
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = "AI Assistant",
                            tint = primaryBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = darkText
                            )
                            Text(
                                "I am here to optimize your experience.",
                                style = MaterialTheme.typography.bodySmall,
                                color = textGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onNavigateToAIAssistant,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryBlue),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text("Engage AI Assistant", fontWeight = FontWeight.Medium)
                    }
                }
            }"""

new_cards = """            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionCard(Icons.Outlined.PanTool, "Hand Control", Modifier.weight(1f), cardBg, darkText)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToReports)
                Spacer(modifier = Modifier.width(12.dp))
                QuickActionCard(Icons.Outlined.Gesture, "Gesture Studio", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToGestureStudio)
            }"""
content = content.replace(old_cards, new_cards)

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
