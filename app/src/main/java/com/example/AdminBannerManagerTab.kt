package com.example

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminBannerManagerScreen(
    banners: List<BannerContentEntity>,
    adminViewModel: AdminViewModel
) {
    val context = LocalContext.current
    val metrics by adminViewModel.bannerMetrics.collectAsState()
    val analyticsSummary by adminViewModel.bannerAnalyticsSummary.collectAsState()
    val syncStatus by adminViewModel.bannerSyncStatus.collectAsState()

    var subTabIndex by remember { mutableIntStateOf(0) } // 0 = Manager & Editor, 1 = Analytics
    var filterStatus by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingBannerId by remember { mutableIntStateOf(0) }
    var showPublishPreviewDialog by remember { mutableStateOf(false) }
    var previewCandidate by remember { mutableStateOf<BannerContentEntity?>(null) }

    // Editor form state
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var bannerType by remember { mutableStateOf(BannerType.ANNOUNCEMENT) }
    var iconKey by remember { mutableStateOf("campaign") }
    var imageUrl by remember { mutableStateOf("") }
    var backgroundHex by remember { mutableStateOf("#EFF6FF") }
    var textHex by remember { mutableStateOf("#1E3A8A") }
    var buttonHex by remember { mutableStateOf("#1D4ED8") }
    var buttonTextHex by remember { mutableStateOf("#FFFFFF") }
    var borderHex by remember { mutableStateOf("#93C5FD") }
    var ctaEnabled by remember { mutableStateOf(true) }
    var ctaText by remember { mutableStateOf("Open Now") }
    var ctaActionType by remember { mutableStateOf(BannerCtaAction.OPEN_SCREEN) }
    var ctaTargetValue by remember { mutableStateOf("hand_control") }
    var displayPosition by remember { mutableStateOf(BannerPosition.HOME_TOP) }
    var priorityText by remember { mutableStateOf("10") }
    var isActive by remember { mutableStateOf(true) }
    var isPublic by remember { mutableStateOf(true) }
    var isPublished by remember { mutableStateOf(true) }
    var isDraft by remember { mutableStateOf(false) }
    var clinicalApproved by remember { mutableStateOf(true) }
    var timezoneId by remember { mutableStateOf(BannerTimezoneHelper.commonTimezones.first()) }
    var startDateInput by remember { mutableStateOf("") }
    var endDateInput by remember { mutableStateOf("") }
    var targetAudience by remember { mutableStateOf(BannerTargetingMode.PUBLIC_ALL) }
    var targetValuesCsv by remember { mutableStateOf("") }
    var dismissible by remember { mutableStateOf(true) }
    var reShowHoursText by remember { mutableStateOf("24") }
    var formError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = BannerImageHelper.processPickedImageToDataUriAndCache(
                context = context,
                uri = uri,
                bannerKeyHint = if (editingBannerId > 0) "banner_$editingBannerId" else "new_banner"
            )
            if (encoded != null) {
                imageUrl = encoded
                Toast.makeText(context, "Banner image compressed & attached!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to process selected image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyTypePreset(type: BannerType) {
        bannerType = type
        backgroundHex = type.preset.backgroundHex
        textHex = type.preset.textHex
        buttonHex = type.preset.buttonHex
        buttonTextHex = type.preset.buttonTextHex
        borderHex = type.preset.borderHex
        iconKey = type.preset.iconKey
        if (type == BannerType.CLINICAL_TIP && editingBannerId == 0) {
            clinicalApproved = false
        } else if (!type.requiresClinicalReview) {
            clinicalApproved = true
        }
    }

    fun loadBannerIntoEditor(banner: BannerContentEntity?) {
        formError = null
        if (banner == null) {
            editingBannerId = 0
            title = ""
            subtitle = ""
            description = ""
            applyTypePreset(BannerType.ANNOUNCEMENT)
            imageUrl = ""
            ctaEnabled = true
            ctaText = "Explore Now"
            ctaActionType = BannerCtaAction.OPEN_SCREEN
            ctaTargetValue = "hand_control"
            displayPosition = BannerPosition.HOME_TOP
            priorityText = "10"
            isActive = true
            isPublic = true
            isPublished = true
            isDraft = false
            clinicalApproved = true
            timezoneId = BannerTimezoneHelper.commonTimezones.first()
            startDateInput = ""
            endDateInput = ""
            targetAudience = BannerTargetingMode.PUBLIC_ALL
            targetValuesCsv = ""
            dismissible = true
            reShowHoursText = "24"
        } else {
            editingBannerId = banner.id
            title = banner.title
            subtitle = banner.subtitle
            description = banner.description
            bannerType = BannerType.fromString(banner.bannerType)
            iconKey = banner.iconKey
            imageUrl = banner.imageUrl
            backgroundHex = banner.backgroundColorHex
            textHex = banner.textColorHex
            buttonHex = banner.buttonColorHex
            buttonTextHex = banner.buttonTextColorHex
            borderHex = banner.borderColorHex
            ctaEnabled = banner.ctaEnabled
            ctaText = banner.ctaText
            ctaActionType = BannerCtaAction.fromString(banner.ctaActionType)
            ctaTargetValue = banner.ctaTargetValue.ifBlank { banner.targetRoute }
            displayPosition = BannerPosition.fromString(banner.displayPosition)
            priorityText = banner.priority.toString()
            isActive = banner.isActive
            isPublic = banner.isPublic
            isPublished = banner.isPublished
            isDraft = banner.isDraft
            clinicalApproved = BannerEligibilityEngine.resolveClinicalApproved(banner)
            timezoneId = banner.timezoneId.ifBlank { BannerTimezoneHelper.commonTimezones.first() }
            startDateInput = if (banner.startAtMillis > 0L) BannerTimezoneHelper.formatMillis(banner.startAtMillis, timezoneId) else ""
            endDateInput = if (banner.endAtMillis > 0L) BannerTimezoneHelper.formatMillis(banner.endAtMillis, timezoneId) else ""
            targetAudience = BannerTargetingMode.fromString(banner.targetAudience)
            targetValuesCsv = banner.targetValuesCsv
            dismissible = banner.dismissible
            reShowHoursText = banner.reShowAfterDismissHours.toString()
        }
        showEditor = true
    }

    fun buildCandidateFromForm(asDraftOverride: Boolean? = null): BannerContentEntity? {
        if (title.trim().isEmpty() || subtitle.trim().isEmpty()) {
            formError = "Banner heading and subtitle are required."
            return null
        }
        val ctaErr = BannerCtaHandler.validateCta(ctaEnabled, ctaText, ctaActionType, ctaTargetValue)
        if (ctaErr != null) {
            formError = ctaErr
            return null
        }
        val startMs = if (startDateInput.trim().isEmpty()) 0L else {
            BannerTimezoneHelper.parseDateTimeToMillis(startDateInput, timezoneId) ?: run {
                formError = "Invalid Start Date format. Use yyyy-MM-dd HH:mm"
                return null
            }
        }
        val endMs = if (endDateInput.trim().isEmpty()) 0L else {
            BannerTimezoneHelper.parseDateTimeToMillis(endDateInput, timezoneId) ?: run {
                formError = "Invalid End Date format. Use yyyy-MM-dd HH:mm"
                return null
            }
        }
        if (startMs > 0L && endMs > 0L && endMs <= startMs) {
            formError = "End schedule must be after Start schedule."
            return null
        }
        formError = null
        val existing = banners.find { it.id == editingBannerId }
        val finalDraft = asDraftOverride ?: isDraft
        val finalPublished = if (finalDraft) false else isPublished
        val actor = adminViewModel.effectiveUsername()
        val reviewStatus = when {
            !bannerType.requiresClinicalReview -> ClinicalReviewStatus.NOT_REQUIRED.name
            clinicalApproved -> ClinicalReviewStatus.APPROVED.name
            else -> ClinicalReviewStatus.PENDING_REVIEW.name
        }
        return BannerContentEntity(
            id = editingBannerId,
            bannerKey = existing?.bannerKey ?: if (editingBannerId > 0) "banner_$editingBannerId" else "",
            title = title.trim(),
            subtitle = subtitle.trim(),
            description = description.trim(),
            bannerType = bannerType.name,
            iconKey = iconKey,
            imageUrl = imageUrl.trim(),
            ctaEnabled = ctaEnabled,
            ctaText = ctaText.trim().ifBlank { "Learn More" },
            ctaActionType = ctaActionType.name,
            ctaTargetValue = ctaTargetValue.trim(),
            targetRoute = ctaTargetValue.trim(),
            backgroundColorHex = BannerColorUtils.normalizeHexOrDefault(backgroundHex, bannerType.preset.backgroundHex),
            textColorHex = BannerColorUtils.normalizeHexOrDefault(textHex, bannerType.preset.textHex),
            buttonColorHex = BannerColorUtils.normalizeHexOrDefault(buttonHex, bannerType.preset.buttonHex),
            buttonTextColorHex = BannerColorUtils.normalizeHexOrDefault(buttonTextHex, bannerType.preset.buttonTextHex),
            borderColorHex = BannerColorUtils.normalizeHexOrDefault(borderHex, bannerType.preset.borderHex),
            isActive = isActive,
            isPublic = isPublic,
            isPublished = finalPublished,
            isDraft = finalDraft,
            clinicalReviewStatus = reviewStatus,
            clinicalApproved = !bannerType.requiresClinicalReview || clinicalApproved,
            reviewedBy = if (clinicalApproved) actor else existing?.reviewedBy.orEmpty(),
            priority = priorityText.toIntOrNull() ?: 1,
            displayPosition = displayPosition.name,
            startAtMillis = startMs,
            endAtMillis = endMs,
            timezoneId = timezoneId,
            targetAudience = targetAudience.name,
            targetValuesCsv = targetValuesCsv.trim(),
            dismissible = dismissible,
            reShowAfterDismissHours = reShowHoursText.toIntOrNull()?.coerceIn(0, 8760) ?: 24,
            impressionsCount = existing?.impressionsCount ?: 0,
            uniqueViewsCount = existing?.uniqueViewsCount ?: 0,
            clicksCount = existing?.clicksCount ?: 0,
            createdBy = existing?.createdBy ?: actor,
            updatedBy = actor,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    val livePreviewBanner = remember(
        editingBannerId, title, subtitle, description, bannerType, iconKey, imageUrl,
        backgroundHex, textHex, buttonHex, buttonTextHex, borderHex,
        ctaEnabled, ctaText, ctaActionType, ctaTargetValue, dismissible
    ) {
        BannerContentEntity(
            id = editingBannerId.coerceAtLeast(999),
            bannerKey = "live_preview",
            title = title.ifBlank { "Real-Time Banner Preview" },
            subtitle = subtitle.ifBlank { "Customize heading, subtitle, colors, icon, image & CTA below." },
            description = description,
            bannerType = bannerType.name,
            iconKey = iconKey,
            imageUrl = imageUrl,
            ctaEnabled = ctaEnabled,
            ctaText = ctaText.ifBlank { "Learn More" },
            ctaActionType = ctaActionType.name,
            ctaTargetValue = ctaTargetValue,
            backgroundColorHex = BannerColorUtils.normalizeHexOrDefault(backgroundHex, bannerType.preset.backgroundHex),
            textColorHex = BannerColorUtils.normalizeHexOrDefault(textHex, bannerType.preset.textHex),
            buttonColorHex = BannerColorUtils.normalizeHexOrDefault(buttonHex, bannerType.preset.buttonHex),
            buttonTextColorHex = BannerColorUtils.normalizeHexOrDefault(buttonTextHex, bannerType.preset.buttonTextHex),
            borderColorHex = BannerColorUtils.normalizeHexOrDefault(borderHex, bannerType.preset.borderHex),
            dismissible = dismissible
        )
    }

    val filteredBanners = remember(banners, filterStatus, searchQuery) {
        val q = searchQuery.trim().lowercase()
        banners.filter { b ->
            val state = BannerEligibilityEngine.classifyLifecycleState(b)
            val statusMatches = when (filterStatus) {
                "LIVE" -> state == BannerLifecycleState.LIVE
                "DRAFT" -> b.isDraft
                "SCHEDULED" -> state == BannerLifecycleState.SCHEDULED
                "INACTIVE" -> !b.isActive || state == BannerLifecycleState.EXPIRED
                "REVIEW" -> state == BannerLifecycleState.PENDING_REVIEW
                else -> true
            }
            val queryMatches = q.isEmpty() ||
                b.title.lowercase().contains(q) ||
                b.subtitle.lowercase().contains(q) ||
                b.bannerType.lowercase().contains(q) ||
                b.displayPosition.lowercase().contains(q)
            statusMatches && queryMatches
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Sub-Tab Switcher
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dynamic Banner Manager",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "${syncStatus.sourceLabel} • Real-time Firebase & Offline Cache",
                                fontSize = 11.sp,
                                color = if (syncStatus.isListening) Color(0xFF059669) else Color(0xFF64748B)
                            )
                        }
                        Button(
                            onClick = { loadBannerIntoEditor(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("create_new_banner_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Banner", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subTabIndex == 0,
                            onClick = { subTabIndex = 0 },
                            label = { Text("Manage Banners (${banners.size})", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("banner_subtab_manage")
                        )
                        FilterChip(
                            selected = subTabIndex == 1,
                            onClick = { subTabIndex = 1 },
                            label = { Text("Analytics (${metrics.totalImpressions} views)", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("banner_subtab_analytics")
                        )
                    }
                }
            }
        }

        // KPI Dashboard Metrics Grid (8 KPIs)
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4
            ) {
                BannerKpiTile("Total", metrics.totalBanners.toString(), Color(0xFF1E3A8A), Color(0xFFEFF6FF), Modifier.weight(1f))
                BannerKpiTile("Active", metrics.activeBanners.toString(), Color(0xFF065F46), Color(0xFFECFDF5), Modifier.weight(1f))
                BannerKpiTile("Inactive", metrics.inactiveBanners.toString(), Color(0xFF475569), Color(0xFFF1F5F9), Modifier.weight(1f))
                BannerKpiTile("Published", metrics.publishedBanners.toString(), Color(0xFF1D4ED8), Color(0xFFDBEAFE), Modifier.weight(1f))
                BannerKpiTile("Drafts", metrics.draftBanners.toString(), Color(0xFF92400E), Color(0xFFFEF3C7), Modifier.weight(1f))
                BannerKpiTile("Scheduled", metrics.scheduledBanners.toString(), Color(0xFF6D28D9), Color(0xFFF3E8FF), Modifier.weight(1f))
                BannerKpiTile("Impressions", metrics.totalImpressions.toString(), Color(0xFF0F766E), Color(0xFFCCFBF1), Modifier.weight(1f))
                BannerKpiTile("Clicks", metrics.totalClicks.toString(), Color(0xFFBE185D), Color(0xFFFCE7F3), Modifier.weight(1f))
            }
        }

        if (subTabIndex == 0) {
            // Editor Card with Real-Time Preview
            if (showEditor) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (editingBannerId == 0) "Create Dynamic Banner" else "Edit Banner #$editingBannerId",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                TextButton(onClick = { showEditor = false }) { Text("Close") }
                            }

                            Text("REAL-TIME PREVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            DynamicBannerCard(banner = livePreviewBanner)

                            // Type & Defaults
                            Text("Banner Type", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BannerType.entries.forEach { t ->
                                    FilterChip(
                                        selected = bannerType == t,
                                        onClick = { applyTypePreset(t) },
                                        label = { Text(t.label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            if (bannerType.requiresClinicalReview) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Clinical Tip Approval Gate", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF065F46))
                                        Text("Clinical tips require approval before rendering to patients.", fontSize = 11.sp, color = Color(0xFF047857))
                                    }
                                    Switch(checked = clinicalApproved, onCheckedChange = { clinicalApproved = it })
                                }
                            }

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it; formError = null },
                                label = { Text("Banner Heading *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("banner_title_input")
                            )
                            OutlinedTextField(
                                value = subtitle,
                                onValueChange = { subtitle = it; formError = null },
                                label = { Text("Banner Subtitle *") },
                                modifier = Modifier.fillMaxWidth().testTag("banner_subtitle_input")
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Detailed Description (shown in View Details / Popup)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            // Icon Picker
                            Text("Banner Icon", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bannerIconChoices.forEach { (key, label) ->
                                    FilterChip(
                                        selected = iconKey == key,
                                        onClick = { iconKey = key },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = bannerIconVector(key, bannerType),
                                                contentDescription = label,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Image Upload + URL
                            Text("Banner Media Image", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload Photo")
                                }
                                if (imageUrl.isNotBlank()) {
                                    TextButton(
                                        onClick = { imageUrl = "" },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Clear Image", color = Color(0xFFDC2626))
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = if (imageUrl.startsWith("data:image/")) "[Uploaded Compressed Image Attached]" else imageUrl,
                                onValueChange = { if (!it.startsWith("[Uploaded")) imageUrl = it },
                                label = { Text("Or Paste Image HTTPS URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Colors
                            Text("Custom HEX Colors", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BannerColorUtils.curatedSwatches.forEach { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(BannerColorUtils.parseColorOrDefault(hex, Color.Blue))
                                            .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                                            .clickable { backgroundHex = hex }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = backgroundHex, onValueChange = { backgroundHex = it }, label = { Text("Bg HEX") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = textHex, onValueChange = { textHex = it }, label = { Text("Text HEX") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = borderHex, onValueChange = { borderHex = it }, label = { Text("Border HEX") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = buttonHex, onValueChange = { buttonHex = it }, label = { Text("Btn HEX") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = buttonTextHex, onValueChange = { buttonTextHex = it }, label = { Text("Btn Text HEX") }, singleLine = true, modifier = Modifier.weight(1f))
                            }

                            // Complete CTA Configuration
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("CTA Button Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Switch(checked = ctaEnabled, onCheckedChange = { ctaEnabled = it })
                            }
                            if (ctaEnabled) {
                                OutlinedTextField(
                                    value = ctaText,
                                    onValueChange = { ctaText = it },
                                    label = { Text("CTA Button Text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("CTA Action Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BannerCtaAction.entries.forEach { act ->
                                        FilterChip(
                                            selected = ctaActionType == act,
                                            onClick = { ctaActionType = act },
                                            label = { Text(act.name, fontSize = 10.sp) }
                                        )
                                    }
                                }
                                if (ctaActionType == BannerCtaAction.OPEN_SCREEN) {
                                    Text("Quick App Destination", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BannerAppScreens.routes.forEach { (route, label) ->
                                            FilterChip(
                                                selected = ctaTargetValue == route,
                                                onClick = { ctaTargetValue = route },
                                                label = { Text(label, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                                if (ctaActionType != BannerCtaAction.NONE) {
                                    OutlinedTextField(
                                        value = ctaTargetValue,
                                        onValueChange = { ctaTargetValue = it },
                                        label = { Text("${ctaActionType.targetLabel} (${ctaActionType.targetHint})") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Display Position & Priority
                            HorizontalDivider()
                            Text("Display Position & Priority", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BannerPosition.entries.forEach { pos ->
                                    FilterChip(
                                        selected = displayPosition == pos,
                                        onClick = { displayPosition = pos },
                                        label = { Text(pos.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = priorityText,
                                    onValueChange = { priorityText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                                    label = { Text("Priority (Higher First)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = reShowHoursText,
                                    onValueChange = { reShowHoursText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Re-show Cooldown (hrs)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Scheduling & Timezone
                            Text("Schedule & Timezone ($timezoneId)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BannerTimezoneHelper.commonTimezones.forEach { tz ->
                                    FilterChip(
                                        selected = timezoneId == tz,
                                        onClick = { timezoneId = tz },
                                        label = { Text(tz, fontSize = 10.sp) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = startDateInput,
                                    onValueChange = { startDateInput = it },
                                    label = { Text("Start (yyyy-MM-dd HH:mm)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = endDateInput,
                                    onValueChange = { endDateInput = it },
                                    label = { Text("End (yyyy-MM-dd HH:mm)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val now = System.currentTimeMillis()
                                    startDateInput = BannerTimezoneHelper.formatMillis(now, timezoneId)
                                    endDateInput = BannerTimezoneHelper.formatMillis(now + 24 * 3_600_000L, timezoneId)
                                }) { Text("24h Window") }
                                TextButton(onClick = {
                                    val now = System.currentTimeMillis()
                                    startDateInput = BannerTimezoneHelper.formatMillis(now + 3_600_000L, timezoneId)
                                    endDateInput = BannerTimezoneHelper.formatMillis(now + 7L * 24 * 3_600_000L, timezoneId)
                                }) { Text("Start in 1h (7d)") }
                                TextButton(onClick = {
                                    startDateInput = ""
                                    endDateInput = ""
                                }) { Text("Clear Schedule") }
                            }

                            // User Targeting
                            Text("Audience Targeting", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BannerTargetingMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = targetAudience == mode,
                                        onClick = { targetAudience = mode },
                                        label = { Text(mode.label, fontSize = 11.sp) }
                                    )
                                }
                            }
                            if (targetAudience == BannerTargetingMode.SPECIFIC_USERS || targetAudience == BannerTargetingMode.USER_ROLES) {
                                OutlinedTextField(
                                    value = targetValuesCsv,
                                    onValueChange = { targetValuesCsv = it },
                                    label = { Text("Comma-separated targets (${targetAudience.hint})") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Visibility Toggles
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ConfigToggleRow("Banner Active (ON / OFF)", isActive) { isActive = it }
                                ConfigToggleRow("Public Live Mode (Visible to targeted users)", isPublic) { isPublic = it }
                                ConfigToggleRow("Dismissible (Close button visible)", dismissible) { dismissible = it }
                            }

                            formError?.let { err ->
                                Text(err, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val candidate = buildCandidateFromForm(asDraftOverride = true) ?: return@OutlinedButton
                                        adminViewModel.saveBanner(candidate) { ok, _ ->
                                            if (ok) showEditor = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Save Draft", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val candidate = buildCandidateFromForm(asDraftOverride = false) ?: return@OutlinedButton
                                        previewCandidate = candidate
                                        showPublishPreviewDialog = true
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Preview", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        val candidate = buildCandidateFromForm(asDraftOverride = false) ?: return@Button
                                        adminViewModel.saveBanner(candidate) { ok, _ ->
                                            if (ok) showEditor = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).testTag("publish_banner_button")
                                ) {
                                    Text("Publish Live", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Filter & Search Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search banners by title, type, or slot...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "LIVE", "DRAFT", "SCHEDULED", "REVIEW", "INACTIVE").forEach { key ->
                            FilterChip(
                                selected = filterStatus == key,
                                onClick = { filterStatus = key },
                                label = { Text(key, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }

            items(filteredBanners, key = { it.id }) { banner ->
                AdminBannerRowCard(
                    banner = banner,
                    onEdit = { loadBannerIntoEditor(banner) },
                    onDuplicate = { adminViewModel.duplicateBanner(banner) },
                    onToggleActive = { active -> adminViewModel.saveBanner(banner.copy(isActive = active)) },
                    onTogglePublic = { pub -> adminViewModel.saveBanner(banner.copy(isPublic = pub)) },
                    onPublishToggle = {
                        if (banner.isDraft || !banner.isPublished) {
                            adminViewModel.publishBanner(banner, makePublic = true)
                        } else {
                            adminViewModel.unpublishBanner(banner)
                        }
                    },
                    onApproveClinical = { adminViewModel.reviewClinicalTipBanner(banner, approved = true) },
                    onDelete = { adminViewModel.deleteBanner(banner.id) }
                )
            }
        } else {
            // Analytics View
            item {
                AdminBannerAnalyticsSection(summary = analyticsSummary)
            }
        }
    }

    // Preview Before Publish Modal
    if (showPublishPreviewDialog && previewCandidate != null) {
        val candidate = previewCandidate!!
        AlertDialog(
            onDismissRequest = { showPublishPreviewDialog = false },
            title = { Text("Preview Before Publish", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Position: ${candidate.displayPosition} • Audience: ${candidate.targetAudience} • Public Live: ${candidate.isPublic}",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    DynamicBannerCard(banner = candidate)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPublishPreviewDialog = false
                        adminViewModel.saveBanner(candidate) { ok, _ ->
                            if (ok) showEditor = false
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Confirm & Publish Live")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishPreviewDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Back to Editor")
                }
            }
        )
    }
}

@Composable
private fun BannerKpiTile(
    label: String,
    value: String,
    fgColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.widthIn(min = 76.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = fgColor, maxLines = 1)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fgColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminBannerRowCard(
    banner: BannerContentEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onTogglePublic: (Boolean) -> Unit,
    onPublishToggle: () -> Unit,
    onApproveClinical: () -> Unit,
    onDelete: () -> Unit
) {
    val lifecycle = BannerEligibilityEngine.classifyLifecycleState(banner)
    val (badgeBg, badgeFg) = when (lifecycle) {
        BannerLifecycleState.LIVE -> Color(0xFFDCFCE7) to Color(0xFF166534)
        BannerLifecycleState.DRAFT -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        BannerLifecycleState.SCHEDULED -> Color(0xFFF3E8FF) to Color(0xFF6B21A8)
        BannerLifecycleState.PENDING_REVIEW -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
        BannerLifecycleState.EXPIRED -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> Color(0xFFE2E8F0) to Color(0xFF334155)
    }
    val ctr = if (banner.impressionsCount > 0) {
        (banner.clicksCount.toFloat() / banner.impressionsCount.toFloat()) * 100f
    } else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                        Text(lifecycle.label, color = badgeFg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp)) {
                        Text(banner.displayPosition, color = Color(0xFF334155), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Text("Priority ${banner.priority}", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
            }

            Text(banner.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
            Text(banner.subtitle, fontSize = 12.sp, color = Color(0xFF475569), maxLines = 2, overflow = TextOverflow.Ellipsis)

            Text(
                "Type: ${banner.bannerType} • CTA: ${if (banner.ctaEnabled) "${banner.ctaText} (${banner.ctaActionType})" else "Disabled"} • Audience: ${banner.targetAudience}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
            Text(
                "Views: ${banner.impressionsCount} • Unique: ${banner.uniqueViewsCount} • Clicks: ${banner.clicksCount} • CTR: ${String.format("%.1f", ctr)}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D4ED8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", fontSize = 11.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = banner.isActive, onCheckedChange = onToggleActive)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public Live", fontSize = 11.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = banner.isPublic, onCheckedChange = onTogglePublic)
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Duplicate", fontSize = 12.sp)
                }
                Button(
                    onClick = onPublishToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (banner.isDraft || !banner.isPublished) Color(0xFF059669) else Color(0xFF64748B)
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (banner.isDraft || !banner.isPublished) "Publish" else "Unpublish", fontSize = 12.sp)
                }
                if (lifecycle == BannerLifecycleState.PENDING_REVIEW) {
                    Button(
                        onClick = onApproveClinical,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Approve Tip", fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete Banner", tint = Color(0xFFDC2626))
                }
            }
        }
    }
}

@Composable
private fun AdminBannerAnalyticsSection(summary: BannerAnalyticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Overall Banner Performance", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerKpiTile("Impressions", summary.totalImpressions.toString(), Color(0xFF1E3A8A), Color(0xFFEFF6FF), Modifier.weight(1f))
                    BannerKpiTile("Unique Views", summary.totalUniqueViews.toString(), Color(0xFF0F766E), Color(0xFFCCFBF1), Modifier.weight(1f))
                    BannerKpiTile("Clicks", summary.totalClicks.toString(), Color(0xFFBE185D), Color(0xFFFCE7F3), Modifier.weight(1f))
                    BannerKpiTile("CTR %", "${String.format("%.1f", summary.overallCtrPercent)}%", Color(0xFF065F46), Color(0xFFECFDF5), Modifier.weight(1f))
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date-Wise Banner Statistics", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (summary.dailyStats.isEmpty()) {
                    Text(
                        "No impression or click events recorded yet. Open Dashboard as a user to record live views & clicks.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                } else {
                    val maxImpr = summary.dailyStats.maxOf { it.impressions }.coerceAtLeast(1)
                    summary.dailyStats.forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day.dateKey, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "${day.impressions} views • ${day.uniqueViews} uniq • ${day.clicks} clicks (${String.format("%.1f", day.ctrPercent)}%)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF334155)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { day.impressions.toFloat() / maxImpr.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF2563EB),
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}
