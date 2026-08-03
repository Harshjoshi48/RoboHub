with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

old_dash = """        onNavigateToGestureStudio = {
          navController.navigate("gesture_studio")
        }
      )
    }"""
new_dash = """        onNavigateToGestureStudio = {
          navController.navigate("gesture_studio")
        },
        onNavigateToAIAssistant = {
          navController.navigate("ai_assistant")
        }
      )
    }"""
main = main.replace(old_dash, new_dash)

route = """    composable("ai_assistant") {
      AIAssistantScreen(
        viewModel = dashboardViewModel,
        onBack = { navController.popBackStack() }
      )
    }"""

if 'composable("ai_assistant")' not in main:
    main = main.replace('composable("gesture_studio") {', route + '\n    \n    composable("gesture_studio") {')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)
