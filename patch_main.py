import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

# Update LoginScreen signature
text = text.replace(
"""fun LoginScreen(
  onLogin: (String) -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit
) {""",
"""fun LoginScreen(
  onLogin: (String) -> Unit,
  onAdminLogin: () -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit
) {""")

# Update LoginScreen call
text = text.replace(
"""      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onSignUp = {""",
"""      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onAdminLogin = {
          navController.navigate("admin_dashboard")
        },
        onSignUp = {""")

# Update Login button logic
text = text.replace(
"""      onClick = {
        if (username == "joshi" && password == "1234") {
          onLogin(username)
        } else {
          Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
      },""",
"""      onClick = {
        if (username == "admin@gmail.com" && password == "password-admin") {
          onAdminLogin()
        } else if (username == "joshi" && password == "1234") {
          onLogin(username)
        } else {
          Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
      },""")

# Add admin_dashboard composable right after the login composable block
text = text.replace(
"""    composable("login") {
      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onAdminLogin = {
          navController.navigate("admin_dashboard")
        },
        onSignUp = {
          navController.navigate("signup")
        },
        onForgotPassword = {
          navController.navigate("forgot_password")
        },
        onGoogleLogin = {
          navController.navigate("welcome/Google User")
        }
      )
    }""",
"""    composable("login") {
      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onAdminLogin = {
          navController.navigate("admin_dashboard")
        },
        onSignUp = {
          navController.navigate("signup")
        },
        onForgotPassword = {
          navController.navigate("forgot_password")
        },
        onGoogleLogin = {
          navController.navigate("welcome/Google User")
        }
      )
    }
    composable("admin_dashboard") {
      AdminDashboardScreen(
        onLogout = {
          navController.navigate("login") {
            popUpTo(0)
          }
        }
      )
    }""")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)
