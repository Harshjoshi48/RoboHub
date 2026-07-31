import re

# Update MainActivity.kt
with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_profile = """    composable(
      route = "profile/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      ProfileScreen(
        username = username,
        onLogout = {
          navController.navigate("login") {
            popUpTo(0)
          }
        },
        onNavigateToDashboard = {
          navController.navigate("dashboard/$username") {
            popUpTo(0)
          }
        }
      )
    }"""

new_profile = """    composable(
      route = "profile/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      ProfileScreen(
        username = username,
        onLogout = {
          navController.navigate("login") {
            popUpTo(0)
          }
        },
        onNavigateToDashboard = {
          navController.navigate("dashboard/$username") {
            popUpTo(0)
          }
        },
        onNavigateToSettings = {
          navController.navigate("settings")
        }
      )
    }
    
    composable("settings") {
      SettingsScreen(
        onBack = { navController.popBackStack() }
      )
    }"""

content = content.replace(old_profile, new_profile)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

# Update AndroidManifest.xml
with open('./app/src/main/AndroidManifest.xml', 'r') as f:
    manifest = f.read()

if "android.permission.ACCESS_NETWORK_STATE" not in manifest:
    manifest = manifest.replace("<application", '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <application')
    with open('./app/src/main/AndroidManifest.xml', 'w') as f:
        f.write(manifest)

