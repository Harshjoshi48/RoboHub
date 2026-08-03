with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

old_dash = """        onNavigateToGestureStudio = {
          navController.navigate("gesture_studio")
        },
        onNavigateToAIAssistant = {
          navController.navigate("ai_assistant")
        }
      )
    }"""
new_dash = """        onNavigateToGestureStudio = {
          navController.navigate("gesture_studio")
        }
      )
    }"""
main = main.replace(old_dash, new_dash)

route = """    composable("ai_assistant") {
      AIAssistantScreen(
        viewModel = dashboardViewModel,
        onBack = { navController.popBackStack() }
      )
    }
    
    """
main = main.replace(route, "")

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)
