package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    onLogout: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHandControl: () -> Unit = {},
    onNavigateToAIAssistant: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    adminViewModel: AdminViewModel = viewModel()
) {
    val primaryBlue = Color(0xFF0055AA)
    val buttonBlue = Color(0xFF4A7499)
    val textGray = Color(0xFF64748B)
    val darkText = Color(0xFF1E293B)
    val lightBg = Color(0xFFF5F7FA)
    val cardBg = Color.White

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appBranding by AppBrandingCacheStore.stateFlow(context).collectAsStateWithLifecycle()

    val currentUserProfile by adminViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val currentSession by adminViewModel.currentSession.collectAsStateWithLifecycle()
    val allUsers by adminViewModel.users.collectAsStateWithLifecycle()
    val isPhotoUploading by adminViewModel.isProfilePhotoUploading.collectAsStateWithLifecycle()

    val matchedUser = remember(currentUserProfile, currentSession, allUsers, username) {
        currentUserProfile ?: allUsers.firstOrNull { u ->
            (username.isNotBlank() && (u.username.equals(username, ignoreCase = true) || u.email.equals(username, ignoreCase = true))) ||
                (currentSession?.email?.isNotBlank() == true && u.email.equals(currentSession?.email, ignoreCase = true))
        }
    }

    val resolvedName = matchedUser?.name?.takeIf { it.isNotBlank() }
        ?: currentSession?.name?.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() && !it.equals("User", ignoreCase = true) }
        ?: ""
    val resolvedEmail = matchedUser?.email?.takeIf { it.isNotBlank() }
        ?: currentSession?.email?.takeIf { it.isNotBlank() }
        ?: ""
    val resolvedMobile = matchedUser?.mobile?.takeIf { it.isNotBlank() } ?: ""
    val resolvedPhotoUrl = matchedUser?.photoUrl?.takeIf { it.isNotBlank() }
        ?: currentSession?.photoUrl?.takeIf { it.isNotBlank() }
    val resolvedPhotoUpdatedAt = maxOf(
        matchedUser?.photoUpdatedAt ?: 0L,
        currentSession?.photoUpdatedAt ?: 0L
    )
    val resolvedMemberYear = remember(matchedUser?.timestamp) {
        val ts = matchedUser?.timestamp ?: 0L
        if (ts > 0L) SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(ts)) else "2026"
    }
    val resolvedMemberCode = remember(matchedUser?.id, matchedUser?.deviceId) {
        val dev = matchedUser?.deviceId?.takeIf { it.isNotBlank() && it != "UNPAIRED" }
        dev ?: matchedUser?.id?.let { "NG-2026-${it.toString().padStart(3, '0')}" } ?: "NG-2026-001"
    }

    var isEditing by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var showPhotoEditorDialog by remember { mutableStateOf(false) }
    var stagedPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var stagedValidatedPhoto by remember { mutableStateOf<ValidatedProfilePhoto?>(null) }
    var photoValidationError by remember { mutableStateOf<String?>(null) }

    var editName by remember(resolvedName) { mutableStateOf(resolvedName.ifBlank { username }) }
    var editEmail by remember(resolvedEmail) { mutableStateOf(resolvedEmail) }
    var editMobile by remember(resolvedMobile) { mutableStateOf(resolvedMobile.ifBlank { "Not set" }) }

    LaunchedEffect(resolvedName, resolvedEmail, resolvedMobile, isEditing) {
        if (!isEditing) {
            if (resolvedName.isNotBlank()) editName = resolvedName
            if (resolvedEmail.isNotBlank()) editEmail = resolvedEmail
            editMobile = resolvedMobile.ifBlank { "Not set" }
        }
    }

    // Dynamic Network Check + Latest Firebase Profile Sync
    var isOnline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        adminViewModel.refreshOwnProfileFromFirebase()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    ProfilePhotoValidator.readAndValidateUri(context, uri)
                }
                result.onSuccess { validated ->
                    stagedPreviewUri = uri
                    stagedValidatedPhoto = validated
                    photoValidationError = null
                    showPhotoEditorDialog = true
                }.onFailure { err ->
                    stagedPreviewUri = null
                    stagedValidatedPhoto = null
                    photoValidationError = err.message ?: "Selected image is invalid or exceeds the 5 MB limit."
                    showPhotoEditorDialog = true
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.White,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = onNavigateToDashboard,
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.PanTool, contentDescription = "Control") },
                    label = { Text("Control", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToHandControl,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Psychology, contentDescription = "AI") },
                    label = { Text("AI", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToAIAssistant,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Timeline, contentDescription = "Progress") },
                    label = { Text("Progress", fontSize = 10.sp) },
                    selected = false,
                    onClick = onNavigateToReports,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = textGray, unselectedTextColor = textGray)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                    selected = true,
                    onClick = { },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = buttonBlue,
                        selectedTextColor = buttonBlue,
                        indicatorColor = buttonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = textGray,
                        unselectedTextColor = textGray
                    )
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (appBranding.hasCustomLogo) {
                            AppBrandingLogo(
                                branding = appBranding,
                                size = 24.dp,
                                cornerRadius = 6.dp,
                                testTag = "profile_branding_logo"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            appBranding.appName,
                            color = primaryBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("profile_branding_app_name")
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToDashboard) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = darkText)
                    }
                },
                actions = {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = if (isEditing) primaryBlue else textGray)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = textGray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = lightBg)
            )
        },
        containerColor = lightBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
        ) {
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = darkText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        SmartUserAvatar(
                            photoUrl = resolvedPhotoUrl,
                            fullName = resolvedName,
                            email = resolvedEmail,
                            photoUpdatedAt = resolvedPhotoUpdatedAt,
                            size = 100.dp,
                            fontSize = 38.sp,
                            borderColor = Color(0xFFDBEAFE),
                            borderWidth = 3.dp,
                            onClick = {
                                stagedPreviewUri = null
                                stagedValidatedPhoto = null
                                photoValidationError = null
                                showPhotoEditorDialog = true
                            },
                            contentDescription = "Profile Photo",
                            testTag = "profile_main_avatar"
                        )
                        Surface(
                            onClick = {
                                stagedPreviewUri = null
                                stagedValidatedPhoto = null
                                photoValidationError = null
                                showPhotoEditorDialog = true
                            },
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("profile_avatar_edit_badge")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .background(Color(0xFFEFF6FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = "Edit Profile Photo",
                                    tint = primaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            stagedPreviewUri = null
                            stagedValidatedPhoto = null
                            photoValidationError = null
                            showPhotoEditorDialog = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("edit_profile_photo_button")
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (SmartAvatarResolver.isUsablePhotoUrl(resolvedPhotoUrl)) {
                                "Edit Profile Photo"
                            } else {
                                "Upload Profile Photo"
                            },
                            color = primaryBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = resolvedName.ifBlank { editName.ifBlank { "NeuroGrip User" } },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = darkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            resolvedMemberCode,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = "Verified", tint = primaryBlue, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .background(if (isOnline) Color(0xFFECFDF5) else Color(0xFFFEF2F2), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isOnline) "Device Online" else "Device Offline",
                                color = if (isOnline) Color(0xFF047857) else Color(0xFFB91C1C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Member Since: $resolvedMemberYear", style = MaterialTheme.typography.bodySmall, color = textGray)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Personal Information
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                        if (isEditing) {
                            TextButton(onClick = {
                                adminViewModel.updateOwnProfileDetails(
                                    identifierHint = matchedUser?.username ?: username,
                                    newName = editName.trim(),
                                    newMobile = if (editMobile == "Not set") "" else editMobile.trim(),
                                    onComplete = { ok, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (ok) {
                                            isEditing = false
                                        }
                                    }
                                )
                            }) {
                                Text("Save", fontWeight = FontWeight.Bold, color = primaryBlue)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    ProfileInfoRow(
                        icon = Icons.Outlined.Person,
                        label = "Full Name",
                        value = editName.ifBlank { "Not set" },
                        isEditing = isEditing,
                        onValueChange = { editName = it }
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(
                        icon = Icons.Outlined.Email,
                        label = "Email Address",
                        value = editEmail.ifBlank { "Not set" },
                        isEditing = false,
                        onValueChange = {}
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                    ProfileInfoRow(
                        icon = Icons.Outlined.Phone,
                        label = "Mobile Number",
                        value = editMobile,
                        isEditing = isEditing,
                        onValueChange = { editMobile = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log Out Button
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 56.dp)
                    .testTag("profile_logout_button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delete Permanent Account Button
            Button(
                onClick = { showDeleteAccountDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 56.dp)
                    .testTag("profile_delete_permanent_account_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB91C1C),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = "Delete Permanent Account",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Permanent Account", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
        }

        if (showPhotoEditorDialog) {
            ProfilePhotoEditorDialog(
                currentPhotoUrl = resolvedPhotoUrl,
                fullName = resolvedName,
                email = resolvedEmail,
                photoUpdatedAt = resolvedPhotoUpdatedAt,
                stagedPreviewUri = stagedPreviewUri,
                stagedValidatedPhoto = stagedValidatedPhoto,
                validationError = photoValidationError,
                isUploading = isPhotoUploading,
                onPickFromGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSavePhoto = {
                    val photo = stagedValidatedPhoto
                    if (photo != null) {
                        adminViewModel.saveValidatedProfilePhoto(
                            validated = photo,
                            identifierHint = matchedUser?.username ?: username,
                            onComplete = { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (ok) {
                                    stagedPreviewUri = null
                                    stagedValidatedPhoto = null
                                    photoValidationError = null
                                    showPhotoEditorDialog = false
                                } else {
                                    photoValidationError = msg
                                }
                            }
                        )
                    }
                },
                onRemovePhoto = {
                    adminViewModel.removeOwnProfilePhoto(
                        identifierHint = matchedUser?.username ?: username,
                        onComplete = { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                stagedPreviewUri = null
                                stagedValidatedPhoto = null
                                photoValidationError = null
                                showPhotoEditorDialog = false
                            } else {
                                photoValidationError = msg
                            }
                        }
                    )
                },
                onDismiss = {
                    stagedPreviewUri = null
                    stagedValidatedPhoto = null
                    photoValidationError = null
                    showPhotoEditorDialog = false
                }
            )
        }

        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFB91C1C)
                    )
                },
                title = {
                    Text("Delete Permanent Account", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                },
                text = {
                    Text(
                        "Are you sure you want to permanently delete your account? All your profile data, saved settings, and account access will be permanently removed. This action cannot be undone.",
                        fontSize = 14.sp,
                        color = darkText
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteAccountDialog = false
                            adminViewModel.deleteOwnAccountPermanently(
                                usernameOrIdentifier = matchedUser?.username ?: username,
                                onSuccess = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    onLogout()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB91C1C),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_delete_permanent_account_button")
                    ) {
                        Text("Delete Permanently", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isEditing: Boolean = false,
    onValueChange: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(2.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF1E293B))
                )
            } else {
                Text(value, fontSize = 14.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Medium)
            }
        }
        if (!isEditing) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFFCBD5E1))
        }
    }
}

