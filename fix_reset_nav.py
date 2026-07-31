with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main = f.read()

# Update ForgotPasswordScreen navigation target
old_forgot = """    composable("forgot_password") {
      ForgotPasswordScreen(
        onBack = { navController.popBackStack() },
        onRequestReset = { email ->
          val safeEmail = if (email.isNotBlank()) email else "user@example.com"
          navController.navigate("otp/$safeEmail/login")
        }
      )
    }"""
new_forgot = """    composable("forgot_password") {
      ForgotPasswordScreen(
        onBack = { navController.popBackStack() },
        onRequestReset = { email ->
          val safeEmail = if (email.isNotBlank()) email else "user@example.com"
          navController.navigate("otp/$safeEmail/reset")
        }
      )
    }"""
main = main.replace(old_forgot, new_forgot)

# Update OTP Screen to handle 'reset'
old_otp = """        onVerify = {
          if (next == "welcome") {
            navController.navigate("welcome/New User") {
              popUpTo("login") { inclusive = true }
            }
          } else {
            navController.navigate("login") {
              popUpTo("login") { inclusive = true }
            }
          }
        },"""
new_otp = """        onVerify = {
          if (next == "welcome") {
            navController.navigate("welcome/New User") {
              popUpTo("login") { inclusive = true }
            }
          } else if (next == "reset") {
            navController.navigate("reset_password") {
              popUpTo("login") { inclusive = false }
            }
          } else {
            navController.navigate("login") {
              popUpTo("login") { inclusive = true }
            }
          }
        },"""
main = main.replace(old_otp, new_otp)

# Add ResetPasswordScreen route
reset_route = """    composable("reset_password") {
      ResetPasswordScreen(
        onResetSuccess = {
          navController.navigate("login") {
            popUpTo("login") { inclusive = true }
          }
        },
        onBack = { navController.popBackStack() }
      )
    }"""
if "composable(\"reset_password\")" not in main:
    main = main.replace("composable(\"forgot_password\") {", reset_route + "\n    \n    composable(\"forgot_password\") {")

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main)

