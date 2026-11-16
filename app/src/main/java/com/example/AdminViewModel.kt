package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AdminRepository(application.applicationContext)

    val users: StateFlow<List<User>> = repository.usersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsersIncludingDeleted: StateFlow<List<User>> = repository.allUsersIncludingDeletedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loginHistory: StateFlow<List<LoginHistoryEntity>> = repository.loginHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.notificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remoteConfig: StateFlow<RemoteConfigEntity?> = repository.remoteConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteConfigEntity())

    val configHistory: StateFlow<List<ConfigHistoryEntity>> = repository.configHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val banners: StateFlow<List<BannerContentEntity>> = repository.bannersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeBanners: StateFlow<List<BannerContentEntity>> = repository.activeBannersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bannerAnalytics: StateFlow<List<BannerAnalyticsEntity>> = repository.bannerAnalyticsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bannerSyncStatus: StateFlow<BannerRealtimeSyncStatus> = repository.bannerSyncStatus

    val bannerDismissRevision: StateFlow<Long> = repository.bannerDismissRevision

    val bannerMetrics: StateFlow<BannerDashboardMetrics> = combine(banners, bannerAnalytics) { list, analytics ->
        BannerEligibilityEngine.computeDashboardMetrics(list, analytics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BannerDashboardMetrics())

    val bannerAnalyticsSummary: StateFlow<BannerAnalyticsSummary> = combine(banners, bannerAnalytics) { list, analytics ->
        BannerEligibilityEngine.buildAnalyticsSummary(list, analytics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BannerAnalyticsSummary())

    val tickets: StateFlow<List<SupportTicketEntity>> = repository.ticketsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionRecordEntity>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backups: StateFlow<List<BackupSnapshotEntity>> = repository.backupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sduiSyncState: StateFlow<SduiSyncState> = repository.sduiSyncState

    val sduiHistory: StateFlow<List<SduiConfigHistoryEntity>> = repository.sduiHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemConfig: StateFlow<SystemConfigRuntimeDto> = repository.systemConfigFlow

    private val _systemConfigError = MutableStateFlow<String?>(null)
    val systemConfigError: StateFlow<String?> = _systemConfigError.asStateFlow()

    private val _currentSession = MutableStateFlow<AuthSessionDto?>(
        SecureSessionManager.loadSession(application.applicationContext)
    )
    val currentSession: StateFlow<AuthSessionDto?> = _currentSession.asStateFlow()

    val currentUserProfile: StateFlow<User?> = combine(
        _currentSession,
        users,
        allUsersIncludingDeleted
    ) { session, activeList, allList ->
        val pool = activeList.ifEmpty { allList }
        if (session != null) {
            pool.firstOrNull {
                (session.username.isNotBlank() && it.username.equals(session.username, ignoreCase = true)) ||
                    (session.email.isNotBlank() && it.email.equals(session.email, ignoreCase = true)) ||
                    (session.userId > 0 && it.id == session.userId)
            }
        } else {
            val fbEmail = runCatching {
                if (com.google.firebase.FirebaseApp.getApps(application.applicationContext).isNotEmpty()) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.trim()
                } else null
            }.getOrNull()
            if (!fbEmail.isNullOrBlank()) {
                pool.firstOrNull { it.email.equals(fbEmail, ignoreCase = true) }
            } else null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isProfilePhotoUploading = MutableStateFlow(false)
    val isProfilePhotoUploading: StateFlow<Boolean> = _isProfilePhotoUploading.asStateFlow()

    val appBranding: StateFlow<AppBrandingConfig> = repository.appBrandingFlow
    val brandingUploadProgress: StateFlow<Float?> = repository.brandingUploadProgress
    val launcherBrandingInfo: LauncherBrandingInfo =
        ApkLauncherBrandingInspector.inspect(application.applicationContext)

    private val _pendingOtpChallenge = MutableStateFlow<OtpChallengeState?>(null)
    val pendingOtpChallenge: StateFlow<OtpChallengeState?> = _pendingOtpChallenge.asStateFlow()

    private val _activeRoleOverride = MutableStateFlow<AdminRole?>(null)
    val activeRoleOverride: StateFlow<AdminRole?> = _activeRoleOverride.asStateFlow()

    private val _selectedAdminSection = MutableStateFlow(AdminSection.DASHBOARD)
    val selectedAdminSection: StateFlow<AdminSection> = _selectedAdminSection.asStateFlow()

    private val _isAdminPreviewMode = MutableStateFlow(false)
    val isAdminPreviewMode: StateFlow<Boolean> = _isAdminPreviewMode.asStateFlow()

    // null = offline / OTP / not yet queried, true = cloud confirmed admin, false = cloud denied
    private val _cloudAdminStatusConfirmed = MutableStateFlow<Boolean?>(null)
    private val _latestDbUsersSnapshot = MutableStateFlow<List<User>>(emptyList())

    private val _isAuthorizedAdminSession = MutableStateFlow(
        isSessionStrictlyAuthorizedAdmin(_currentSession.value, emptyList(), null)
    )
    val isAuthorizedAdminSession: StateFlow<Boolean> = _isAuthorizedAdminSession.asStateFlow()

    private val _showFloatingAdminReturnButton = MutableStateFlow(false)
    val showFloatingAdminReturnButton: StateFlow<Boolean> = _showFloatingAdminReturnButton.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _aiGeneratedOutput = MutableStateFlow(
        "AI Operations Co-Pilot Ready: Select an analytical or operational task above to generate insights from live database metrics."
    )
    val aiGeneratedOutput: StateFlow<String> = _aiGeneratedOutput.asStateFlow()

    // --- Firestore Database Management State ---
    private val _firestorePageState = MutableStateFlow<PaginatedDocumentsResult?>(null)
    val firestorePageState: StateFlow<PaginatedDocumentsResult?> = _firestorePageState.asStateFlow()

    private val _firestoreLoading = MutableStateFlow(false)
    val firestoreLoading: StateFlow<Boolean> = _firestoreLoading.asStateFlow()

    private val _firestoreError = MutableStateFlow<String?>(null)
    val firestoreError: StateFlow<String?> = _firestoreError.asStateFlow()

    private val _exportedCollectionJson = MutableStateFlow<String?>(null)
    val exportedCollectionJson: StateFlow<String?> = _exportedCollectionJson.asStateFlow()

    private val _isGoogleSignInInProgress = MutableStateFlow(false)
    val isGoogleSignInInProgress: StateFlow<Boolean> = _isGoogleSignInInProgress.asStateFlow()

    private val _adminSignatureConfig = MutableStateFlow(
        AdminDigitalSignatureManager.loadSignature(application.applicationContext)
    )
    val adminSignatureConfig: StateFlow<AdminSignatureConfig> = _adminSignatureConfig.asStateFlow()

    private val _generatedReports = MutableStateFlow(
        GeneratedReportStore.loadAllReports(application.applicationContext)
    )
    val generatedReports: StateFlow<List<GeneratedReportRecord>> = _generatedReports.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allUsersIncludingDeletedFlow.collect { usersList ->
                _latestDbUsersSnapshot.value = usersList
                recomputeAdminSwitchStates(usersList)
            }
        }
        viewModelScope.launch {
            repository.ensureSeeded()
            repository.syncRealUsersFromFirebase()
            repository.syncCurrentFirebaseUserProfile(_currentSession.value)?.let { synced ->
                _currentSession.value?.let { sess ->
                    _currentSession.value = sess.copy(
                        name = synced.name,
                        photoUrl = synced.photoUrl,
                        photoUpdatedAt = synced.photoUpdatedAt
                    )
                }
            }
            repository.startSduiRealtimeSync()
            repository.startBrandingRealtimeSync()
            val isAdminSession = _currentSession.value?.let {
                RolePermissionPolicy.hasPermission(
                    AdminRole.fromString(it.role),
                    AdminPermission.VIEW_DASHBOARD
                )
            } ?: false
            repository.startBannerRealtimeSync(isAdminUser = isAdminSession)
            if (isAdminSession) {
                refreshCloudAdminVerification()
            }
            loadFirestoreCollection("users")
        }
    }

    private fun recomputeAdminSwitchStates(
        allUsers: List<User> = _latestDbUsersSnapshot.value.ifEmpty { allUsersIncludingDeleted.value }
    ) {
        val authorized = isSessionStrictlyAuthorizedAdmin(
            session = _currentSession.value,
            allUsers = allUsers,
            cloudConfirmed = _cloudAdminStatusConfirmed.value
        )
        _isAuthorizedAdminSession.value = authorized
        if (!authorized) {
            _isAdminPreviewMode.value = false
        }
        _showFloatingAdminReturnButton.value = authorized &&
            _isAdminPreviewMode.value &&
            SecureSessionManager.isSessionValid(_currentSession.value)
    }

    fun selectAdminSection(section: AdminSection) {
        _selectedAdminSection.value = section
    }

    fun onEnteredAdminPanel() {
        _isAdminPreviewMode.value = false
        recomputeAdminSwitchStates()
    }

    fun refreshSessionFromStorage() {
        val loaded = SecureSessionManager.loadSession(getApplication())
        _currentSession.value = loaded
        if (loaded == null || !isSessionStrictlyAuthorizedAdmin(
                loaded,
                _latestDbUsersSnapshot.value.ifEmpty { allUsersIncludingDeleted.value },
                _cloudAdminStatusConfirmed.value
            )
        ) {
            _isAdminPreviewMode.value = false
        }
        recomputeAdminSwitchStates()
    }

    fun verifiedSessionAdminRole(): AdminRole {
        val session = _currentSession.value
        if (!SecureSessionManager.isSessionValid(session)) return AdminRole.USER
        val claims = SecureSessionManager.extractVerifiedTokenClaims(session?.token) ?: return AdminRole.USER
        return claims.role
    }

    fun isSessionStrictlyAuthorizedAdmin(
        session: AuthSessionDto? = _currentSession.value,
        allUsers: List<User> = _latestDbUsersSnapshot.value.ifEmpty { allUsersIncludingDeleted.value },
        cloudConfirmed: Boolean? = _cloudAdminStatusConfirmed.value
    ): Boolean {
        if (!SecureSessionManager.isSessionValid(session)) return false
        val validSession = session!!
        val claims = SecureSessionManager.extractVerifiedTokenClaims(validSession.token) ?: return false
        if (!SecureSessionManager.isAuthorizedAdminRole(claims.role)) return false

        val matchedDbUser = (if (validSession.userId > 0) allUsers.find { it.id == validSession.userId } else null)
            ?: allUsers.firstOrNull { u ->
                (validSession.username.isNotBlank() && u.username.equals(validSession.username, ignoreCase = true)) ||
                    (validSession.email.isNotBlank() && u.email.equals(validSession.email, ignoreCase = true))
            }

        if (matchedDbUser != null) {
            if (!matchedDbUser.status.equals(AccountStatus.ACTIVE.name, ignoreCase = true)) return false
            val dbRole = AdminRole.fromString(matchedDbUser.role)
            if (!SecureSessionManager.isAuthorizedAdminRole(dbRole)) return false
        }

        val fbUser = runCatching {
            if (com.google.firebase.FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } else {
                null
            }
        }.getOrNull()

        if (fbUser != null) {
            if (fbUser.isAnonymous) return false
            val fbEmail = fbUser.email?.trim()?.lowercase().orEmpty()
            val sessEmail = validSession.email.trim().lowercase()
            if (sessEmail.isNotBlank() && fbEmail.isNotBlank() && fbEmail != sessEmail) return false
        }

        if (cloudConfirmed == false) return false
        return true
    }

    fun validateActiveAdminSessionAndRevokeIfExpired(): Boolean {
        val session = _currentSession.value
        val valid = isSessionStrictlyAuthorizedAdmin(
            session = session,
            allUsers = _latestDbUsersSnapshot.value.ifEmpty { allUsersIncludingDeleted.value },
            cloudConfirmed = _cloudAdminStatusConfirmed.value
        )
        if (!valid) {
            if (session != null && !SecureSessionManager.isSessionValid(session)) {
                SecureSessionManager.clearSession(getApplication())
                _currentSession.value = null
                _activeRoleOverride.value = null
                _cloudAdminStatusConfirmed.value = null
            }
            _isAdminPreviewMode.value = false
        }
        recomputeAdminSwitchStates()
        return valid
    }

    fun refreshCloudAdminVerification() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            when (val res = repository.verifyAuthorizedAdminAccess(session)) {
                is RepoResult.Success -> {
                    _cloudAdminStatusConfirmed.value = true
                    recomputeAdminSwitchStates()
                }
                is RepoResult.Error -> {
                    if (res.code == 401 || res.code == 403) {
                        _cloudAdminStatusConfirmed.value = false
                        _isAdminPreviewMode.value = false
                        if (res.code == 401) {
                            SecureSessionManager.clearSession(getApplication())
                            _currentSession.value = null
                        }
                        recomputeAdminSwitchStates()
                    }
                }
            }
        }
    }

    fun enterUserAppPreviewMode(): Boolean {
        if (!validateActiveAdminSessionAndRevokeIfExpired()) {
            _statusMessage.value = "Admin session expired or unauthorized. Please sign in again."
            return false
        }
        _isAdminPreviewMode.value = true
        recomputeAdminSwitchStates()
        refreshCloudAdminVerification()
        return true
    }

    fun returnToAdminPanel(): Boolean {
        if (!validateActiveAdminSessionAndRevokeIfExpired()) {
            _statusMessage.value = "Admin session expired or unauthorized. Please sign in again."
            return false
        }
        _isAdminPreviewMode.value = false
        recomputeAdminSwitchStates()
        refreshCloudAdminVerification()
        return true
    }

    fun effectiveRole(): AdminRole {
        _activeRoleOverride.value?.let { return it }
        val roleStr = _currentSession.value?.role ?: AdminRole.USER.name
        return AdminRole.fromString(roleStr)
    }

    fun effectiveUsername(): String {
        return _currentSession.value?.username?.ifBlank { "hj123" } ?: "hj123"
    }

    fun setRoleSimulationOverride(role: AdminRole?) {
        _activeRoleOverride.value = role
        _statusMessage.value = if (role != null) {
            "RBAC Simulation Active: Viewing & enforcing as ${role.displayName}"
        } else {
            "Restored native session role: ${effectiveRole().displayName}"
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun authenticateUserOrAdmin(
        username: String,
        password: String,
        requireAdmin: Boolean = false,
        onSuccess: (LoginOutcome) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.authenticate(username, password, requireAdmin)) {
                is RepoResult.Success -> {
                    _currentSession.value = res.data.session
                    _activeRoleOverride.value = null
                    _isAdminPreviewMode.value = false
                    _selectedAdminSection.value = AdminSection.DASHBOARD
                    _cloudAdminStatusConfirmed.value = null
                    _isLoading.value = false
                    recomputeAdminSwitchStates()
                    repository.startBannerRealtimeSync(res.data.isAdminPortalEligible)
                    if (res.data.isAdminPortalEligible) {
                        refreshCloudAdminVerification()
                    }
                    onSuccess(res.data)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun initiateEmailOtpLogin(
        identifier: String,
        password: String,
        requireAdmin: Boolean = false,
        onChallengeIssued: (OtpChallengeState) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.initiateEmailOtpLogin(identifier, password, requireAdmin)) {
                is RepoResult.Success -> {
                    // Do not grant session before 6-digit OTP verification
                    _currentSession.value = null
                    _pendingOtpChallenge.value = res.data
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onChallengeIssued(res.data)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun issueOrResendOtp(
        identifier: String,
        purpose: String = "LOGIN",
        requireAdmin: Boolean = false,
        onChallengeIssued: (OtpChallengeState) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.issueOrResendEmailOtp(identifier, purpose, requireAdmin)) {
                is RepoResult.Success -> {
                    _pendingOtpChallenge.value = res.data
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onChallengeIssued(res.data)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun verifyOtpAndCompleteLogin(
        identifier: String,
        otpCode: String,
        requireAdmin: Boolean = false,
        rememberMe: Boolean = SecureSessionManager.isRememberMeEnabled(getApplication()),
        onSuccess: (LoginOutcome) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            SecureSessionManager.setRememberMePreference(getApplication(), rememberMe, identifier)
            when (val res = repository.verifyEmailOtpForSession(identifier, otpCode, requireAdmin, rememberMe)) {
                is RepoResult.Success -> {
                    _currentSession.value = res.data.session
                    _pendingOtpChallenge.value = null
                    _activeRoleOverride.value = null
                    _isAdminPreviewMode.value = false
                    _selectedAdminSection.value = AdminSection.DASHBOARD
                    _cloudAdminStatusConfirmed.value = null
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    recomputeAdminSwitchStates()
                    repository.startBannerRealtimeSync(res.data.isAdminPortalEligible)
                    refreshOwnProfileFromFirebase()
                    if (res.data.isAdminPortalEligible) {
                        refreshCloudAdminVerification()
                    }
                    onSuccess(res.data)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun verifyOtpForPasswordReset(
        email: String,
        otpCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.verifyEmailOtpOnly(email, otpCode)) {
                is RepoResult.Success -> {
                    _pendingOtpChallenge.value = null
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onSuccess()
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun resetPasswordAfterOtp(
        email: String,
        newPassword: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.resetPasswordAfterOtpVerified(email, newPassword)) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onSuccess(res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun logoutAdmin() {
        SecureSessionManager.clearSession(getApplication())
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            }
        }
        _currentSession.value = null
        _pendingOtpChallenge.value = null
        _activeRoleOverride.value = null
        _isAdminPreviewMode.value = false
        _cloudAdminStatusConfirmed.value = null
        _selectedAdminSection.value = AdminSection.DASHBOARD
        recomputeAdminSwitchStates()
    }

    fun createUser(
        fullName: String,
        email: String,
        username: String,
        password: String,
        mobile: String,
        role: AdminRole,
        isSelfSignUp: Boolean = false,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.registerNewUser(
                fullName = fullName,
                email = email,
                username = username,
                rawPassword = password,
                mobile = mobile,
                role = role,
                actorUsername = if (isSelfSignUp) "self_signup" else effectiveUsername(),
                actorRole = if (isSelfSignUp) AdminRole.SUPER_ADMIN else effectiveRole()
            )
            when (res) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun updateUserProfile(
        targetUserId: Int,
        name: String,
        email: String,
        mobile: String,
        subscriptionPlan: String,
        notes: String,
        newPassword: String? = null
    ) {
        viewModelScope.launch {
            when (val res = repository.updateUserProfile(
                effectiveUsername(),
                effectiveRole(),
                targetUserId,
                name,
                email,
                mobile,
                subscriptionPlan,
                notes,
                newPassword
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun setUserStatus(targetUserId: Int, status: AccountStatus, reason: String) {
        viewModelScope.launch {
            when (val res = repository.setUserStatus(
                effectiveUsername(),
                effectiveRole(),
                targetUserId,
                status,
                reason
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun changeUserRole(targetUserId: Int, newRole: AdminRole) {
        viewModelScope.launch {
            when (val res = repository.changeUserRole(
                effectiveUsername(),
                effectiveRole(),
                targetUserId,
                newRole
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun updateUserCustomPermissions(targetUserId: Int, customPermissions: Set<AdminPermission>) {
        viewModelScope.launch {
            when (val res = repository.updateUserCustomPermissions(
                effectiveUsername(),
                effectiveRole(),
                targetUserId,
                customPermissions
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun deleteUser(targetUserId: Int, hardDelete: Boolean = false) {
        viewModelScope.launch {
            when (val res = repository.deleteUserSecurely(
                effectiveUsername(),
                effectiveRole(),
                targetUserId,
                hardDelete
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun deleteOwnAccountPermanently(
        usernameOrIdentifier: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.deleteOwnAccountPermanently(usernameOrIdentifier, _currentSession.value)) {
                is RepoResult.Success -> {
                    _currentSession.value = null
                    _pendingOtpChallenge.value = null
                    _activeRoleOverride.value = null
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onSuccess(res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onError(res.error)
                }
            }
        }
    }

    fun dispatchNotification(
        title: String,
        message: String,
        audience: String,
        targetUsername: String = "",
        category: String = "SYSTEM",
        isTest: Boolean = false,
        isScheduled: Boolean = false,
        scheduledOffsetMins: Int = 0
    ) {
        viewModelScope.launch {
            when (val res = repository.sendNotification(
                effectiveUsername(),
                effectiveRole(),
                title,
                message,
                audience,
                targetUsername,
                category,
                isTest,
                isScheduled,
                scheduledOffsetMins
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun publishRemoteConfig(config: RemoteConfigEntity, summary: String) {
        viewModelScope.launch {
            when (val res = repository.publishRemoteConfig(
                effectiveUsername(),
                effectiveRole(),
                config,
                summary
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun rollbackConfig(history: ConfigHistoryEntity) {
        viewModelScope.launch {
            when (val res = repository.rollbackConfigHistory(
                effectiveUsername(),
                effectiveRole(),
                history
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun currentBannerUserContext(usernameOverride: String = ""): BannerUserContext {
        val session = _currentSession.value
        val uname = usernameOverride.ifBlank { session?.username ?: "User" }
        val userRecord = users.value.find {
            it.username.equals(uname, ignoreCase = true) ||
                (!session?.email.isNullOrBlank() && it.email.equals(session?.email, ignoreCase = true))
        }
        return BannerUserContext(
            userId = session?.userId?.toString() ?: userRecord?.id?.toString() ?: uname,
            username = userRecord?.username ?: uname,
            email = session?.email ?: userRecord?.email ?: "",
            role = session?.role ?: userRecord?.role ?: AdminRole.USER.name,
            loginCount = userRecord?.loginCount ?: 1,
            accountCreatedAtMillis = userRecord?.timestamp ?: 0L,
            dismissedAtMillisByBannerKey = repository.getDismissedBannerMap(uname),
            nowMillis = System.currentTimeMillis()
        )
    }

    fun startBannerRealtimeSync(isAdminUser: Boolean) {
        repository.startBannerRealtimeSync(isAdminUser)
    }

    fun saveBanner(banner: BannerContentEntity, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            when (val res = repository.saveBanner(effectiveUsername(), effectiveRole(), banner)) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun duplicateBanner(banner: BannerContentEntity) {
        viewModelScope.launch {
            when (val res = repository.duplicateBanner(effectiveUsername(), effectiveRole(), banner)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun publishBanner(banner: BannerContentEntity, makePublic: Boolean = true) {
        viewModelScope.launch {
            when (val res = repository.publishBanner(effectiveUsername(), effectiveRole(), banner, makePublic)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun unpublishBanner(banner: BannerContentEntity) {
        viewModelScope.launch {
            when (val res = repository.unpublishBanner(effectiveUsername(), effectiveRole(), banner)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun reviewClinicalTipBanner(banner: BannerContentEntity, approved: Boolean) {
        viewModelScope.launch {
            when (val res = repository.reviewClinicalTipBanner(effectiveUsername(), effectiveRole(), banner, approved)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun deleteBanner(id: Int) {
        viewModelScope.launch {
            when (val res = repository.deleteBanner(effectiveUsername(), effectiveRole(), id)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun recordBannerImpression(banner: BannerContentEntity, username: String, screenSlot: String) {
        viewModelScope.launch {
            repository.recordBannerImpression(banner, username, screenSlot)
        }
    }

    fun recordBannerClick(banner: BannerContentEntity, username: String, screenSlot: String) {
        viewModelScope.launch {
            repository.recordBannerClick(banner, username, screenSlot)
        }
    }

    fun dismissBannerForUser(banner: BannerContentEntity, username: String, screenSlot: String) {
        viewModelScope.launch {
            repository.dismissBanner(banner, username, screenSlot)
        }
    }

    fun updateTicket(
        ticket: SupportTicketEntity,
        newStatus: String,
        reply: String,
        assignedTo: String,
        sendResolutionNotification: Boolean = false,
        customNotificationMessage: String = ""
    ) {
        viewModelScope.launch {
            when (val res = repository.updateSupportTicket(
                effectiveUsername(),
                effectiveRole(),
                ticket,
                newStatus,
                reply,
                assignedTo,
                sendResolutionNotification,
                customNotificationMessage
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun submitUserTicket(
        username: String,
        email: String,
        subject: String,
        message: String,
        category: String = "APP_FEEDBACK",
        priority: String = "MEDIUM"
    ) {
        viewModelScope.launch {
            when (val res = repository.createSupportTicketFromUser(username, email, subject, message, category, priority)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun recordActivity(actionType: String, targetEntity: String, details: String, statusToast: String? = null) {
        viewModelScope.launch {
            repository.recordAdminOrUserActivity(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole().name,
                actionType = actionType,
                targetEntity = targetEntity,
                details = details
            )
            if (!statusToast.isNullOrBlank()) {
                _statusMessage.value = statusToast
            }
        }
    }

    fun updateSubscription(sub: SubscriptionRecordEntity, plan: String, amount: Double, status: String) {
        viewModelScope.launch {
            when (val res = repository.updateSubscription(
                effectiveUsername(),
                effectiveRole(),
                sub,
                plan,
                amount,
                status
            )) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun createBackup(label: String) {
        viewModelScope.launch {
            when (val res = repository.createDatabaseBackup(effectiveUsername(), effectiveRole(), label)) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun restoreBackup(backup: BackupSnapshotEntity) {
        viewModelScope.launch {
            when (val res = repository.restoreFromBackup(effectiveUsername(), effectiveRole(), backup)) {
                is RepoResult.Success -> _statusMessage.value = res.data
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun runAiAdminTool(toolType: String) {
        val currentUsers = users.value
        val currentTickets = tickets.value
        val currentLogs = auditLogs.value
        val currentConfig = remoteConfig.value ?: RemoteConfigEntity()

        _aiGeneratedOutput.value = when (toolType) {
            "SECURITY_AUDIT" -> {
                val failedLogins = loginHistory.value.count { !it.success }
                val criticalLogs = currentLogs.count { it.severity == "CRITICAL" || it.severity == "WARNING" }
                val no2faAdmins = currentUsers.count { it.role != AdminRole.USER.name && !it.twoFactorEnabled }
                """
                🔒 AI Security & Compliance Assessment:
                • Analyzed ${currentLogs.size} audit trail events and ${loginHistory.value.size} authentication records.
                • Failed Login Attempts: $failedLogins | Elevated Security Events: $criticalLogs.
                • Staff 2FA Coverage: $no2faAdmins staff account(s) currently have 2FA disabled.
                • Recommendation: Enable 'Enforce 2FA for Admins' in Remote Security Policy and keep Session Timeout <= ${currentConfig.sessionTimeoutMinutes} mins.
                """.trimIndent()
            }
            "TICKET_TRIAGE" -> {
                val openCount = currentTickets.count { it.status == "OPEN" }
                val highPriority = currentTickets.filter { it.priority == "HIGH" || it.priority == "CRITICAL" }
                """
                🩺 AI Support Triage & Suggested Response:
                • Open Queue: $openCount ticket(s), ${highPriority.size} flagged High/Critical priority.
                • Top Issue Pattern: ESP32 servo jitter above 150° during high-gain EMG calibration.
                • Draft Reply for Ticket #1: "Hello! We recommend lowering Max Grip Force Limit to 12.5N in Remote Config or recalibrating index servo PWM deadband in Firmware v2.4."
                """.trimIndent()
            }
            "NOTIFICATION_DRAFT" -> {
                """
                📣 AI Broadcast Copywriter:
                • Suggested Title: "NeuroGrip v${currentConfig.recommendedVersionName} Calibration Boost"
                • Suggested Body: "Your prosthetic telemetry engine now supports 150ms low-latency EEG lock. Open Gesture Studio today to complete your 10-minute recovery session!"
                • Optimal Audience: PRO_USERS (${currentUsers.count { it.subscriptionPlan.contains("Pro", true) }} active recipients)
                """.trimIndent()
            }
            else -> {
                val activeCount = currentUsers.count { it.status == AccountStatus.ACTIVE.name }
                val mrr = subscriptions.value.filter { it.status == "ACTIVE" }.sumOf { it.amountUsd }
                """
                📊 AI Executive Platform Summary:
                • Active Accounts: $activeCount / ${currentUsers.size} total registered users.
                • Monthly Recurring Revenue (MRR): $${"%.2f".format(mrr)} USD.
                • Config Version: v${currentConfig.configVersion} (Maintenance Mode: ${if (currentConfig.maintenanceMode) "ON" else "OFF"}).
                • System Health: All 5 robotic finger telemetry channels operating within nominal thermal & latency bounds.
                """.trimIndent()
            }
        }
    }

    // --- Cloud Firestore Database Management Operations ---

    fun clearFirestoreError() {
        _firestoreError.value = null
    }

    fun clearExportedJson() {
        _exportedCollectionJson.value = null
    }

    fun loadFirestoreCollection(
        collectionPath: String,
        searchQuery: String = "",
        fieldFilterKey: String = "",
        fieldFilterValue: String = "",
        pageIndex: Int = 0,
        pageSize: Int = 10
    ) {
        viewModelScope.launch {
            _firestoreLoading.value = true
            _firestoreError.value = null
            when (val res = repository.browseFirestoreCollection(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                collectionPath = collectionPath,
                searchQuery = searchQuery,
                fieldFilterKey = fieldFilterKey,
                fieldFilterValue = fieldFilterValue,
                pageIndex = pageIndex,
                pageSize = pageSize
            )) {
                is RepoResult.Success -> {
                    _firestorePageState.value = res.data
                    _firestoreLoading.value = false
                }
                is RepoResult.Error -> {
                    _firestoreError.value = res.error
                    _statusMessage.value = res.error
                    _firestoreLoading.value = false
                }
            }
        }
    }

    fun upsertFirestoreDocument(
        collectionPath: String,
        documentId: String,
        rawFieldValues: Map<String, String>,
        isCreate: Boolean,
        currentSearch: String = "",
        currentFilterKey: String = "",
        currentFilterVal: String = "",
        currentPage: Int = 0,
        currentPageSize: Int = 10,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _firestoreLoading.value = true
            _firestoreError.value = null
            when (val res = repository.upsertFirestoreDocumentFromAdmin(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                collectionPath = collectionPath,
                documentId = documentId,
                rawFieldValues = rawFieldValues,
                isCreate = isCreate
            )) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    loadFirestoreCollection(
                        collectionPath = collectionPath,
                        searchQuery = currentSearch,
                        fieldFilterKey = currentFilterKey,
                        fieldFilterValue = currentFilterVal,
                        pageIndex = currentPage,
                        pageSize = currentPageSize
                    )
                    onComplete(true)
                }
                is RepoResult.Error -> {
                    _firestoreError.value = res.error
                    _statusMessage.value = res.error
                    _firestoreLoading.value = false
                    onComplete(false)
                }
            }
        }
    }

    fun deleteFirestoreDocument(
        collectionPath: String,
        documentId: String,
        currentSearch: String = "",
        currentFilterKey: String = "",
        currentFilterVal: String = "",
        currentPage: Int = 0,
        currentPageSize: Int = 10
    ) {
        viewModelScope.launch {
            _firestoreLoading.value = true
            _firestoreError.value = null
            when (val res = repository.deleteFirestoreDocumentFromAdmin(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                collectionPath = collectionPath,
                documentId = documentId
            )) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    loadFirestoreCollection(
                        collectionPath = collectionPath,
                        searchQuery = currentSearch,
                        fieldFilterKey = currentFilterKey,
                        fieldFilterValue = currentFilterVal,
                        pageIndex = currentPage,
                        pageSize = currentPageSize
                    )
                }
                is RepoResult.Error -> {
                    _firestoreError.value = res.error
                    _statusMessage.value = res.error
                    _firestoreLoading.value = false
                }
            }
        }
    }

    fun exportFirestoreCollection(collectionPath: String) {
        viewModelScope.launch {
            _firestoreLoading.value = true
            when (val res = repository.exportFirestoreCollectionJson(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                collectionPath = collectionPath
            )) {
                is RepoResult.Success -> {
                    _exportedCollectionJson.value = res.data
                    _statusMessage.value = res.message
                    _firestoreLoading.value = false
                }
                is RepoResult.Error -> {
                    _firestoreError.value = res.error
                    _statusMessage.value = res.error
                    _firestoreLoading.value = false
                }
            }
        }
    }

    fun importFirestoreCollection(
        collectionPath: String,
        rawJson: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _firestoreLoading.value = true
            _firestoreError.value = null
            when (val res = repository.importFirestoreCollectionJson(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                collectionPath = collectionPath,
                rawJsonPayload = rawJson
            )) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    loadFirestoreCollection(collectionPath)
                    onComplete(true)
                }
                is RepoResult.Error -> {
                    _firestoreError.value = res.error
                    _statusMessage.value = res.error
                    _firestoreLoading.value = false
                    onComplete(false)
                }
            }
        }
    }

    fun hasPermission(permission: AdminPermission): Boolean {
        val uname = effectiveUsername()
        val userRecord = users.value.firstOrNull {
            it.username.equals(uname, ignoreCase = true) || it.email.equals(uname, ignoreCase = true)
        }
        return RolePermissionPolicy.hasPermission(
            effectiveRole(),
            permission,
            userRecord?.customPermissions ?: ""
        )
    }

    fun setGoogleSignInInProgress(inProgress: Boolean) {
        _isGoogleSignInInProgress.value = inProgress
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        rememberMe: Boolean = SecureSessionManager.isRememberMeEnabled(getApplication()),
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onOutcome: ((LoginOutcome) -> Unit)? = null
    ) {
        if (idToken.isBlank()) {
            _isGoogleSignInInProgress.value = false
            val msg = "Google Sign-In was cancelled or returned an empty token."
            _statusMessage.value = msg
            onError(msg)
            return
        }
        try {
            _isLoading.value = true
            _isGoogleSignInInProgress.value = true
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val fbUser = result.user
                    val email = fbUser?.email?.trim()?.lowercase() ?: ""
                    val displayName = fbUser?.displayName?.ifBlank { email.substringBefore("@") } ?: "Google User"
                    viewModelScope.launch {
                        if (fbUser != null && email.isNotBlank()) {
                            SecureSessionManager.setRememberMePreference(getApplication(), rememberMe, email)
                            // Fast path: authenticate locally & issue session immediately without blocking on cloud profile round-trip
                            when (val authRes = repository.authenticateWithGoogle(
                                idToken = idToken,
                                email = email,
                                displayName = displayName,
                                googleUid = fbUser.uid,
                                emailVerified = fbUser.isEmailVerified || email.contains("@"),
                                rememberMe = rememberMe
                            )) {
                                is RepoResult.Success -> {
                                    _currentSession.value = authRes.data.session
                                    _pendingOtpChallenge.value = null
                                    _activeRoleOverride.value = null
                                    _isAdminPreviewMode.value = false
                                    _selectedAdminSection.value = AdminSection.DASHBOARD
                                    _cloudAdminStatusConfirmed.value = null
                                    _isLoading.value = false
                                    _isGoogleSignInInProgress.value = false
                                    _statusMessage.value = authRes.message
                                    recomputeAdminSwitchStates()
                                    repository.startBannerRealtimeSync(authRes.data.isAdminPortalEligible)
                                    if (authRes.data.isAdminPortalEligible) {
                                        refreshCloudAdminVerification()
                                    }
                                    // Background non-blocking Firestore user profile sync
                                    viewModelScope.launch {
                                        runCatching {
                                            val existingProfile = repository.firestoreRepo?.getUserProfileById(fbUser.uid)?.getOrNull()
                                            if (existingProfile == null) {
                                                repository.firestoreRepo?.createOwnUserProfile(
                                                    uid = fbUser.uid,
                                                    username = authRes.data.user.username,
                                                    name = displayName,
                                                    email = email,
                                                    photoUrl = authRes.data.user.photoUrl.takeIf { it.startsWith("https://", ignoreCase = true) },
                                                    photoUpdatedAt = authRes.data.user.photoUpdatedAt.takeIf { it > 0L }
                                                )
                                            } else {
                                                val synced = repository.syncCurrentFirebaseUserProfile(_currentSession.value)
                                                if (synced != null) {
                                                    _currentSession.value = _currentSession.value?.copy(
                                                        name = synced.name,
                                                        photoUrl = synced.photoUrl,
                                                        photoUpdatedAt = synced.photoUpdatedAt
                                                    )
                                                }
                                            }
                                            if (authRes.data.isAdminPortalEligible) {
                                                repository.syncRealUsersFromFirebase(
                                                    actorUsername = authRes.data.user.username,
                                                    actorRole = AdminRole.fromString(authRes.data.user.role)
                                                )
                                                loadFirestoreCollection("users")
                                            }
                                        }
                                    }
                                    if (onOutcome != null) {
                                        onOutcome(authRes.data)
                                    } else {
                                        onSuccess(authRes.data.user.username)
                                    }
                                }
                                is RepoResult.Error -> {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                    _isLoading.value = false
                                    _isGoogleSignInInProgress.value = false
                                    _statusMessage.value = authRes.error
                                    onError(authRes.error)
                                }
                            }
                        } else {
                            _isLoading.value = false
                            _isGoogleSignInInProgress.value = false
                            val err = "Google account did not provide a verified email address."
                            _statusMessage.value = err
                            onError(err)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    _isLoading.value = false
                    _isGoogleSignInInProgress.value = false
                    val err = e.localizedMessage ?: "Firebase Google authentication failed"
                    _statusMessage.value = err
                    onError(err)
                }
        } catch (e: Exception) {
            _isLoading.value = false
            _isGoogleSignInInProgress.value = false
            val err = e.localizedMessage ?: "Google Sign-In error"
            _statusMessage.value = err
            onError(err)
        }
    }

    fun deleteNotification(notificationId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.deleteNotification(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                notificationId = notificationId
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                }
            }
        }
    }

    fun syncRealUsersFromFirebase() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.syncRealUsersFromFirebase(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole()
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    loadFirestoreCollection("users")
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                }
            }
        }
    }

    fun saveAdminDigitalSignature(
        signerName: String,
        signerEmail: String = "hj195344@gmail.com",
        signerTitle: String = "Chief Platform & Clinical Security Administrator",
        signatureText: String = "",
        signatureScript: String = "",
        signatureImageBase64: String = "",
        signatureImageUri: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val role = effectiveRole()
            val result = AdminDigitalSignatureManager.saveSignature(
                context = getApplication(),
                actorRole = role,
                signerName = signerName,
                signerEmail = signerEmail,
                signerTitle = signerTitle,
                signatureText = signatureText.ifBlank { signatureScript },
                signatureScript = signatureScript,
                signatureImageBase64 = signatureImageBase64,
                signatureImageUri = signatureImageUri
            )
            result.onSuccess { saved ->
                _adminSignatureConfig.value = saved
                val msg = "Admin Digital Signature saved & verified (SHA-256: ${saved.signatureDigestSha256.take(12)}...)."
                _statusMessage.value = msg
                repository.recordAdminOrUserActivity(
                    actorUsername = effectiveUsername(),
                    actorRole = role.name,
                    actionType = "ADMIN_DIGITAL_SIGNATURE_UPDATED",
                    targetEntity = "security:digital_signature",
                    details = "Updated Admin Digital Signature for ${saved.signerName} (${saved.signerEmail}), digest=${saved.signatureDigestSha256}"
                )
                onComplete(true, msg)
            }.onFailure { err ->
                val msg = err.message ?: "Failed to save Admin Digital Signature."
                _statusMessage.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun recordGeneratedReport(record: GeneratedReportRecord) {
        viewModelScope.launch {
            GeneratedReportStore.saveReport(getApplication(), record)
            _generatedReports.value = GeneratedReportStore.loadAllReports(getApplication())
            repository.recordAdminOrUserActivity(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole().name,
                actionType = "ADMIN_USER_REPORT_GENERATED",
                targetEntity = "report:${record.reportId}",
                details = "Generated ${record.reportType} activity report for ${record.targetUserFullName} (${record.targetUserEmail}) [${record.startDate} to ${record.endDate}] on ${record.generatedDateDisplay}"
            )
            _statusMessage.value = "Generated & saved ${record.reportType} report for ${record.targetUserFullName} (${record.generatedDateDisplay})."
        }
    }

    fun searchGeneratedReports(userQuery: String, dateQuery: String): List<GeneratedReportRecord> {
        return GeneratedReportStore.searchReports(getApplication(), userQuery, dateQuery)
    }

    // --- Super Admin System Configuration Operations ---

    fun clearSystemConfigError() {
        _systemConfigError.value = null
    }

    fun getEnvironmentDiagnostics(): List<EnvVarDiagnosticDto> {
        return repository.getEnvironmentDiagnostics()
    }

    fun validateDraftSystemConfig(candidate: SystemConfigRuntimeDto): String? {
        return repository.validateSystemConfig(candidate)
    }

    fun saveSystemConfiguration(
        newConfig: SystemConfigRuntimeDto,
        changeNote: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _systemConfigError.value = null
            when (val res = repository.saveSystemConfiguration(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                newConfig = newConfig,
                changeNote = changeNote
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _systemConfigError.value = res.error
                    _statusMessage.value = res.error
                    onComplete(false)
                }
            }
        }
    }

    fun rollbackSystemConfiguration(historyItem: ConfigHistoryEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            _systemConfigError.value = null
            when (val res = repository.rollbackSystemConfiguration(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                historyItem = historyItem
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _systemConfigError.value = res.error
                    _statusMessage.value = res.error
                }
            }
        }
    }

    fun rotateSuperAdminCredentials(
        currentPassword: String,
        newPassword: String,
        displayName: String,
        email: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _systemConfigError.value = null
            when (val res = repository.rotateSuperAdminCredentials(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                currentPassword = currentPassword,
                newPassword = newPassword,
                newDisplayName = displayName,
                newEmail = email
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _systemConfigError.value = res.error
                    _statusMessage.value = res.error
                    onComplete(false)
                }
            }
        }
    }

    fun testOtpDispatch(identifier: String) {
        viewModelScope.launch {
            when (val res = repository.simulateOtpDispatch(identifier)) {
                is RepoResult.Success -> _statusMessage.value = res.data
                is RepoResult.Error -> {
                    _systemConfigError.value = res.error
                    _statusMessage.value = res.error
                }
            }
        }
    }

    // --- Real-Time Server-Driven UI (SDUI) Operations ---

    fun resolveSduiImageModel(url: String): Any {
        return repository.sduiRepo.resolveImageModel(url)
    }

    fun refreshSduiFromServer() {
        viewModelScope.launch {
            when (val res = repository.refreshSduiNow()) {
                is RepoResult.Success -> _statusMessage.value = res.message
                is RepoResult.Error -> _statusMessage.value = res.error
            }
        }
    }

    fun publishSduiConfig(
        draft: SduiDashboardConfig,
        summary: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.publishSduiConfig(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                draftConfig = draft,
                changeSummary = summary
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun resetSduiToBaseline(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        publishSduiConfig(
            draft = SduiDashboardConfig.baselineDefault(),
            summary = "Restored NeuroGrip default baseline UI",
            onComplete = onComplete
        )
    }

    fun rollbackSduiConfig(historyItem: SduiConfigHistoryEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.rollbackSduiConfig(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                historyItem = historyItem
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                }
            }
        }
    }

    // --- Smart User Profile Avatar & Profile Sync Operations ---

    fun refreshOwnProfileFromFirebase() {
        viewModelScope.launch {
            val synced = repository.syncCurrentFirebaseUserProfile(_currentSession.value)
            if (synced != null) {
                _currentSession.value?.let { sess ->
                    if (sess.username.equals(synced.username, ignoreCase = true) ||
                        sess.email.equals(synced.email, ignoreCase = true)
                    ) {
                        _currentSession.value = sess.copy(
                            name = synced.name,
                            photoUrl = synced.photoUrl,
                            photoUpdatedAt = synced.photoUpdatedAt
                        )
                    }
                }
            }
        }
    }

    fun saveValidatedProfilePhoto(
        validated: ValidatedProfilePhoto,
        identifierHint: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isProfilePhotoUploading.value = true
            when (val res = repository.uploadAndSaveOwnProfilePhoto(
                validated = validated,
                session = _currentSession.value,
                identifierHint = identifierHint
            )) {
                is RepoResult.Success -> {
                    val (_, updatedSession) = res.data
                    if (updatedSession != null) {
                        _currentSession.value = updatedSession
                    }
                    _isProfilePhotoUploading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isProfilePhotoUploading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun removeOwnProfilePhoto(
        identifierHint: String? = null,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isProfilePhotoUploading.value = true
            when (val res = repository.removeOwnProfilePhoto(
                session = _currentSession.value,
                identifierHint = identifierHint
            )) {
                is RepoResult.Success -> {
                    val (_, updatedSession) = res.data
                    if (updatedSession != null) {
                        _currentSession.value = updatedSession
                    }
                    _isProfilePhotoUploading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isProfilePhotoUploading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun updateOwnProfileDetails(
        identifierHint: String?,
        newName: String,
        newMobile: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            when (val res = repository.updateOwnProfileDetails(
                session = _currentSession.value,
                identifierHint = identifierHint,
                newName = newName,
                newMobile = newMobile
            )) {
                is RepoResult.Success -> {
                    val (_, updatedSession) = res.data
                    if (updatedSession != null) {
                        _currentSession.value = updatedSession
                    }
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    // --- App Branding & Identity Management (Super Admin Only) ---

    fun isSuperAdminAccount(): Boolean {
        return effectiveRole() == AdminRole.SUPER_ADMIN
    }

    fun refreshAppBrandingNow(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            when (val res = repository.refreshAppBrandingNow()) {
                is RepoResult.Success -> {
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun publishAppBranding(
        newAppName: String,
        stagedLogo: ValidatedBrandingLogo? = null,
        removeLogo: Boolean = false,
        notifyUsers: Boolean = true,
        summaryNote: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.publishAppBrandingChanges(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                newAppName = newAppName,
                stagedLogo = stagedLogo,
                removeLogo = removeLogo,
                notifyUsers = notifyUsers,
                summaryNote = summaryNote
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun restoreDefaultAppBranding(
        notifyUsers: Boolean = true,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.restoreDefaultAppBranding(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                notifyUsers = notifyUsers
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }

    fun publishLauncherBrandingReleaseAdvisory(
        recommendedVersionName: String,
        releaseSummary: String,
        forceUpdate: Boolean,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.publishLauncherBrandingReleaseAdvisory(
                actorUsername = effectiveUsername(),
                actorRole = effectiveRole(),
                recommendedVersionName = recommendedVersionName,
                releaseSummary = releaseSummary,
                forceUpdate = forceUpdate
            )) {
                is RepoResult.Success -> {
                    _isLoading.value = false
                    _statusMessage.value = res.message
                    onComplete(true, res.message)
                }
                is RepoResult.Error -> {
                    _isLoading.value = false
                    _statusMessage.value = res.error
                    onComplete(false, res.error)
                }
            }
        }
    }
}
