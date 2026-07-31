with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

old_dash_nav = """        onNavigateToSettings = {
          navController.navigate("settings")
        }
      )"""
new_dash_nav = """        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToReports = {
          navController.navigate("reports")
        }
      )"""
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

