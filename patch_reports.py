import re

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

old_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
new_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, onNavigateToNotifications: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToSettings: () -> Unit = {}, onNavigateToReports: () -> Unit = {}, viewModel: DashboardViewModel = viewModel()) {"
content = content.replace(old_sig, new_sig)

old_card_def = "fun QuickActionCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, cardBg: Color, darkText: Color) {"
new_card_def = "fun QuickActionCard(icon: ImageVector, label: String, modifier: Modifier = Modifier, cardBg: Color, darkText: Color, onClick: () -> Unit = {}) {"
content = content.replace(old_card_def, new_card_def)

old_card_body = """    Card(
        modifier = modifier.height(100.dp),"""
new_card_body = """    Card(
        modifier = modifier.height(100.dp).clickable { onClick() },"""
content = content.replace(old_card_body, new_card_body)

old_reports_call = """QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText)"""
new_reports_call = """QuickActionCard(Icons.Outlined.BarChart, "Reports", Modifier.weight(1f), cardBg, darkText, onClick = onNavigateToReports)"""
content = content.replace(old_reports_call, new_reports_call)

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

old_dash_nav = """        onNavigateToSettings = {
          navController.navigate("settings")
        },
        viewModel = dashboardViewModel,"""
new_dash_nav = """        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToReports = {
          navController.navigate("reports")
        },
        viewModel = dashboardViewModel,"""
main = main.replace(old_dash_nav, new_dash_nav)

reports_route = """    composable("reports") {
      ReportsScreen(
        onBack = { navController.popBackStack() }
      )
    }"""
if "composable(\"reports\")" not in main:
    main = main.replace("composable(\"settings\") {", reports_route + "\n    \n    composable(\"settings\") {")

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)

