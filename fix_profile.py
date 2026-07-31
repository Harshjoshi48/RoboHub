with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

bad_profile = """        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToReports = {
          navController.navigate("reports")
        }
      )
    }"""
good_profile = """        onNavigateToSettings = {
          navController.navigate("settings")
        }
      )
    }"""
if main.count(bad_profile) > 0:
    # replace the second occurrence only (in profile screen)
    parts = main.split(bad_profile)
    main = bad_profile.join(parts[:-1]) + good_profile + parts[-1]

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)

