package com.example

import androidx.compose.ui.graphics.Color
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object SduiAllowlists {
    const val ACTIVE_CONFIG_KEY = "dashboard_active"
    const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_SUPPORTED_SCHEMA_VERSION = 2

    val ALLOWED_SECTION_IDS = setOf(
        "promo_banner",
        "hero",
        "ai_assistant",
        "permissions",
        "control_modes",
        "bio_signals",
        "prosthetic_status",
        "quick_actions"
    )

    val ALLOWED_ROUTES = setOf(
        "none",
        "hand_control",
        "ai_assistant",
        "reports",
        "gesture_studio",
        "permissions",
        "settings",
        "notifications",
        "profile"
    )

    val ALLOWED_ICON_KEYS = setOf(
        "psychology",
        "antenna",
        "phone",
        "hand",
        "chart",
        "gesture",
        "shield",
        "sensors",
        "settings",
        "notifications"
    )

    val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    val UNSAFE_CONTENT_PATTERNS = listOf(
        "<script",
        "</script",
        "javascript:",
        "vbscript:",
        "onerror=",
        "onload=",
        "eval("
    )
}

data class SduiThemeTokens(
    val primaryBlueHex: String = "#5A81A5",
    val buttonBlueHex: String = "#4A7499",
    val textGrayHex: String = "#64748B",
    val darkTextHex: String = "#1E293B",
    val lightBgHex: String = "#F5F7FA",
    val cardBgHex: String = "#FFFFFF",
    val greenColorHex: String = "#10B981",
    val borderLightHex: String = "#E2E8F0",
    val cornerRadiusDp: Int = 24,
    val sectionSpacingDp: Int = 24
)

data class SduiTopBarConfig(
    val brandTitle: String = "NeuroGrip",
    val brandEmoji: String = "\uD83E\uDDE0",
    val onlineLabel: String = "System Online",
    val offlineLabel: String = "System Offline",
    val defaultAvatarUrl: String = "https://i.pravatar.cc/100?img=11"
)

data class SduiHeroSection(
    val visible: Boolean = true,
    val title: String = "NeuroGrip Ready",
    val subtitle: String = "Your interface is calibrated and ready for seamless neural communication.",
    val connectButtonText: String = "Connect ESP32",
    val disconnectButtonText: String = "Disconnect",
    val connectingText: String = "Connecting...",
    val imageUrl: String = "https://images.unsplash.com/photo-1589254065878-42c9da997008?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80",
    val gradientStartHex: String = "#F8FAFC",
    val gradientEndHex: String = "#E2E8F0",
    val titleFontSizeSp: Int = 24
)

data class SduiAiAssistantSection(
    val visible: Boolean = true,
    val headerLabel: String = "AI ASSISTANT",
    val title: String = "AI Assistant",
    val subtitle: String = "I am here to optimize your experience.",
    val ctaText: String = "Engage AI Assistant",
    val ctaRoute: String = "ai_assistant"
)

data class SduiPermissionsSection(
    val visible: Boolean = true,
    val title: String = "Device Permissions & Setup",
    val subtitle: String = "Step 2 of 5: Bluetooth, Location & Sensors",
    val actionText: String = "Manage",
    val actionRoute: String = "permissions"
)

data class SduiControlModeItem(
    val title: String,
    val badgeText: String = "READY",
    val iconKey: String = "psychology",
    val route: String = "none"
)

data class SduiControlModesSection(
    val visible: Boolean = true,
    val headerLabel: String = "CONTROL MODES",
    val items: List<SduiControlModeItem> = defaultControlModes()
) {
    companion object {
        fun defaultControlModes(): List<SduiControlModeItem> = listOf(
            SduiControlModeItem(
                title = "Brain Control (BCI)",
                badgeText = "READY",
                iconKey = "psychology",
                route = "none"
            ),
            SduiControlModeItem(
                title = "Muscle Control (EMG)",
                badgeText = "READY",
                iconKey = "antenna",
                route = "none"
            ),
            SduiControlModeItem(
                title = "App Control",
                badgeText = "READY",
                iconKey = "phone",
                route = "hand_control"
            )
        )
    }
}

data class SduiBioSignalsSection(
    val visible: Boolean = true,
    val headerLabel: String = "Live Bio-Signals",
    val actionText: String = "View Analysis",
    val eegLabel: String = "EEG Attention",
    val emgLabel: String = "EMG Strength"
)

data class SduiProstheticStatusSection(
    val visible: Boolean = true,
    val imageUrl: String = "https://images.unsplash.com/photo-1535378917042-10a22c95931a?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80",
    val title: String = "Prosthetic Status",
    val therapyGoalLabel: String = "Therapy Goal",
    val quoteText: String = "\"Ready to beat yesterday's score, Tarun?\nStart a 5-minute gesture drill.\""
)

data class SduiQuickActionItem(
    val label: String,
    val iconKey: String,
    val route: String
)

data class SduiQuickActionsSection(
    val visible: Boolean = true,
    val headerLabel: String = "Quick Actions",
    val actions: List<SduiQuickActionItem> = defaultQuickActions()
) {
    companion object {
        fun defaultQuickActions(): List<SduiQuickActionItem> = listOf(
            SduiQuickActionItem(label = "Hand Control", iconKey = "hand", route = "hand_control"),
            SduiQuickActionItem(label = "Reports", iconKey = "chart", route = "reports"),
            SduiQuickActionItem(label = "Gesture Studio", iconKey = "gesture", route = "gesture_studio")
        )
    }
}

data class SduiPromoBannerSection(
    val enabled: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val ctaText: String = "Open",
    val ctaRoute: String = "hand_control",
    val imageUrl: String = "",
    val backgroundHex: String = "#EFF6FF",
    val borderHex: String = "#BFDBFE",
    val textColorHex: String = "#1E3A8A"
)

data class SduiDashboardConfig(
    val configKey: String = SduiAllowlists.ACTIVE_CONFIG_KEY,
    val schemaVersion: Int = 1,
    val configVersion: Int = 1,
    val updatedBy: String = "system",
    val updatedAt: Long = 0L,
    val changeSummary: String = "Initial baseline dashboard UI",
    val sectionsOrder: List<String> = DEFAULT_SECTIONS_ORDER,
    val theme: SduiThemeTokens = SduiThemeTokens(),
    val topBar: SduiTopBarConfig = SduiTopBarConfig(),
    val promoBanner: SduiPromoBannerSection = SduiPromoBannerSection(),
    val hero: SduiHeroSection = SduiHeroSection(),
    val aiAssistant: SduiAiAssistantSection = SduiAiAssistantSection(),
    val permissions: SduiPermissionsSection = SduiPermissionsSection(),
    val controlModes: SduiControlModesSection = SduiControlModesSection(),
    val bioSignals: SduiBioSignalsSection = SduiBioSignalsSection(),
    val prostheticStatus: SduiProstheticStatusSection = SduiProstheticStatusSection(),
    val quickActions: SduiQuickActionsSection = SduiQuickActionsSection()
) {
    fun requiredImageUrls(): List<String> {
        return buildList {
            if (hero.visible && hero.imageUrl.isNotBlank()) add(hero.imageUrl.trim())
            if (prostheticStatus.visible && prostheticStatus.imageUrl.isNotBlank()) add(prostheticStatus.imageUrl.trim())
            if (promoBanner.enabled && promoBanner.imageUrl.isNotBlank()) add(promoBanner.imageUrl.trim())
            if (topBar.defaultAvatarUrl.isNotBlank()) add(topBar.defaultAvatarUrl.trim())
        }.distinct()
    }

    companion object {
        val DEFAULT_SECTIONS_ORDER = listOf(
            "promo_banner",
            "hero",
            "ai_assistant",
            "permissions",
            "control_modes",
            "bio_signals",
            "prosthetic_status",
            "quick_actions"
        )

        fun baselineDefault(): SduiDashboardConfig = SduiDashboardConfig()
    }
}

sealed class SduiValidationResult {
    data class Valid(
        val config: SduiDashboardConfig,
        val normalizedJson: String,
        val sha256Hash: String
    ) : SduiValidationResult()

    data class Invalid(
        val reasons: List<String>
    ) : SduiValidationResult() {
        fun formattedReason(): String = reasons.joinToString("; ")
    }
}

object SduiColorParser {
    fun parseOrDefault(hex: String, fallback: Color): Color {
        val trimmed = hex.trim()
        if (!SduiAllowlists.HEX_COLOR_REGEX.matches(trimmed)) return fallback
        return try {
            val clean = trimmed.removePrefix("#")
            val argb = when (clean.length) {
                6 -> 0xFF000000L or clean.toLong(16)
                8 -> clean.toLong(16)
                else -> return fallback
            }
            Color(argb.toInt())
        } catch (_: Exception) {
            fallback
        }
    }
}

object SduiJsonCodec {
    fun encode(config: SduiDashboardConfig): String {
        val root = JSONObject()
        root.put("configKey", config.configKey)
        root.put("schemaVersion", config.schemaVersion)
        root.put("configVersion", config.configVersion)
        root.put("updatedBy", config.updatedBy)
        root.put("updatedAt", config.updatedAt)
        root.put("changeSummary", config.changeSummary)

        val orderArr = JSONArray()
        config.sectionsOrder.forEach { orderArr.put(it) }
        root.put("sectionsOrder", orderArr)

        root.put("theme", JSONObject().apply {
            put("primaryBlueHex", config.theme.primaryBlueHex)
            put("buttonBlueHex", config.theme.buttonBlueHex)
            put("textGrayHex", config.theme.textGrayHex)
            put("darkTextHex", config.theme.darkTextHex)
            put("lightBgHex", config.theme.lightBgHex)
            put("cardBgHex", config.theme.cardBgHex)
            put("greenColorHex", config.theme.greenColorHex)
            put("borderLightHex", config.theme.borderLightHex)
            put("cornerRadiusDp", config.theme.cornerRadiusDp)
            put("sectionSpacingDp", config.theme.sectionSpacingDp)
        })

        root.put("topBar", JSONObject().apply {
            put("brandTitle", config.topBar.brandTitle)
            put("brandEmoji", config.topBar.brandEmoji)
            put("onlineLabel", config.topBar.onlineLabel)
            put("offlineLabel", config.topBar.offlineLabel)
            put("defaultAvatarUrl", config.topBar.defaultAvatarUrl)
        })

        root.put("promoBanner", JSONObject().apply {
            put("enabled", config.promoBanner.enabled)
            put("title", config.promoBanner.title)
            put("subtitle", config.promoBanner.subtitle)
            put("ctaText", config.promoBanner.ctaText)
            put("ctaRoute", config.promoBanner.ctaRoute)
            put("imageUrl", config.promoBanner.imageUrl)
            put("backgroundHex", config.promoBanner.backgroundHex)
            put("borderHex", config.promoBanner.borderHex)
            put("textColorHex", config.promoBanner.textColorHex)
        })

        root.put("hero", JSONObject().apply {
            put("visible", config.hero.visible)
            put("title", config.hero.title)
            put("subtitle", config.hero.subtitle)
            put("connectButtonText", config.hero.connectButtonText)
            put("disconnectButtonText", config.hero.disconnectButtonText)
            put("connectingText", config.hero.connectingText)
            put("imageUrl", config.hero.imageUrl)
            put("gradientStartHex", config.hero.gradientStartHex)
            put("gradientEndHex", config.hero.gradientEndHex)
            put("titleFontSizeSp", config.hero.titleFontSizeSp)
        })

        root.put("aiAssistant", JSONObject().apply {
            put("visible", config.aiAssistant.visible)
            put("headerLabel", config.aiAssistant.headerLabel)
            put("title", config.aiAssistant.title)
            put("subtitle", config.aiAssistant.subtitle)
            put("ctaText", config.aiAssistant.ctaText)
            put("ctaRoute", config.aiAssistant.ctaRoute)
        })

        root.put("permissions", JSONObject().apply {
            put("visible", config.permissions.visible)
            put("title", config.permissions.title)
            put("subtitle", config.permissions.subtitle)
            put("actionText", config.permissions.actionText)
            put("actionRoute", config.permissions.actionRoute)
        })

        root.put("controlModes", JSONObject().apply {
            put("visible", config.controlModes.visible)
            put("headerLabel", config.controlModes.headerLabel)
            val itemsArr = JSONArray()
            config.controlModes.items.forEach { item ->
                itemsArr.put(JSONObject().apply {
                    put("title", item.title)
                    put("badgeText", item.badgeText)
                    put("iconKey", item.iconKey)
                    put("route", item.route)
                })
            }
            put("items", itemsArr)
        })

        root.put("bioSignals", JSONObject().apply {
            put("visible", config.bioSignals.visible)
            put("headerLabel", config.bioSignals.headerLabel)
            put("actionText", config.bioSignals.actionText)
            put("eegLabel", config.bioSignals.eegLabel)
            put("emgLabel", config.bioSignals.emgLabel)
        })

        root.put("prostheticStatus", JSONObject().apply {
            put("visible", config.prostheticStatus.visible)
            put("imageUrl", config.prostheticStatus.imageUrl)
            put("title", config.prostheticStatus.title)
            put("therapyGoalLabel", config.prostheticStatus.therapyGoalLabel)
            put("quoteText", config.prostheticStatus.quoteText)
        })

        root.put("quickActions", JSONObject().apply {
            put("visible", config.quickActions.visible)
            put("headerLabel", config.quickActions.headerLabel)
            val actionsArr = JSONArray()
            config.quickActions.actions.forEach { act ->
                actionsArr.put(JSONObject().apply {
                    put("label", act.label)
                    put("iconKey", act.iconKey)
                    put("route", act.route)
                })
            }
            put("actions", actionsArr)
        })

        return root.toString()
    }

    fun toFirestoreMap(config: SduiDashboardConfig): Map<String, Any?> {
        val payloadJson = encode(config)
        val checksum = sha256(payloadJson)
        return linkedMapOf(
            "configKey" to config.configKey,
            "schemaVersion" to config.schemaVersion.toLong(),
            "configVersion" to config.configVersion.toLong(),
            "updatedBy" to config.updatedBy,
            "changeSummary" to config.changeSummary,
            "payloadJson" to payloadJson,
            "contentSha256" to checksum,
            "themePrimaryHex" to config.theme.buttonBlueHex,
            "themeBackgroundHex" to config.theme.lightBgHex,
            "heroTitle" to config.hero.title,
            "heroImageUrl" to config.hero.imageUrl,
            "promoEnabled" to config.promoBanner.enabled,
            "sectionsOrder" to config.sectionsOrder
        )
    }

    fun decodeStrictJson(rawJson: String): Result<SduiDashboardConfig> {
        return try {
            if (rawJson.isBlank() || rawJson.length > 65536) {
                return Result.failure(IllegalArgumentException("SDUI JSON payload is empty or exceeds 64KB limit."))
            }
            val root = JSONObject(rawJson)
            if (!root.has("configVersion") || !root.has("schemaVersion")) {
                return Result.failure(IllegalArgumentException("Missing required 'schemaVersion' or 'configVersion'."))
            }
            val defaults = SduiDashboardConfig.baselineDefault()

            val schemaVersionAny = root.opt("schemaVersion")
            val configVersionAny = root.opt("configVersion")
            val schemaVersion = (schemaVersionAny as? Number)?.toInt()
                ?: return Result.failure(IllegalArgumentException("'schemaVersion' must be an integer."))
            val configVersion = (configVersionAny as? Number)?.toInt()
                ?: return Result.failure(IllegalArgumentException("'configVersion' must be an integer."))

            val sectionsOrder = if (root.has("sectionsOrder")) {
                val arr = root.optJSONArray("sectionsOrder")
                    ?: return Result.failure(IllegalArgumentException("'sectionsOrder' must be an array."))
                (0 until arr.length()).map { arr.optString(it, "") }
            } else {
                defaults.sectionsOrder
            }

            val themeObj = root.optJSONObject("theme")
            val theme = if (themeObj != null) {
                SduiThemeTokens(
                    primaryBlueHex = themeObj.optString("primaryBlueHex", defaults.theme.primaryBlueHex),
                    buttonBlueHex = themeObj.optString("buttonBlueHex", defaults.theme.buttonBlueHex),
                    textGrayHex = themeObj.optString("textGrayHex", defaults.theme.textGrayHex),
                    darkTextHex = themeObj.optString("darkTextHex", defaults.theme.darkTextHex),
                    lightBgHex = themeObj.optString("lightBgHex", defaults.theme.lightBgHex),
                    cardBgHex = themeObj.optString("cardBgHex", defaults.theme.cardBgHex),
                    greenColorHex = themeObj.optString("greenColorHex", defaults.theme.greenColorHex),
                    borderLightHex = themeObj.optString("borderLightHex", defaults.theme.borderLightHex),
                    cornerRadiusDp = themeObj.optInt("cornerRadiusDp", defaults.theme.cornerRadiusDp),
                    sectionSpacingDp = themeObj.optInt("sectionSpacingDp", defaults.theme.sectionSpacingDp)
                )
            } else {
                defaults.theme
            }

            val topBarObj = root.optJSONObject("topBar")
            val topBar = if (topBarObj != null) {
                SduiTopBarConfig(
                    brandTitle = topBarObj.optString("brandTitle", defaults.topBar.brandTitle),
                    brandEmoji = topBarObj.optString("brandEmoji", defaults.topBar.brandEmoji),
                    onlineLabel = topBarObj.optString("onlineLabel", defaults.topBar.onlineLabel),
                    offlineLabel = topBarObj.optString("offlineLabel", defaults.topBar.offlineLabel),
                    defaultAvatarUrl = topBarObj.optString("defaultAvatarUrl", defaults.topBar.defaultAvatarUrl)
                )
            } else {
                defaults.topBar
            }

            val promoObj = root.optJSONObject("promoBanner")
            val promoBanner = if (promoObj != null) {
                SduiPromoBannerSection(
                    enabled = promoObj.optBoolean("enabled", defaults.promoBanner.enabled),
                    title = promoObj.optString("title", defaults.promoBanner.title),
                    subtitle = promoObj.optString("subtitle", defaults.promoBanner.subtitle),
                    ctaText = promoObj.optString("ctaText", defaults.promoBanner.ctaText),
                    ctaRoute = promoObj.optString("ctaRoute", defaults.promoBanner.ctaRoute),
                    imageUrl = promoObj.optString("imageUrl", defaults.promoBanner.imageUrl),
                    backgroundHex = promoObj.optString("backgroundHex", defaults.promoBanner.backgroundHex),
                    borderHex = promoObj.optString("borderHex", defaults.promoBanner.borderHex),
                    textColorHex = promoObj.optString("textColorHex", defaults.promoBanner.textColorHex)
                )
            } else {
                defaults.promoBanner
            }

            val heroObj = root.optJSONObject("hero")
            val hero = if (heroObj != null) {
                SduiHeroSection(
                    visible = heroObj.optBoolean("visible", defaults.hero.visible),
                    title = heroObj.optString("title", defaults.hero.title),
                    subtitle = heroObj.optString("subtitle", defaults.hero.subtitle),
                    connectButtonText = heroObj.optString("connectButtonText", defaults.hero.connectButtonText),
                    disconnectButtonText = heroObj.optString("disconnectButtonText", defaults.hero.disconnectButtonText),
                    connectingText = heroObj.optString("connectingText", defaults.hero.connectingText),
                    imageUrl = heroObj.optString("imageUrl", defaults.hero.imageUrl),
                    gradientStartHex = heroObj.optString("gradientStartHex", defaults.hero.gradientStartHex),
                    gradientEndHex = heroObj.optString("gradientEndHex", defaults.hero.gradientEndHex),
                    titleFontSizeSp = heroObj.optInt("titleFontSizeSp", defaults.hero.titleFontSizeSp)
                )
            } else {
                defaults.hero
            }

            val aiObj = root.optJSONObject("aiAssistant")
            val aiAssistant = if (aiObj != null) {
                SduiAiAssistantSection(
                    visible = aiObj.optBoolean("visible", defaults.aiAssistant.visible),
                    headerLabel = aiObj.optString("headerLabel", defaults.aiAssistant.headerLabel),
                    title = aiObj.optString("title", defaults.aiAssistant.title),
                    subtitle = aiObj.optString("subtitle", defaults.aiAssistant.subtitle),
                    ctaText = aiObj.optString("ctaText", defaults.aiAssistant.ctaText),
                    ctaRoute = aiObj.optString("ctaRoute", defaults.aiAssistant.ctaRoute)
                )
            } else {
                defaults.aiAssistant
            }

            val permObj = root.optJSONObject("permissions")
            val permissions = if (permObj != null) {
                SduiPermissionsSection(
                    visible = permObj.optBoolean("visible", defaults.permissions.visible),
                    title = permObj.optString("title", defaults.permissions.title),
                    subtitle = permObj.optString("subtitle", defaults.permissions.subtitle),
                    actionText = permObj.optString("actionText", defaults.permissions.actionText),
                    actionRoute = permObj.optString("actionRoute", defaults.permissions.actionRoute)
                )
            } else {
                defaults.permissions
            }

            val modesObj = root.optJSONObject("controlModes")
            val controlModes = if (modesObj != null) {
                val items = if (modesObj.has("items")) {
                    val arr = modesObj.optJSONArray("items")
                        ?: return Result.failure(IllegalArgumentException("'controlModes.items' must be an array."))
                    (0 until arr.length()).map { i ->
                        val itemObj = arr.getJSONObject(i)
                        SduiControlModeItem(
                            title = itemObj.optString("title", ""),
                            badgeText = itemObj.optString("badgeText", "READY"),
                            iconKey = itemObj.optString("iconKey", "psychology"),
                            route = itemObj.optString("route", "none")
                        )
                    }
                } else {
                    defaults.controlModes.items
                }
                SduiControlModesSection(
                    visible = modesObj.optBoolean("visible", defaults.controlModes.visible),
                    headerLabel = modesObj.optString("headerLabel", defaults.controlModes.headerLabel),
                    items = items
                )
            } else {
                defaults.controlModes
            }

            val bioObj = root.optJSONObject("bioSignals")
            val bioSignals = if (bioObj != null) {
                SduiBioSignalsSection(
                    visible = bioObj.optBoolean("visible", defaults.bioSignals.visible),
                    headerLabel = bioObj.optString("headerLabel", defaults.bioSignals.headerLabel),
                    actionText = bioObj.optString("actionText", defaults.bioSignals.actionText),
                    eegLabel = bioObj.optString("eegLabel", defaults.bioSignals.eegLabel),
                    emgLabel = bioObj.optString("emgLabel", defaults.bioSignals.emgLabel)
                )
            } else {
                defaults.bioSignals
            }

            val prosObj = root.optJSONObject("prostheticStatus")
            val prostheticStatus = if (prosObj != null) {
                SduiProstheticStatusSection(
                    visible = prosObj.optBoolean("visible", defaults.prostheticStatus.visible),
                    imageUrl = prosObj.optString("imageUrl", defaults.prostheticStatus.imageUrl),
                    title = prosObj.optString("title", defaults.prostheticStatus.title),
                    therapyGoalLabel = prosObj.optString("therapyGoalLabel", defaults.prostheticStatus.therapyGoalLabel),
                    quoteText = prosObj.optString("quoteText", defaults.prostheticStatus.quoteText)
                )
            } else {
                defaults.prostheticStatus
            }

            val qaObj = root.optJSONObject("quickActions")
            val quickActions = if (qaObj != null) {
                val actions = if (qaObj.has("actions")) {
                    val arr = qaObj.optJSONArray("actions")
                        ?: return Result.failure(IllegalArgumentException("'quickActions.actions' must be an array."))
                    (0 until arr.length()).map { i ->
                        val actObj = arr.getJSONObject(i)
                        SduiQuickActionItem(
                            label = actObj.optString("label", ""),
                            iconKey = actObj.optString("iconKey", "hand"),
                            route = actObj.optString("route", "hand_control")
                        )
                    }
                } else {
                    defaults.quickActions.actions
                }
                SduiQuickActionsSection(
                    visible = qaObj.optBoolean("visible", defaults.quickActions.visible),
                    headerLabel = qaObj.optString("headerLabel", defaults.quickActions.headerLabel),
                    actions = actions
                )
            } else {
                defaults.quickActions
            }

            Result.success(
                SduiDashboardConfig(
                    configKey = root.optString("configKey", defaults.configKey),
                    schemaVersion = schemaVersion,
                    configVersion = configVersion,
                    updatedBy = root.optString("updatedBy", "admin"),
                    updatedAt = root.optLong("updatedAt", 0L),
                    changeSummary = root.optString("changeSummary", ""),
                    sectionsOrder = sectionsOrder,
                    theme = theme,
                    topBar = topBar,
                    promoBanner = promoBanner,
                    hero = hero,
                    aiAssistant = aiAssistant,
                    permissions = permissions,
                    controlModes = controlModes,
                    bioSignals = bioSignals,
                    prostheticStatus = prostheticStatus,
                    quickActions = quickActions
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed SDUI JSON: ${e.message}"))
        }
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

object SduiSchemaValidator {
    fun validateConfig(config: SduiDashboardConfig): SduiValidationResult {
        val errors = mutableListOf<String>()

        if (config.configKey != SduiAllowlists.ACTIVE_CONFIG_KEY) {
            errors += "Invalid configKey '${config.configKey}'; expected '${SduiAllowlists.ACTIVE_CONFIG_KEY}'."
        }
        if (config.schemaVersion !in SduiAllowlists.MIN_SUPPORTED_SCHEMA_VERSION..SduiAllowlists.MAX_SUPPORTED_SCHEMA_VERSION) {
            errors += "Unsupported schemaVersion=${config.schemaVersion}; supported range is ${SduiAllowlists.MIN_SUPPORTED_SCHEMA_VERSION}..${SduiAllowlists.MAX_SUPPORTED_SCHEMA_VERSION}."
        }
        if (config.configVersion < 1 || config.configVersion > 1_000_000) {
            errors += "Invalid configVersion=${config.configVersion}; must be between 1 and 1,000,000."
        }
        if (config.sectionsOrder.isEmpty()) {
            errors += "sectionsOrder cannot be empty."
        } else {
            val seen = mutableSetOf<String>()
            config.sectionsOrder.forEach { sec ->
                if (sec !in SduiAllowlists.ALLOWED_SECTION_IDS) {
                    errors += "Disallowed section ID '$sec'."
                }
                if (!seen.add(sec)) {
                    errors += "Duplicate section ID '$sec' in sectionsOrder."
                }
            }
        }

        // Theme color and spacing bounds
        validateHex("theme.primaryBlueHex", config.theme.primaryBlueHex, errors)
        validateHex("theme.buttonBlueHex", config.theme.buttonBlueHex, errors)
        validateHex("theme.textGrayHex", config.theme.textGrayHex, errors)
        validateHex("theme.darkTextHex", config.theme.darkTextHex, errors)
        validateHex("theme.lightBgHex", config.theme.lightBgHex, errors)
        validateHex("theme.cardBgHex", config.theme.cardBgHex, errors)
        validateHex("theme.greenColorHex", config.theme.greenColorHex, errors)
        validateHex("theme.borderLightHex", config.theme.borderLightHex, errors)
        validateHex("hero.gradientStartHex", config.hero.gradientStartHex, errors)
        validateHex("hero.gradientEndHex", config.hero.gradientEndHex, errors)
        validateHex("promoBanner.backgroundHex", config.promoBanner.backgroundHex, errors)
        validateHex("promoBanner.borderHex", config.promoBanner.borderHex, errors)
        validateHex("promoBanner.textColorHex", config.promoBanner.textColorHex, errors)

        if (config.theme.cornerRadiusDp !in 4..36) {
            errors += "theme.cornerRadiusDp (${config.theme.cornerRadiusDp}) must be in 4..36."
        }
        if (config.theme.sectionSpacingDp !in 4..48) {
            errors += "theme.sectionSpacingDp (${config.theme.sectionSpacingDp}) must be in 4..48."
        }
        if (config.hero.titleFontSizeSp !in 14..36) {
            errors += "hero.titleFontSizeSp (${config.hero.titleFontSizeSp}) must be in 14..36."
        }

        // Strings & XSS/script protection
        validateSafeText("topBar.brandTitle", config.topBar.brandTitle, required = true, maxLen = 40, errors = errors)
        validateSafeText("topBar.onlineLabel", config.topBar.onlineLabel, required = true, maxLen = 40, errors = errors)
        validateSafeText("topBar.offlineLabel", config.topBar.offlineLabel, required = true, maxLen = 40, errors = errors)
        validateSafeText("updatedBy", config.updatedBy, required = true, maxLen = 80, errors = errors)
        validateSafeText("changeSummary", config.changeSummary, required = false, maxLen = 200, errors = errors)

        validateSafeText("hero.title", config.hero.title, required = config.hero.visible, maxLen = 80, errors = errors)
        validateSafeText("hero.subtitle", config.hero.subtitle, required = config.hero.visible, maxLen = 220, errors = errors)
        validateSafeText("hero.connectButtonText", config.hero.connectButtonText, required = config.hero.visible, maxLen = 40, errors = errors)
        validateSafeText("hero.disconnectButtonText", config.hero.disconnectButtonText, required = config.hero.visible, maxLen = 40, errors = errors)
        validateSafeText("hero.connectingText", config.hero.connectingText, required = config.hero.visible, maxLen = 40, errors = errors)

        validateSafeText("aiAssistant.headerLabel", config.aiAssistant.headerLabel, required = config.aiAssistant.visible, maxLen = 40, errors = errors)
        validateSafeText("aiAssistant.title", config.aiAssistant.title, required = config.aiAssistant.visible, maxLen = 60, errors = errors)
        validateSafeText("aiAssistant.subtitle", config.aiAssistant.subtitle, required = config.aiAssistant.visible, maxLen = 160, errors = errors)
        validateSafeText("aiAssistant.ctaText", config.aiAssistant.ctaText, required = config.aiAssistant.visible, maxLen = 50, errors = errors)
        validateRoute("aiAssistant.ctaRoute", config.aiAssistant.ctaRoute, errors)

        validateSafeText("permissions.title", config.permissions.title, required = config.permissions.visible, maxLen = 80, errors = errors)
        validateSafeText("permissions.subtitle", config.permissions.subtitle, required = config.permissions.visible, maxLen = 160, errors = errors)
        validateSafeText("permissions.actionText", config.permissions.actionText, required = config.permissions.visible, maxLen = 30, errors = errors)
        validateRoute("permissions.actionRoute", config.permissions.actionRoute, errors)

        if (config.promoBanner.enabled) {
            validateSafeText("promoBanner.title", config.promoBanner.title, required = true, maxLen = 80, errors = errors)
            validateSafeText("promoBanner.subtitle", config.promoBanner.subtitle, required = true, maxLen = 180, errors = errors)
            validateSafeText("promoBanner.ctaText", config.promoBanner.ctaText, required = true, maxLen = 32, errors = errors)
        }
        validateRoute("promoBanner.ctaRoute", config.promoBanner.ctaRoute, errors)

        validateSafeText("controlModes.headerLabel", config.controlModes.headerLabel, required = config.controlModes.visible, maxLen = 40, errors = errors)
        if (config.controlModes.items.isEmpty() || config.controlModes.items.size > 6) {
            errors += "controlModes.items count (${config.controlModes.items.size}) must be between 1 and 6."
        }
        config.controlModes.items.forEachIndexed { idx, item ->
            validateSafeText("controlModes.items[$idx].title", item.title, required = true, maxLen = 60, errors = errors)
            validateSafeText("controlModes.items[$idx].badgeText", item.badgeText, required = true, maxLen = 20, errors = errors)
            validateIconKey("controlModes.items[$idx].iconKey", item.iconKey, errors)
            validateRoute("controlModes.items[$idx].route", item.route, errors)
        }

        validateSafeText("bioSignals.headerLabel", config.bioSignals.headerLabel, required = config.bioSignals.visible, maxLen = 60, errors = errors)
        validateSafeText("bioSignals.actionText", config.bioSignals.actionText, required = config.bioSignals.visible, maxLen = 40, errors = errors)
        validateSafeText("bioSignals.eegLabel", config.bioSignals.eegLabel, required = config.bioSignals.visible, maxLen = 40, errors = errors)
        validateSafeText("bioSignals.emgLabel", config.bioSignals.emgLabel, required = config.bioSignals.visible, maxLen = 40, errors = errors)

        validateSafeText("prostheticStatus.title", config.prostheticStatus.title, required = config.prostheticStatus.visible, maxLen = 60, errors = errors)
        validateSafeText("prostheticStatus.therapyGoalLabel", config.prostheticStatus.therapyGoalLabel, required = config.prostheticStatus.visible, maxLen = 50, errors = errors)
        validateSafeText("prostheticStatus.quoteText", config.prostheticStatus.quoteText, required = false, maxLen = 220, errors = errors)

        validateSafeText("quickActions.headerLabel", config.quickActions.headerLabel, required = config.quickActions.visible, maxLen = 50, errors = errors)
        if (config.quickActions.actions.isEmpty() || config.quickActions.actions.size > 4) {
            errors += "quickActions.actions count (${config.quickActions.actions.size}) must be between 1 and 4."
        }
        config.quickActions.actions.forEachIndexed { idx, act ->
            validateSafeText("quickActions.actions[$idx].label", act.label, required = true, maxLen = 32, errors = errors)
            validateIconKey("quickActions.actions[$idx].iconKey", act.iconKey, errors)
            validateRoute("quickActions.actions[$idx].route", act.route, errors)
        }

        // Strict HTTPS URL checks
        validateImageUrl("topBar.defaultAvatarUrl", config.topBar.defaultAvatarUrl, allowEmpty = false, errors = errors)
        validateImageUrl("hero.imageUrl", config.hero.imageUrl, allowEmpty = !config.hero.visible, errors = errors)
        validateImageUrl("prostheticStatus.imageUrl", config.prostheticStatus.imageUrl, allowEmpty = !config.prostheticStatus.visible, errors = errors)
        validateImageUrl("promoBanner.imageUrl", config.promoBanner.imageUrl, allowEmpty = true, errors = errors)

        if (errors.isNotEmpty()) {
            return SduiValidationResult.Invalid(errors)
        }

        val normalizedJson = SduiJsonCodec.encode(config)
        val sha256 = SduiJsonCodec.sha256(normalizedJson)
        return SduiValidationResult.Valid(config, normalizedJson, sha256)
    }

    fun validateJson(rawJson: String): SduiValidationResult {
        val decoded = SduiJsonCodec.decodeStrictJson(rawJson)
        if (decoded.isFailure) {
            return SduiValidationResult.Invalid(
                listOf(decoded.exceptionOrNull()?.message ?: "Invalid SDUI JSON.")
            )
        }
        return validateConfig(decoded.getOrThrow())
    }

    private fun validateHex(field: String, hex: String, errors: MutableList<String>) {
        if (!SduiAllowlists.HEX_COLOR_REGEX.matches(hex.trim())) {
            errors += "Field '$field' has invalid hex color '$hex'."
        }
    }

    private fun validateRoute(field: String, route: String, errors: MutableList<String>) {
        if (route !in SduiAllowlists.ALLOWED_ROUTES) {
            errors += "Field '$field' uses unallowlisted navigation route '$route'."
        }
    }

    private fun validateIconKey(field: String, iconKey: String, errors: MutableList<String>) {
        if (iconKey !in SduiAllowlists.ALLOWED_ICON_KEYS) {
            errors += "Field '$field' uses unallowlisted icon key '$iconKey'."
        }
    }

    private fun validateSafeText(
        field: String,
        value: String,
        required: Boolean,
        maxLen: Int,
        errors: MutableList<String>
    ) {
        if (required && value.isBlank()) {
            errors += "Required field '$field' cannot be blank."
            return
        }
        if (value.length > maxLen) {
            errors += "Field '$field' exceeds max length ($maxLen)."
        }
        val lower = value.lowercase()
        SduiAllowlists.UNSAFE_CONTENT_PATTERNS.forEach { bad ->
            if (lower.contains(bad)) {
                errors += "Field '$field' contains unsafe executable pattern '$bad'."
            }
        }
    }

    private fun validateImageUrl(
        field: String,
        url: String,
        allowEmpty: Boolean,
        errors: MutableList<String>
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            if (!allowEmpty) {
                errors += "Required image URL '$field' is empty."
            }
            return
        }
        if (trimmed.length > 600) {
            errors += "Image URL '$field' exceeds 600 chars."
        }
        if (!trimmed.startsWith("https://")) {
            errors += "Image URL '$field' must use HTTPS ('https://...')."
        }
        if (trimmed.any { it.isWhitespace() || it in setOf('<', '>', '"', '\'') }) {
            errors += "Image URL '$field' contains illegal characters."
        }
    }
}
