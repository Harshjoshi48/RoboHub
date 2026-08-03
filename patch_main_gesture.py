with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

old_route = """    composable("gesture_studio") {
      GestureStudioScreen(
        onBack = { navController.popBackStack() }
      )
    }"""
new_route = """    composable("gesture_studio") {
      GestureStudioScreen(
        viewModel = dashboardViewModel,
        onBack = { navController.popBackStack() }
      )
    }"""
main = main.replace(old_route, new_route)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)
