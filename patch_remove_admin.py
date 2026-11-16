import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

# Remove admin_dashboard composable
text = text.replace("""    composable("admin_dashboard") {
      AdminDashboardScreen(
        onLogout = {
          navController.navigate("login") {
            popUpTo(0)
          }
        }
      )
    }""", "")

# Remove onAdminLogin from LoginScreen call
text = text.replace("""      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onAdminLogin = {
          navController.navigate("admin_dashboard")
        },
        onSignUp = {""",
"""      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
        },
        onSignUp = {""")

# Remove onAdminLogin from LoginScreen signature
text = text.replace("""fun LoginScreen(
  onLogin: (String) -> Unit,
  onAdminLogin: () -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit
) {""",
"""fun LoginScreen(
  onLogin: (String) -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit
) {""")

# Update Login button logic to remove admin condition
text = text.replace("""      onClick = {
        if (username == "admin@gmail.com" && password == "password-admin") {
          onAdminLogin()
        } else if (username == "joshi" && password == "1234") {
          onLogin(username)
        } else {
          Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
      },""",
"""      onClick = {
        if (username == "joshi" && password == "1234") {
          onLogin(username)
        } else {
          Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
      },""")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)
