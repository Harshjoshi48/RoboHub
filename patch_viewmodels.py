import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Add viewModel to MainActivity Navigation
if "val dashboardViewModel: DashboardViewModel = viewModel()" not in content:
    content = content.replace("val navController = rememberNavController()", "val navController = rememberNavController()\n  val dashboardViewModel: DashboardViewModel = viewModel()")

# Pass it to DashboardScreen
old_dash_call = """      DashboardScreen(
        username = username,"""
new_dash_call = """      DashboardScreen(
        username = username,
        viewModel = dashboardViewModel,"""
content = content.replace(old_dash_call, new_dash_call)

# Pass it to SettingsScreen
old_settings_call = """    composable("settings") {
      SettingsScreen(
        onBack = { navController.popBackStack() },"""
new_settings_call = """    composable("settings") {
      SettingsScreen(
        viewModel = dashboardViewModel,
        onBack = { navController.popBackStack() },"""
content = content.replace(old_settings_call, new_settings_call)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

