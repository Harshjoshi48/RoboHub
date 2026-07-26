package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          AppNavigation(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
  val navController = rememberNavController()
  val context = LocalContext.current

  NavHost(navController = navController, startDestination = "splash", modifier = modifier) {
    composable("splash") {
      SplashScreen(
        onSplashFinished = {
          navController.navigate("login") {
            popUpTo("splash") { inclusive = true }
          }
        }
      )
    }
    composable("login") {
      LoginScreen(
        onLogin = { username ->
          navController.navigate("welcome/$username")
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
    composable("signup") {
      SignUpScreen(
        onBack = { navController.popBackStack() },
        onSignUp = { email ->
          val safeEmail = if (email.isNotBlank()) email else "user@example.com"
          navController.navigate("otp/$safeEmail/welcome") 
        },
        onGoogleLogin = {
          navController.navigate("welcome/Google User") {
            popUpTo("login") { inclusive = true }
          }
        }
      )
    }
    composable("forgot_password") {
      ForgotPasswordScreen(
        onBack = { navController.popBackStack() },
        onRequestReset = { email ->
          val safeEmail = if (email.isNotBlank()) email else "user@example.com"
          navController.navigate("otp/$safeEmail/login")
        }
      )
    }
    composable(
      route = "otp/{email}/{next}",
      arguments = listOf(
        navArgument("email") { type = NavType.StringType },
        navArgument("next") { type = NavType.StringType }
      )
    ) { backStackEntry ->
      val email = backStackEntry.arguments?.getString("email") ?: "user@example.com"
      val next = backStackEntry.arguments?.getString("next") ?: "login"
      OtpScreen(
        email = email,
        onVerify = {
          if (next == "welcome") {
            navController.navigate("welcome/New User") {
              popUpTo("login") { inclusive = true }
            }
          } else {
            navController.navigate("login") {
              popUpTo("login") { inclusive = true }
            }
          }
        },
        onBack = { navController.popBackStack() }
      )
    }
    composable(
      route = "welcome/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      WelcomeScreen(
        username = username,
        onLogout = {
          navController.navigate("login") {
            popUpTo(0)
          }
        }
      )
    }
  }
}

@Composable
fun LoginScreen(
  onLogin: (String) -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var rememberMe by remember { mutableStateOf(true) }
  val context = LocalContext.current

  val lightBlueBg = Color(0xFFF0F8FF)
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(horizontal = 24.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(64.dp))
    
    // Logo placeholder (Using text to represent the logo shape)
    Text(
      text = "🧠\nNEUROGRIP",
      style = MaterialTheme.typography.titleMedium,
      color = primaryBlue,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 32.dp)
    )
    
    Text(
      text = "HAND CONNECT",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 2.sp,
      fontWeight = FontWeight.Bold
    )
    
    Text(
      text = "Login",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
    
    Text(
      text = "Welcome back. Sign in to continue controlling\nyour NeuroGrip device.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    TextField(
      value = username,
      onValueChange = { username = it },
      placeholder = { Text("Enter your username", color = textGray) },
      leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = textGray) },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = lightBlueBg,
        unfocusedContainerColor = lightBlueBg,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )
    
    Spacer(modifier = Modifier.height(16.dp))

    TextField(
      value = password,
      onValueChange = { password = it },
      placeholder = { Text("Enter your password", color = textGray) },
      leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = textGray) },
      trailingIcon = {
        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = textGray)
        }
      },
      visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = lightBlueBg,
        unfocusedContainerColor = lightBlueBg,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))
    
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp)
      ) {
        Checkbox(
          checked = rememberMe,
          onCheckedChange = { rememberMe = it },
          colors = CheckboxDefaults.colors(checkedColor = primaryBlue, uncheckedColor = textGray),
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Remember Me", style = MaterialTheme.typography.bodySmall, color = textGray)
      }
      TextButton(onClick = onForgotPassword) {
        Text("Forgot Password?", style = MaterialTheme.typography.bodySmall, color = primaryBlue)
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        if (username == "joshi" && password == "1234") {
          onLogin(username)
        } else {
          Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT).show()
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("LOGIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
      onClick = onSignUp,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, primaryBlue)
    ) {
      Text("SIGN UP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryBlue)
    }

    Spacer(modifier = Modifier.height(32.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
      Text("OR", style = MaterialTheme.typography.labelSmall, color = textGray, modifier = Modifier.padding(horizontal = 16.dp))
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
    }

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
      onClick = onGoogleLogin,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_google_logo),
        contentDescription = "Google Logo",
        modifier = Modifier.size(24.dp),
        tint = Color.Unspecified
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text("Continue with Google", color = darkText, fontWeight = FontWeight.Medium)
    }

    Spacer(modifier = Modifier.height(48.dp))

    Text(
      text = "By signing in, you agree to our\nTerms of Service and Privacy Policy",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
      text = "© 2024 NeuroPulse Surgical Systems. All rights reserved.",
      style = MaterialTheme.typography.labelSmall,
      color = Color(0xFFBDBDBD),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )
  }
}

@Composable
fun WelcomeScreen(username: String, onLogout: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Welcome, $username!",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 16.dp)
    )
    Text(
      text = "You are now connected to the robotic system.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 48.dp)
    )

    Button(
      onClick = onLogout,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = MaterialTheme.shapes.large,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
      )
    ) {
      Text("LOGOUT", style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, onSignUp: (String) -> Unit, onGoogleLogin: () -> Unit) {
  var fullName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }

  val lightBlueBg = Color(0xFFF0F8FF)
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(horizontal = 24.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(32.dp))
    
    Text(
      text = "🧠",
      fontSize = 40.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Text(
      text = "NEUROGRIP",
      style = MaterialTheme.typography.titleMedium,
      color = primaryBlue,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
    )
    
    Text(
      text = "HAND CONNECT",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 2.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 24.dp)
    )
    
    Text(
      text = "Sign Up",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Text(
      text = "Join NeuroGrip to connect and control\nyour robotic hand.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    TextField(
      value = fullName,
      onValueChange = { fullName = it },
      placeholder = { Text("Enter your full name", color = textGray) },
      leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = textGray) },
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      shape = RoundedCornerShape(12.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedIndicatorColor = primaryBlue,
        unfocusedIndicatorColor = Color(0xFFE0E0E0),
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )

    TextField(
      value = email,
      onValueChange = { email = it },
      placeholder = { Text("Enter your email address", color = textGray) },
      leadingIcon = { Icon(painter = painterResource(id = android.R.drawable.ic_dialog_email), contentDescription = null, tint = textGray) }, // Using standard android icon as fallback, better to use Compose Material Icons if available
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      shape = RoundedCornerShape(12.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedIndicatorColor = primaryBlue,
        unfocusedIndicatorColor = Color(0xFFE0E0E0),
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )

    TextField(
      value = password,
      onValueChange = { password = it },
      placeholder = { Text("Create a password", color = textGray) },
      leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = textGray) },
      trailingIcon = {
        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = textGray)
        }
      },
      visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      shape = RoundedCornerShape(12.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedIndicatorColor = primaryBlue,
        unfocusedIndicatorColor = Color(0xFFE0E0E0),
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )

    TextField(
      value = confirmPassword,
      onValueChange = { confirmPassword = it },
      placeholder = { Text("Confirm your password", color = textGray) },
      leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = textGray) },
      trailingIcon = {
        val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
            Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = textGray)
        }
      },
      visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      shape = RoundedCornerShape(12.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedIndicatorColor = primaryBlue,
        unfocusedIndicatorColor = Color(0xFFE0E0E0),
        unfocusedTextColor = darkText,
        focusedTextColor = darkText
      ),
      singleLine = true
    )

    Button(
      onClick = { onSignUp(email) },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .padding(bottom = 8.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("SIGN UP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }

    OutlinedButton(
      onClick = onBack,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, primaryBlue)
    ) {
      Text("BACK TO LOGIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryBlue)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
      Text("OR", style = MaterialTheme.typography.labelSmall, color = textGray, modifier = Modifier.padding(horizontal = 16.dp))
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
      onClick = onGoogleLogin,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_google_logo),
        contentDescription = "Google Logo",
        modifier = Modifier.size(24.dp),
        tint = Color.Unspecified
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text("Continue with Google", color = darkText, fontWeight = FontWeight.Medium)
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
      text = "By signing up, you agree to our Terms of Service and\nPrivacy Policy",
      style = MaterialTheme.typography.labelSmall,
      color = darkText,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 32.dp)
    )
  }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onRequestReset: (String) -> Unit) {
  var email by remember { mutableStateOf("") }
  
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(horizontal = 24.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(64.dp))
    
    Text(
      text = "🧠",
      fontSize = 40.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Text(
      text = "NEUROGRIP",
      style = MaterialTheme.typography.titleMedium,
      color = darkText,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
    )
    
    Text(
      text = "HAND CONNECT",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 2.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 32.dp)
    )
    
    Text(
      text = "Reset Your Password",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Email Address",
            style = MaterialTheme.typography.bodyMedium,
            color = darkText,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        TextField(
          value = email,
          onValueChange = { email = it },
          placeholder = { Text("name@example.com", color = textGray) },
          leadingIcon = { Icon(painter = painterResource(id = android.R.drawable.ic_dialog_email), contentDescription = null, tint = textGray) },
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
          shape = RoundedCornerShape(12.dp),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = primaryBlue,
            unfocusedIndicatorColor = Color(0xFFE0E0E0),
            unfocusedTextColor = darkText,
            focusedTextColor = darkText
          ),
          singleLine = true
        )
    }

    Button(
      onClick = { onRequestReset(email) },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .padding(bottom = 8.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("Request Reset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = Color.White)
    }

    OutlinedButton(
      onClick = onBack,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFF336699)) // Slightly darker border as seen in screenshot
    ) {
      Text("Go back to Login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = Color(0xFF336699))
    }
  }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
  val alpha = remember { Animatable(0f) }
  val scale = remember { Animatable(0.5f) }

  LaunchedEffect(key1 = true) {
    alpha.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    scale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    delay(2000) // Wait for 2 seconds (so total time is ~3s)
    onSplashFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .alpha(alpha.value)
        .scale(scale.value)
    ) {
      Text(
        text = "🧠",
        fontSize = 100.sp,
        modifier = Modifier.padding(bottom = 16.dp)
      )
      Text(
        text = "NEUROGRIP",
        style = MaterialTheme.typography.headlineLarge,
        color = Color(0xFF003366),
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp
      )
      Text(
        text = "HAND CONNECT",
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF666666),
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun OtpScreen(email: String, onVerify: () -> Unit, onBack: () -> Unit) {
  var otpValue by remember { mutableStateOf("") }
  val context = LocalContext.current

  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(horizontal = 24.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(64.dp))
    
    Text(
      text = "🧠",
      fontSize = 40.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Text(
      text = "NEUROGRIP",
      style = MaterialTheme.typography.titleMedium,
      color = darkText,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
    )
    
    Text(
      text = "HAND CONNECT",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 2.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 32.dp)
    )
    
    Text(
      text = "Verify Your Email",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
      text = "Please enter the 6-digit verification code sent\nto your registered email.",
      style = MaterialTheme.typography.bodyMedium,
      color = darkText,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(bottom = 32.dp)
    ) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = textGray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = email, color = darkText, style = MaterialTheme.typography.bodyMedium)
    }

    Text(
        text = "Enter the code",
        style = MaterialTheme.typography.labelSmall,
        color = textGray,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    androidx.compose.foundation.text.BasicTextField(
        value = otpValue,
        onValueChange = { if (it.length <= 6) otpValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        singleLine = true,
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(6) { index ->
                    val char = when {
                        index >= otpValue.length -> ""
                        else -> otpValue[index].toString()
                    }
                    val isFocused = otpValue.length == index
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(56.dp)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) primaryBlue else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = char, style = MaterialTheme.typography.titleLarge, color = darkText)
                    }
                }
            }
        }
    )

    Button(
      onClick = {
        if (otpValue == "000000") {
            onVerify()
        } else {
            Toast.makeText(context, "Invalid OTP. Default is 000000", Toast.LENGTH_SHORT).show()
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
        .height(52.dp),
      shape = RoundedCornerShape(8.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("VERIFY CODE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }

    Text(
      text = "Didn't receive the code? Resend Code\nResend in 30s",
      style = MaterialTheme.typography.bodySmall,
      color = darkText,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    TextButton(onClick = onBack) {
      Text("Back", style = MaterialTheme.typography.bodyMedium, color = darkText, fontWeight = FontWeight.Bold)
    }
  }
}
