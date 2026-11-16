package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
          AppNavigation(modifier = Modifier.padding(innerPadding).imePadding())
        }
      }
    }
  }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
  val navController = rememberNavController()
  val dashboardViewModel: DashboardViewModel = viewModel()
  val adminViewModel: AdminViewModel = viewModel()
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current

  val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()
  val isAuthorizedAdmin by adminViewModel.isAuthorizedAdminSession.collectAsStateWithLifecycle()
  val showFloatingAdminReturn by adminViewModel.showFloatingAdminReturnButton.collectAsStateWithLifecycle()
  val isAdminPreviewMode by adminViewModel.isAdminPreviewMode.collectAsStateWithLifecycle()
  val activeUsername = currentSession?.username?.ifBlank { "User" } ?: "User"

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  LaunchedEffect(currentRoute, currentSession) {
    val valid = adminViewModel.validateActiveAdminSessionAndRevokeIfExpired()
    if (!valid && currentRoute == "admin_panel") {
      navController.navigate("login") {
        popUpTo(0)
      }
    }
  }

  LaunchedEffect(isAdminPreviewMode, currentSession) {
    if (isAdminPreviewMode) {
      while (true) {
        delay(5_000L)
        if (!adminViewModel.validateActiveAdminSessionAndRevokeIfExpired()) {
          Toast.makeText(context, "Admin session expired. Please sign in again.", Toast.LENGTH_SHORT).show()
          navController.navigate("login") {
            popUpTo(0)
          }
          break
        }
      }
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val valid = adminViewModel.validateActiveAdminSessionAndRevokeIfExpired()
        if (!valid && currentRoute == "admin_panel") {
          navController.navigate("login") {
            popUpTo(0)
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
  NavHost(navController = navController, startDestination = "splash", modifier = Modifier.fillMaxSize()) {
    composable("splash") {
      SplashScreen(
        onSplashFinished = {
          val rememberedSession = SecureSessionManager.loadSession(context)
          val rememberEnabled = SecureSessionManager.isRememberMeEnabled(context)
          if (rememberedSession != null && rememberEnabled) {
            val sessionRole = AdminRole.fromString(rememberedSession.role)
            if (SecureSessionManager.isAuthorizedAdminRole(sessionRole) &&
              adminViewModel.validateActiveAdminSessionAndRevokeIfExpired()
            ) {
              navController.navigate("admin_panel") {
                popUpTo("splash") { inclusive = true }
              }
            } else {
              val uname = rememberedSession.username.ifBlank { "User" }
              navController.navigate("dashboard/$uname") {
                popUpTo("splash") { inclusive = true }
              }
            }
          } else {
            navController.navigate("login") {
              popUpTo("splash") { inclusive = true }
            }
          }
        }
      )
    }
    composable("login") {
      LoginScreen(
        adminViewModel = adminViewModel,
        onOtpRequired = { targetEmail, isAdminMode ->
          val safeEmail = targetEmail.trim().ifBlank { "hj195344@gmail.com" }
          val nextStep = if (isAdminMode) "admin_login" else "user_login"
          navController.navigate("otp/$safeEmail/$nextStep")
        },
        onSignUp = {
          navController.navigate("signup")
        },
        onForgotPassword = {
          navController.navigate("forgot_password")
        },
        onGoogleLogin = {
          if (adminViewModel.isGoogleSignInInProgress.value) return@LoginScreen
          coroutineScope.launch {
            adminViewModel.setGoogleSignInInProgress(true)
            try {
              val idToken = requestGoogleIdTokenViaCredentialManager(context)
              adminViewModel.signInWithGoogleIdToken(
                idToken = idToken,
                rememberMe = SecureSessionManager.isRememberMeEnabled(context),
                onSuccess = { username ->
                  navController.navigate("welcome/$username") {
                    popUpTo("login") { inclusive = true }
                  }
                },
                onError = { err ->
                  Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                },
                onOutcome = { outcome ->
                  if (outcome.isAdminPortalEligible) {
                    navController.navigate("admin_panel") {
                      popUpTo("login") { inclusive = true }
                    }
                  } else {
                    navController.navigate("welcome/${outcome.user.username}") {
                      popUpTo("login") { inclusive = true }
                    }
                  }
                }
              )
            } catch (e: Exception) {
              adminViewModel.setGoogleSignInInProgress(false)
              Toast.makeText(context, "Google Sign-In cancelled or unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      )
    }

    composable("admin_panel") {
      AdminPanelScreen(
        adminViewModel = adminViewModel,
        onNavigateToUserAppPreview = { username ->
          if (adminViewModel.enterUserAppPreviewMode()) {
            navController.navigate("dashboard/$username") {
              launchSingleTop = true
            }
          } else {
            Toast.makeText(context, "Admin session expired or unauthorized.", Toast.LENGTH_SHORT).show()
            navController.navigate("login") {
              popUpTo(0)
            }
          }
        },
        onLogout = {
          adminViewModel.logoutAdmin()
          navController.navigate("login") {
            popUpTo(0)
          }
        }
      )
    }

    composable("signup") {
      SignUpScreen(
        adminViewModel = adminViewModel,
        onBack = { navController.popBackStack() },
        onSignUp = { email ->
          val safeEmail = if (email.isNotBlank()) email.trim() else "user@example.com"
          navController.navigate("otp/$safeEmail/welcome")
        },
        onGoogleLogin = {
          if (adminViewModel.isGoogleSignInInProgress.value) return@SignUpScreen
          coroutineScope.launch {
            adminViewModel.setGoogleSignInInProgress(true)
            try {
              val idToken = requestGoogleIdTokenViaCredentialManager(context)
              adminViewModel.signInWithGoogleIdToken(
                idToken = idToken,
                rememberMe = SecureSessionManager.isRememberMeEnabled(context),
                onSuccess = { username ->
                  navController.navigate("welcome/$username") {
                    popUpTo("login") { inclusive = true }
                  }
                },
                onError = { err ->
                  Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                },
                onOutcome = { outcome ->
                  if (outcome.isAdminPortalEligible) {
                    navController.navigate("admin_panel") {
                      popUpTo("login") { inclusive = true }
                    }
                  } else {
                    navController.navigate("welcome/${outcome.user.username}") {
                      popUpTo("login") { inclusive = true }
                    }
                  }
                }
              )
            } catch (e: Exception) {
              adminViewModel.setGoogleSignInInProgress(false)
              Toast.makeText(context, "Google Sign-In cancelled or unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      )
    }

    composable(
      route = "reset_password/{email}",
      arguments = listOf(navArgument("email") { type = NavType.StringType })
    ) { backStackEntry ->
      val emailArg = backStackEntry.arguments?.getString("email") ?: "hj195345@gmail.com"
      ResetPasswordScreen(
        email = emailArg,
        adminViewModel = adminViewModel,
        onResetSuccess = {
          navController.navigate("login") {
            popUpTo("login") { inclusive = true }
          }
        },
        onBack = { navController.popBackStack() }
      )
    }

    composable("reset_password") {
      ResetPasswordScreen(
        email = "hj195345@gmail.com",
        adminViewModel = adminViewModel,
        onResetSuccess = {
          navController.navigate("login") {
            popUpTo("login") { inclusive = true }
          }
        },
        onBack = { navController.popBackStack() }
      )
    }
    
    composable("forgot_password") {
      ForgotPasswordScreen(
        adminViewModel = adminViewModel,
        onBack = { navController.popBackStack() },
        onRequestReset = { email ->
          val safeEmail = if (email.isNotBlank()) email.trim() else "hj195345@gmail.com"
          navController.navigate("otp/$safeEmail/reset")
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
      val next = backStackEntry.arguments?.getString("next") ?: "user_login"
      OtpScreen(
        email = email,
        next = next,
        adminViewModel = adminViewModel,
        onVerifiedAdmin = {
          navController.navigate("admin_panel") {
            popUpTo("login") { inclusive = true }
          }
        },
        onVerifiedUser = { username ->
          navController.navigate("welcome/$username") {
            popUpTo("login") { inclusive = true }
          }
        },
        onVerifiedReset = { targetEmail ->
          navController.navigate("reset_password/$targetEmail") {
            popUpTo("login") { inclusive = false }
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
        onSkip = {
          navController.navigate("dashboard/$username") {
            popUpTo("welcome/$username") { inclusive = true }
          }
        },
        onStartSetup = {
          navController.navigate("permissions/$username")
        }
      )
    }

    composable(
      route = "dashboard/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      DashboardScreen(
        username = username,
        viewModel = dashboardViewModel,
        onLogout = {
          adminViewModel.logoutAdmin()
          navController.navigate("login") {
            popUpTo(0)
          }
        },
        onNavigateToNotifications = {
          navController.navigate("notifications")
        },
        onNavigateToProfile = {
          navController.navigate("profile/$username") {
            launchSingleTop = true
          }
        },
        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToReports = {
          navController.navigate("reports") {
            launchSingleTop = true
          }
        },
        onNavigateToGestureStudio = {
          navController.navigate("gesture_studio")
        },
        onNavigateToAIAssistant = {
          navController.navigate("ai_assistant") {
            launchSingleTop = true
          }
        },
        onNavigateToPermissions = {
          navController.navigate("permissions/$username")
        },
        onNavigateToHandControl = {
          navController.navigate("hand_control") {
            launchSingleTop = true
          }
        },
        onNavigateToConnectDevices = {
          navController.navigate("connect_devices/$username")
        },
        onNavigateToAdminPanel = {
          if (adminViewModel.returnToAdminPanel()) {
            val popped = navController.popBackStack("admin_panel", inclusive = false)
            if (!popped) {
              navController.navigate("admin_panel") {
                launchSingleTop = true
              }
            }
          } else {
            navController.navigate("login") {
              popUpTo(0)
            }
          }
        },
        adminViewModel = adminViewModel
      )
    }
    
    composable(
      route = "profile/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      ProfileScreen(
        username = username,
        onLogout = {
          adminViewModel.logoutAdmin()
          navController.navigate("login") {
            popUpTo(0)
          }
        },
        onNavigateToDashboard = {
          navController.navigate("dashboard/$username") {
            launchSingleTop = true
            if (adminViewModel.isAdminPreviewMode.value) {
              popUpTo("admin_panel") { inclusive = false }
            } else {
              popUpTo(0)
            }
          }
        },
        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToHandControl = {
          navController.navigate("hand_control") {
            launchSingleTop = true
          }
        },
        onNavigateToAIAssistant = {
          navController.navigate("ai_assistant") {
            launchSingleTop = true
          }
        },
        onNavigateToReports = {
          navController.navigate("reports") {
            launchSingleTop = true
          }
        },
        adminViewModel = adminViewModel
      )
    }
    
            
    
    composable("hand_control") {
      HandControlScreen(
        onBack = { navController.popBackStack() },
        onNavigateToHome = { navController.navigate("dashboard/$activeUsername") { launchSingleTop = true } },
        onNavigateToAI = { navController.navigate("ai_assistant") { launchSingleTop = true } },
        onNavigateToReports = { navController.navigate("reports") { launchSingleTop = true } },
        onNavigateToProfile = { navController.navigate("profile/$activeUsername") { launchSingleTop = true } },
        viewModel = dashboardViewModel,
        adminViewModel = adminViewModel
      )
    }

    composable("ai_assistant") {
      AIAssistantScreen(
        viewModel = dashboardViewModel,
        onBack = { navController.popBackStack() },
        onNavigateToHome = { navController.navigate("dashboard/$activeUsername") { launchSingleTop = true } },
        onNavigateToControl = { navController.navigate("hand_control") { launchSingleTop = true } },
        onNavigateToReports = { navController.navigate("reports") { launchSingleTop = true } },
        onNavigateToProfile = { navController.navigate("profile/$activeUsername") { launchSingleTop = true } }
      )
    }
    
    composable("gesture_studio") {
      GestureStudioScreen(
        viewModel = dashboardViewModel,
        adminViewModel = adminViewModel,
        onBack = { navController.popBackStack() }
      )
    }
    
    composable("reports") {
      ReportsScreen(
        onBack = { navController.popBackStack() },
        onNavigateToHome = { navController.navigate("dashboard/$activeUsername") { launchSingleTop = true } },
        onNavigateToControl = { navController.navigate("hand_control") { launchSingleTop = true } },
        onNavigateToAI = { navController.navigate("ai_assistant") { launchSingleTop = true } },
        onNavigateToProfile = { navController.navigate("profile/$activeUsername") { launchSingleTop = true } },
        dashboardViewModel = dashboardViewModel,
        adminViewModel = adminViewModel
      )
    }
    
    composable("settings") {
      val currentUser = "User"
      SettingsScreen(
        viewModel = dashboardViewModel,
        adminViewModel = adminViewModel,
        onBack = { navController.popBackStack() },
        onLogout = {
          adminViewModel.logoutAdmin()
          navController.navigate("login") {
            popUpTo(0)
          }
        },
        onNavigateToPermissions = {
          navController.navigate("permissions/$currentUser")
        },
        onNavigateToConnectDevices = {
          navController.navigate("connect_devices/$currentUser")
        }
      )
    }
    
    composable("notifications") {
      NotificationsScreen(
        onBack = { navController.popBackStack() }
      )
    }

    composable(
      route = "permissions/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      PermissionsScreen(
        onBack = { navController.popBackStack() },
        onContinue = {
          navController.navigate("connect_devices/$username")
        }
      )
    }

    composable(
      route = "connect_devices/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      ConnectDevicesScreen(
        onBack = { navController.popBackStack() },
        onContinue = {
          navController.navigate("hand_test/$username")
        },
        dashboardViewModel = dashboardViewModel
      )
    }

    composable(
      route = "hand_test/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      RoboticHandTestScreen(
        onBack = { navController.popBackStack() },
        onComplete = {
          navController.navigate("setup_complete/$username")
        }
      )
    }

    composable(
      route = "setup_complete/{username}",
      arguments = listOf(navArgument("username") { type = NavType.StringType })
    ) { backStackEntry ->
      val username = backStackEntry.arguments?.getString("username") ?: "User"
      SetupCompleteScreen(
        onGoToDashboard = {
          navController.navigate("dashboard/$username") {
            popUpTo(0)
          }
        },
        onViewTutorial = {
          navController.navigate("welcome/$username") {
            popUpTo(0)
          }
        }
      )
    }
  }

  val isUserAppScreen = currentRoute in USER_APP_PREVIEW_ROUTES
  FloatingAdminReturnButton(
    visible = showFloatingAdminReturn && isAuthorizedAdmin && isUserAppScreen,
    onClick = {
      if (adminViewModel.returnToAdminPanel()) {
        val popped = navController.popBackStack("admin_panel", inclusive = false)
        if (!popped) {
          navController.navigate("admin_panel") {
            popUpTo("dashboard/$activeUsername") { inclusive = true }
            launchSingleTop = true
          }
        }
      } else {
        Toast.makeText(
          context,
          "Admin session expired or unauthorized. Please sign in again.",
          Toast.LENGTH_SHORT
        ).show()
        navController.navigate("login") {
          popUpTo(0)
        }
      }
    },
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .padding(
        end = 14.dp,
        bottom = if (currentRoute == "ai_assistant") 166.dp else 94.dp
      )
  )
  }
}

@Composable
fun LoginScreen(
  onOtpRequired: (String, Boolean) -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
  onGoogleLogin: () -> Unit,
  adminViewModel: AdminViewModel = viewModel()
) {
  val context = LocalContext.current
  var username by remember {
    mutableStateOf(SecureSessionManager.getRememberedIdentifier(context))
  }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var rememberMe by remember {
    mutableStateOf(SecureSessionManager.isRememberMeEnabled(context))
  }
  var loginErrorText by remember { mutableStateOf<String?>(null) }
  val isLoading by adminViewModel.isLoading.collectAsState()
  val isGoogleSigningIn by adminViewModel.isGoogleSignInInProgress.collectAsState()

  val lightBlueBg = Color(0xFFF0F8FF)
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter
  ) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    val loginBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()

    if (loginBranding.hasCustomLogo) {
      AppBrandingLogo(
        branding = loginBranding,
        size = 56.dp,
        cornerRadius = 14.dp,
        modifier = Modifier.padding(bottom = 8.dp),
        testTag = "login_branding_logo"
      )
    } else {
      Text(
        text = "🧠",
        fontSize = 36.sp,
        modifier = Modifier.padding(bottom = 4.dp)
      )
    }
    Text(
      text = loginBranding.appName.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = primaryBlue,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .padding(bottom = 16.dp)
        .testTag("login_branding_app_name")
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
      modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
    
    Text(
      text = "Sign in with your account or Admin credentials. A 6-digit OTP will be sent to your email.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    if (loginErrorText != null) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 14.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
      ) {
        Text(
          text = loginErrorText!!,
          color = Color(0xFFB91C1C),
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(12.dp)
        )
      }
    }

    TextField(
      value = username,
      onValueChange = {
        username = it
        loginErrorText = null
      },
      placeholder = {
        Text(
          "Enter your email address or username",
          color = textGray
        )
      },
      leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = "Email input", tint = textGray) },
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
      onValueChange = {
        password = it
        loginErrorText = null
      },
      placeholder = {
        Text(
          "Enter your password",
          color = textGray
        )
      },
      leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = "Password input", tint = textGray) },
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
        modifier = Modifier
          .heightIn(min = 48.dp)
          .clickable {
            rememberMe = !rememberMe
            SecureSessionManager.setRememberMePreference(context, rememberMe, username)
          }
          .padding(horizontal = 4.dp)
      ) {
        Checkbox(
          checked = rememberMe,
          onCheckedChange = null,
          colors = CheckboxDefaults.colors(checkedColor = primaryBlue, uncheckedColor = textGray),
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Remember Me (30 Days)", style = MaterialTheme.typography.bodySmall, color = textGray)
      }
      TextButton(
        onClick = onForgotPassword,
        modifier = Modifier
          .weight(1f, fill = false)
          .heightIn(min = 48.dp)
      ) {
        Text(
          "Forgot Password?",
          style = MaterialTheme.typography.bodySmall,
          color = primaryBlue,
          textAlign = TextAlign.End
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        loginErrorText = null
        SecureSessionManager.setRememberMePreference(context, rememberMe, username)
        adminViewModel.initiateEmailOtpLogin(
          identifier = username,
          password = password,
          requireAdmin = false,
          onChallengeIssued = { challenge ->
            val isAdminAccount = challenge.role != AdminRole.USER.name &&
              challenge.role != AdminRole.BLOCKED.name
            Toast.makeText(
              context,
              "OTP sent to your registered email (${challenge.maskedEmail}).",
              Toast.LENGTH_SHORT
            ).show()
            onOtpRequired(challenge.email, isAdminAccount)
          },
          onError = { errMsg ->
            loginErrorText = errMsg
            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
          }
        )
      },
      enabled = !isLoading && !isGoogleSigningIn,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      if (isLoading && !isGoogleSigningIn) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          color = Color.White,
          strokeWidth = 2.dp
        )
      } else {
        Text(
          "LOGIN",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
      onClick = onSignUp,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, primaryBlue)
    ) {
      Text(
        "SIGN UP",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = primaryBlue,
        textAlign = TextAlign.Center
      )
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
      onClick = {
        if (!isGoogleSigningIn && !isLoading) {
          SecureSessionManager.setRememberMePreference(context, rememberMe, username)
          onGoogleLogin()
        }
      },
      enabled = !isGoogleSigningIn && !isLoading,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
      if (isGoogleSigningIn) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          color = primaryBlue,
          strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text("Signing in with Google...", color = darkText, fontWeight = FontWeight.Medium)
      } else {
        Icon(
          painter = painterResource(id = R.drawable.ic_google_logo),
          contentDescription = "Google Logo",
          modifier = Modifier.size(24.dp),
          tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text("Continue with Google", color = darkText, fontWeight = FontWeight.Medium)
      }
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
}

@Composable
fun WelcomeScreen(username: String, onSkip: () -> Unit, onStartSetup: () -> Unit) {
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)
  val lightBg = Color(0xFFF8F9FA)
  val welcomeBranding by AppBrandingCacheStore.stateFlow(LocalContext.current).collectAsStateWithLifecycle()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter
  ) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      TextButton(
        onClick = onSkip,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Skip", color = textGray, style = MaterialTheme.typography.bodyMedium)
      }
      
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f)
      ) {
        if (welcomeBranding.hasCustomLogo) {
          AppBrandingLogo(
            branding = welcomeBranding,
            size = 24.dp,
            cornerRadius = 6.dp,
            testTag = "welcome_branding_logo"
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
          text = welcomeBranding.appName,
          style = MaterialTheme.typography.titleLarge,
          color = primaryBlue,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("welcome_branding_app_name")
        )
      }
      
      IconButton(onClick = { /* Help */ }) {
        Icon(
          painter = painterResource(android.R.drawable.ic_menu_help),
          contentDescription = "Help",
          tint = textGray
        )
      }
    }

    Text(
      text = "FIRST TIME SETUP",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 1.5.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
      text = "Welcome to ${welcomeBranding.appName}",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = "Let's prepare your robotic hand system in just a few guided steps.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 16.dp)
    )

    // Main Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
      Column {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFFE3EDF7)),
          contentAlignment = Alignment.Center
        ) {
          coil.compose.AsyncImage(
            model = "https://img.freepik.com/premium-photo/robot-human-hand-touching-creating-spark-concept-artificial-intelligence-connection_14117-681283.jpg",
            contentDescription = "AI-Powered Robotic Rehabilitation",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = "AI-Powered Robotic Rehabilitation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = darkText,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          Text(
            text = "Control your NeuroGrip robotic hand using brain signals, muscle activity, or the mobile application with intelligent AI assistance.",
            style = MaterialTheme.typography.bodySmall,
            color = textGray,
            lineHeight = 18.sp
          )
        }
      }
    }

    // Feature Cards
    FeatureCard(iconRes = android.R.drawable.ic_menu_view, title = "Brain Control", subtitle = "Direct neural interface")
    FeatureCard(iconUrl = "https://cdn-icons-png.flaticon.com/512/2936/2936886.png", title = "Muscle Control", subtitle = "EMG signal processing")
    FeatureCard(iconUrl = "https://cdn-icons-png.flaticon.com/512/15/15874.png", title = "App Control", subtitle = "Manual adjustments")

    // Setup Includes
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Setup Includes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = darkText
          )
          Box(
            modifier = Modifier
              .background(Color(0xFFE8F0FE), RoundedCornerShape(16.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "5-7 Minutes",
              style = MaterialTheme.typography.labelSmall,
              color = primaryBlue,
              fontWeight = FontWeight.Bold
            )
          }
        }

        SetupItem(icon = android.R.drawable.ic_menu_save, text = "Connect ESP32 Microcontroller")
        SetupItem(icon = android.R.drawable.ic_media_play, text = "Pair EEG Headset")
        SetupItem(icon = android.R.drawable.ic_menu_compass, text = "Attach EMG Sensor")
        SetupItem(icon = android.R.drawable.ic_menu_edit, text = "Test Robotic Hand", isLast = true)
      }
    }

    // System Status
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = lightBg)
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Text(
          text = "CURRENT SYSTEM STATUS",
          style = MaterialTheme.typography.labelSmall,
          color = textGray,
          letterSpacing = 1.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(modifier = Modifier.weight(1f)) {
            StatusIndicator(text = "ESP32: Waiting")
          }
          Box(modifier = Modifier.weight(1f)) {
            StatusIndicator(text = "EEG: Not Connected")
          }
        }
      }
    }

    // Buttons
    Button(
      onClick = onStartSetup,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text(
        "START SETUP",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = { /* Watch Introduction Action */ },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
      Text(
        "WATCH QUICK INTRODUCTION",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = primaryBlue,
        textAlign = TextAlign.Center
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "By continuing, you agree to the safety guidelines.",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Text(
      text = "Step 1 of 5",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    LinearProgressIndicator(
      progress = { 1f / 5f },
      modifier = Modifier
        .width(100.dp)
        .height(4.dp),
      color = primaryBlue,
      trackColor = Color(0xFFE0E0E0),
      strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    
    Spacer(modifier = Modifier.height(40.dp))
  }
  }
}

@Composable
fun FeatureCard(iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null, iconRes: Int? = null, iconUrl: String? = null, title: String, subtitle: String) {
  val primaryBlue = Color(0xFF2F80ED)
  val darkText = Color(0xFF1A1B1F)
  val textGray = Color(0xFF727785)

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFEBEBEB))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .background(Color(0xFFE8F0FE), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
      ) {
        if (iconUrl != null) {
          coil.compose.AsyncImage(
            model = iconUrl,
            contentDescription = title,
            modifier = Modifier.size(24.dp)
          )
        } else if (iconVector != null) {
          Icon(
            imageVector = iconVector,
            contentDescription = title,
            tint = primaryBlue,
            modifier = Modifier.size(24.dp)
          )
        } else if (iconRes != null) {
          Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = primaryBlue,
            modifier = Modifier.size(24.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = darkText
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = textGray
      )
    }
  }
}

@Composable
fun SetupItem(icon: Int, text: String, isLast: Boolean = false) {
  val textGray = Color(0xFF727785)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = if (isLast) 0.dp else 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      tint = textGray,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = Color(0xFF1A1B1F)
    )
  }
}

@Composable
fun StatusIndicator(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .background(Color(0xFF9E9E9E), androidx.compose.foundation.shape.CircleShape)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = Color(0xFF1A1B1F)
    )
  }
}

@Composable
fun SignUpScreen(
  onBack: () -> Unit,
  onSignUp: (String) -> Unit,
  onGoogleLogin: () -> Unit,
  adminViewModel: AdminViewModel = viewModel()
) {
  var fullName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val lightBlueBg = Color(0xFFF0F8FF)
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter
  ) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    val signUpBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()

    if (signUpBranding.hasCustomLogo) {
      AppBrandingLogo(
        branding = signUpBranding,
        size = 56.dp,
        cornerRadius = 14.dp,
        modifier = Modifier.padding(bottom = 8.dp),
        testTag = "signup_branding_logo"
      )
    } else {
      Text(
        text = "🧠",
        fontSize = 40.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )
    }

    Text(
      text = signUpBranding.appName.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = primaryBlue,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag("signup_branding_app_name")
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
      onClick = {
        if (fullName.isBlank() || email.isBlank() || password.length < 4) {
          Toast.makeText(context, "Please enter name, email, and 4+ char password", Toast.LENGTH_SHORT).show()
        } else if (password != confirmPassword) {
          Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
        } else {
          adminViewModel.createUser(
            fullName = fullName,
            email = email,
            username = email.substringBefore("@"),
            password = password,
            mobile = "+91 98765 43210",
            role = AdminRole.USER,
            isSelfSignUp = true
          ) { success, msg ->
            if (success) {
              adminViewModel.issueOrResendOtp(
                identifier = email,
                purpose = "SIGNUP_VERIFY",
                requireAdmin = false,
                onChallengeIssued = {
                  Toast.makeText(context, "Account created! Verify the 6-digit OTP sent to $email", Toast.LENGTH_SHORT).show()
                  onSignUp(email)
                },
                onError = { otpErr ->
                  Toast.makeText(context, otpErr, Toast.LENGTH_SHORT).show()
                  onSignUp(email)
                }
              )
            } else {
              Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
          }
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("SIGN UP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onBack,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, primaryBlue)
    ) {
      Text("BACK TO LOGIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryBlue, textAlign = TextAlign.Center)
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
        .heightIn(min = 52.dp),
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
}

@Composable
fun ForgotPasswordScreen(
  adminViewModel: AdminViewModel = viewModel(),
  onBack: () -> Unit,
  onRequestReset: (String) -> Unit
) {
  var email by remember { mutableStateOf("hj195345@gmail.com") }
  var errorText by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter
  ) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    val forgotBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()

    if (forgotBranding.hasCustomLogo) {
      AppBrandingLogo(
        branding = forgotBranding,
        size = 56.dp,
        cornerRadius = 14.dp,
        modifier = Modifier.padding(bottom = 8.dp),
        testTag = "forgot_branding_logo"
      )
    } else {
      Text(
        text = "🧠",
        fontSize = 40.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )
    }

    Text(
      text = forgotBranding.appName.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = darkText,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag("forgot_branding_app_name")
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
      text = "Set or Reset Password via OTP",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
      text = "Enter your account or Super Admin email (hj195345@gmail.com) to receive a 6-digit verification OTP (60s expiry).",
      style = MaterialTheme.typography.bodySmall,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    if (errorText != null) {
      Text(
        text = errorText!!,
        color = Color(0xFFB91C1C),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp)
      )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Email Address",
            style = MaterialTheme.typography.bodyMedium,
            color = darkText,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        TextField(
          value = email,
          onValueChange = {
            email = it
            errorText = null
          },
          placeholder = { Text("hj195345@gmail.com", color = textGray) },
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
      onClick = {
        val targetEmail = email.trim()
        if (targetEmail.isBlank() || !targetEmail.contains("@")) {
          errorText = "Please enter a valid email address."
        } else {
          adminViewModel.issueOrResendOtp(
            identifier = targetEmail,
            purpose = "PASSWORD_RESET",
            requireAdmin = false,
            onChallengeIssued = { challenge ->
              Toast.makeText(context, "6-digit reset OTP sent to ${challenge.maskedEmail}", Toast.LENGTH_SHORT).show()
              onRequestReset(challenge.email)
            },
            onError = { err ->
              errorText = err
              Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
          )
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text("Send 6-Digit OTP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onBack,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, Color(0xFF336699))
    ) {
      Text("Go back to Login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = Color(0xFF336699), textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(24.dp))
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

  val splashBranding by AppBrandingCacheStore.stateFlow(LocalContext.current).collectAsStateWithLifecycle()

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
      if (splashBranding.hasCustomLogo) {
        AppBrandingLogo(
          branding = splashBranding,
          size = 112.dp,
          cornerRadius = 24.dp,
          modifier = Modifier.padding(bottom = 16.dp),
          testTag = "splash_branding_logo"
        )
      } else {
        Text(
          text = "🧠",
          fontSize = 100.sp,
          modifier = Modifier.padding(bottom = 16.dp)
        )
      }
      Text(
        text = splashBranding.appName.uppercase(),
        style = MaterialTheme.typography.headlineLarge,
        color = Color(0xFF003366),
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier.testTag("splash_branding_app_name")
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
fun OtpScreen(
  email: String,
  next: String = "user_login",
  adminViewModel: AdminViewModel = viewModel(),
  onVerifiedAdmin: () -> Unit = {},
  onVerifiedUser: (String) -> Unit = {},
  onVerifiedReset: (String) -> Unit = {},
  onBack: () -> Unit
) {
  var otpValue by remember { mutableStateOf("") }
  var otpErrorMessage by remember { mutableStateOf<String?>(null) }
  val challenge by adminViewModel.pendingOtpChallenge.collectAsState()
  val isLoading by adminViewModel.isLoading.collectAsState()
  val context = LocalContext.current

  val isAdminFlow = next == "admin_login"
  val primaryBlue = if (isAdminFlow) Color(0xFF1E3A8A) else Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val darkText = Color(0xFF1A1B1F)

  // Ensure an active OTP challenge exists if user navigated directly
  LaunchedEffect(email) {
    if (challenge == null || !challenge!!.email.equals(email.trim(), ignoreCase = true)) {
      adminViewModel.issueOrResendOtp(
        identifier = email,
        purpose = if (next == "reset") "PASSWORD_RESET" else "LOGIN",
        requireAdmin = isAdminFlow
      )
    }
  }

  // Live 60-second countdown synchronized with authoritative expiration timestamp
  var remainingSeconds by remember { mutableIntStateOf(60) }
  var resendCooldownSeconds by remember { mutableIntStateOf(15) }

  LaunchedEffect(challenge?.expiresAtMillis, challenge?.resendAvailableAtMillis) {
    val targetExpiry = challenge?.expiresAtMillis ?: (System.currentTimeMillis() + 60_000L)
    val resendTarget = challenge?.resendAvailableAtMillis ?: (System.currentTimeMillis() + 15_000L)
    while (true) {
      val now = System.currentTimeMillis()
      val secsLeft = ((targetExpiry - now + 999L) / 1000L).toInt().coerceAtLeast(0)
      remainingSeconds = secsLeft
      val resendSecsLeft = ((resendTarget - now + 999L) / 1000L).toInt().coerceAtLeast(0)
      resendCooldownSeconds = resendSecsLeft
      delay(250L)
    }
  }

  val isExpired = remainingSeconds <= 0
  val remainingSends = challenge?.remainingSendsInWindow ?: 2

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter
  ) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    val otpBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()

    if (otpBranding.hasCustomLogo) {
      AppBrandingLogo(
        branding = otpBranding,
        size = 52.dp,
        cornerRadius = 12.dp,
        modifier = Modifier.padding(bottom = 6.dp),
        testTag = "otp_branding_logo"
      )
    } else {
      Text(
        text = "🧠",
        fontSize = 36.sp,
        modifier = Modifier.padding(bottom = 6.dp)
      )
    }

    Text(
      text = otpBranding.appName.uppercase(),
      style = MaterialTheme.typography.titleMedium,
      color = primaryBlue,
      textAlign = TextAlign.Center,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag("otp_branding_app_name")
    )
    
    Text(
      text = if (isAdminFlow) "SUPER ADMIN EMAIL OTP VERIFICATION" else "EMAIL OTP VERIFICATION",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 1.5.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 20.dp)
    )
    
    Text(
      text = if (isAdminFlow) "Verify Admin OTP" else "Verify Your Email",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.ExtraBold,
      color = darkText,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
      text = "Enter the 6-digit verification OTP sent to your verified email address. Each code is single-use and expires in exactly 60 seconds.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color(0xFFF1F5F9),
      modifier = Modifier.padding(bottom = 16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
      ) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = primaryBlue)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = challenge?.email ?: email,
          color = darkText,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Live 60-Second Countdown & Rate Limit Status Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (isExpired) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
      ),
      border = BorderStroke(
        1.dp,
        if (isExpired) Color(0xFFFECACA) else Color(0xFFBFDBFE)
      )
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = if (isExpired) "OTP EXPIRED (60s TTL)" else "LIVE OTP COUNTDOWN",
            style = MaterialTheme.typography.labelSmall,
            color = if (isExpired) Color(0xFFB91C1C) else primaryBlue,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Rate limit: $remainingSends of 3 sends left (10m window)",
            style = MaterialTheme.typography.bodySmall,
            color = textGray
          )
        }
        Text(
          text = String.format("00:%02d", remainingSeconds),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          color = if (isExpired) Color(0xFFB91C1C) else primaryBlue
        )
      }
    }

    val isDevTestModeActive = challenge?.isTestOtp == true ||
      challenge?.message?.contains("DEV TEST MODE") == true

    if (isDevTestModeActive) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Text(
            text = "DEVELOPMENT TESTING MODE ACTIVE",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF92400E),
            fontWeight = FontWeight.ExtraBold
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Default Testing OTP: 000000 (Authorized test account only). Live email dispatch is bypassed.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF78350F)
          )
        }
      }
    }

    // Email Delivery Status Banner
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
      border = BorderStroke(1.dp, Color(0xFFBBF7D0))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Text(
          text = if (isDevTestModeActive) "Test OTP Challenge Active" else "OTP sent to your registered email.",
          style = MaterialTheme.typography.labelMedium,
          color = Color(0xFF166534),
          fontWeight = FontWeight.Bold
        )
        Text(
          text = challenge?.message ?: "Please check your inbox (${challenge?.maskedEmail ?: email}) and enter the 6-digit verification code within 60 seconds.",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF14532D)
        )
      }
    }

    if (otpErrorMessage != null) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
      ) {
        Text(
          text = otpErrorMessage!!,
          color = Color(0xFFB91C1C),
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(12.dp)
        )
      }
    }

    Text(
        text = "Enter 6-Digit Verification OTP",
        style = MaterialTheme.typography.labelSmall,
        color = textGray,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    androidx.compose.foundation.text.BasicTextField(
        value = otpValue,
        onValueChange = { input ->
          val digitsOnly = input.filter { it.isDigit() }
          if (digitsOnly.length <= 6) {
            otpValue = digitsOnly
            otpErrorMessage = null
          }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
          keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        singleLine = true,
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) { index ->
                    val char = when {
                        index >= otpValue.length -> ""
                        else -> otpValue[index].toString()
                    }
                    val isFocused = otpValue.length == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isExpired) Color(0xFFFCA5A5) else if (isFocused) primaryBlue else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = char, style = MaterialTheme.typography.titleLarge, color = darkText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )

    Button(
      onClick = {
        otpErrorMessage = null
        if (otpValue.length != 6) {
          otpErrorMessage = "Please enter the complete 6-digit OTP."
          return@Button
        }
        if (isExpired) {
          otpErrorMessage = "OTP has expired (60-second limit). Please tap Resend OTP."
          return@Button
        }
        val targetEmail = challenge?.email ?: email
        if (next == "reset") {
          adminViewModel.verifyOtpForPasswordReset(
            email = targetEmail,
            otpCode = otpValue,
            onSuccess = {
              Toast.makeText(context, "OTP verified! Set your new password.", Toast.LENGTH_SHORT).show()
              onVerifiedReset(targetEmail)
            },
            onError = { err ->
              otpErrorMessage = err
              Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
          )
        } else {
          adminViewModel.verifyOtpAndCompleteLogin(
            identifier = targetEmail,
            otpCode = otpValue,
            requireAdmin = false,
            rememberMe = SecureSessionManager.isRememberMeEnabled(context),
            onSuccess = { outcome ->
              if (outcome.isAdminPortalEligible) {
                Toast.makeText(
                  context,
                  "OTP Verified! Welcome Admin (${outcome.user.email})",
                  Toast.LENGTH_SHORT
                ).show()
                onVerifiedAdmin()
              } else {
                Toast.makeText(
                  context,
                  "Email OTP Verified! Welcome ${outcome.user.name}",
                  Toast.LENGTH_SHORT
                ).show()
                onVerifiedUser(outcome.user.username)
              }
            },
            onError = { err ->
              otpErrorMessage = err
              Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
          )
        }
      },
      enabled = !isLoading,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Text(
        if (isAdminFlow) "VERIFY OTP & OPEN ADMIN PANEL" else "VERIFY OTP",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = {
        otpErrorMessage = null
        otpValue = ""
        val targetEmail = challenge?.email ?: email
        adminViewModel.issueOrResendOtp(
          identifier = targetEmail,
          purpose = if (next == "reset") "PASSWORD_RESET" else "LOGIN",
          requireAdmin = isAdminFlow,
          onChallengeIssued = { newChallenge ->
            Toast.makeText(
              context,
              "New 6-digit OTP sent to ${newChallenge.maskedEmail} (previous OTP invalidated)",
              Toast.LENGTH_SHORT
            ).show()
          },
          onError = { err ->
            otpErrorMessage = err
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
          }
        )
      },
      enabled = !isLoading && (resendCooldownSeconds == 0 || isExpired),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, primaryBlue)
    ) {
      val label = if (resendCooldownSeconds > 0 && !isExpired) {
        "Resend OTP in ${resendCooldownSeconds}s (${remainingSends} left in 10m)"
      } else {
        "Resend 6-Digit OTP (${remainingSends} left in 10m)"
      }
      Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = primaryBlue, textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(
      onClick = onBack,
      modifier = Modifier.heightIn(min = 48.dp)
    ) {
      Text("Back to Login", style = MaterialTheme.typography.bodyMedium, color = darkText, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
  }
}
