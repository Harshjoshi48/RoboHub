package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

fun bannerIconVector(iconKey: String, bannerType: BannerType): ImageVector {
    return when (iconKey.trim().lowercase()) {
        "campaign", "announcement" -> Icons.Outlined.Campaign
        "medical", "clinical" -> Icons.Outlined.MedicalServices
        "star", "promo", "promotion" -> Icons.Outlined.Stars
        "warning", "alert" -> Icons.Outlined.WarningAmber
        "bolt", "flash" -> Icons.Outlined.Bolt
        "bluetooth", "ble" -> Icons.Outlined.BluetoothConnected
        "shield", "security" -> Icons.Outlined.Shield
        "tips", "lightbulb" -> Icons.Outlined.Lightbulb
        "support", "headset" -> Icons.Outlined.SupportAgent
        "celebration", "gift" -> Icons.Outlined.CardGiftcard
        else -> when (bannerType) {
            BannerType.ANNOUNCEMENT -> Icons.Outlined.Campaign
            BannerType.CLINICAL_TIP -> Icons.Outlined.MedicalServices
            BannerType.PROMOTION -> Icons.Outlined.Stars
            BannerType.WARNING -> Icons.Outlined.WarningAmber
        }
    }
}

val bannerIconChoices: List<Pair<String, String>> = listOf(
    "campaign" to "Megaphone",
    "medical" to "Clinical",
    "star" to "Promo Star",
    "warning" to "Warning",
    "bolt" to "Fast Tip",
    "bluetooth" to "BLE Sync",
    "shield" to "Safety",
    "tips" to "Idea",
    "support" to "Support",
    "celebration" to "Offer"
)

@Composable
fun DynamicBannerCard(
    banner: BannerContentEntity,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onImpression: () -> Unit = {},
    onCtaClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onReadMore: () -> Unit = {}
) {
    val bannerType = BannerType.fromString(banner.bannerType)
    val bgColor = BannerColorUtils.parseColorOrDefault(
        banner.backgroundColorHex,
        BannerColorUtils.parseColorOrDefault(bannerType.preset.backgroundHex, Color(0xFFEFF6FF))
    )
    val fgColor = BannerColorUtils.parseColorOrDefault(
        banner.textColorHex,
        BannerColorUtils.parseColorOrDefault(bannerType.preset.textHex, Color(0xFF1E3A8A))
    )
    val btnColor = BannerColorUtils.parseColorOrDefault(
        banner.buttonColorHex,
        BannerColorUtils.parseColorOrDefault(bannerType.preset.buttonHex, Color(0xFF1D4ED8))
    )
    val btnTextColor = BannerColorUtils.parseColorOrDefault(
        banner.buttonTextColorHex,
        BannerColorUtils.parseColorOrDefault(bannerType.preset.buttonTextHex, Color.White)
    )
    val borderColor = BannerColorUtils.parseColorOrDefault(
        banner.borderColorHex,
        BannerColorUtils.parseColorOrDefault(bannerType.preset.borderHex, Color(0xFF93C5FD))
    )
    val icon = bannerIconVector(banner.iconKey, bannerType)
    val imageModel = remember(banner.imageUrl) { BannerImageHelper.resolveCoilModel(banner.imageUrl) }

    LaunchedEffect(banner.id, banner.bannerKey, banner.updatedAt) {
        onImpression()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dynamic_banner_card_${BannerEligibilityEngine.effectiveBannerKey(banner)}"),
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.25.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!compact && imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = banner.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 148.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 28.dp else 32.dp)
                                .background(fgColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = bannerType.badgeText,
                                tint = fgColor,
                                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                            )
                        }
                        Surface(
                            color = fgColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = bannerType.badgeText,
                                color = fgColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }

                    if (banner.dismissible) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("banner_dismiss_${BannerEligibilityEngine.effectiveBannerKey(banner)}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss banner",
                                tint = fgColor.copy(alpha = 0.78f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = banner.title,
                    color = fgColor,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = banner.subtitle,
                    color = fgColor.copy(alpha = 0.90f),
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = 18.sp,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!compact && banner.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = banner.description,
                        color = fgColor.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val showCta = banner.ctaEnabled &&
                    banner.ctaText.isNotBlank() &&
                    BannerCtaAction.fromString(banner.ctaActionType) != BannerCtaAction.NONE
                val showDetailsLink = banner.description.isNotBlank()

                if (showCta || showDetailsLink) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showCta) {
                            Button(
                                onClick = onCtaClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = btnColor,
                                    contentColor = btnTextColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("banner_cta_${BannerEligibilityEngine.effectiveBannerKey(banner)}")
                            ) {
                                Text(
                                    text = banner.ctaText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (showDetailsLink) {
                            TextButton(
                                onClick = onReadMore,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "View Details",
                                    color = fgColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DynamicBannerSlotHost(
    banners: List<BannerContentEntity>,
    position: BannerPosition,
    username: String,
    adminViewModel: AdminViewModel,
    callbacks: BannerNavigationCallbacks,
    modifier: Modifier = Modifier,
    horizontalPaddingDp: Int = 20
) {
    val context = LocalContext.current
    val dismissRevision by adminViewModel.bannerDismissRevision.collectAsState()
    val usersSnapshot by adminViewModel.users.collectAsState()
    val sessionSnapshot by adminViewModel.currentSession.collectAsState()

    val eligibleBanners = remember(banners, position, username, dismissRevision, usersSnapshot, sessionSnapshot) {
        val ctx = adminViewModel.currentBannerUserContext(username)
        BannerEligibilityEngine.filterEligibleForPosition(banners, position, ctx)
    }

    var detailBanner by remember { mutableStateOf<BannerContentEntity?>(null) }
    var popupData by remember { mutableStateOf<Pair<BannerContentEntity, String>?>(null) }
    var activeModalShownKeys by remember { mutableStateOf(setOf<String>()) }

    val wrappedCallbacks = remember(callbacks) {
        callbacks.copy(
            onShowPopup = { banner, msg ->
                popupData = banner to msg
                callbacks.onShowPopup(banner, msg)
            }
        )
    }

    if (eligibleBanners.isNotEmpty()) {
        when (position) {
            BannerPosition.HOME_SLIDER -> {
                val pagerState = rememberPagerState(pageCount = { eligibleBanners.size })
                Column(modifier = modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = horizontalPaddingDp.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val banner = eligibleBanners[page]
                        DynamicBannerCard(
                            banner = banner,
                            onImpression = {
                                adminViewModel.recordBannerImpression(banner, username, position.name)
                            },
                            onCtaClick = {
                                adminViewModel.recordBannerClick(banner, username, position.name)
                                BannerCtaHandler.executeCta(context, banner, wrappedCallbacks)
                            },
                            onDismiss = {
                                adminViewModel.dismissBannerForUser(banner, username, position.name)
                            },
                            onReadMore = {
                                detailBanner = banner
                            }
                        )
                    }
                    if (eligibleBanners.size > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(eligibleBanners.size) { index ->
                                val selected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (selected) 8.dp else 6.dp)
                                        .background(
                                            if (selected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }

            BannerPosition.POPUP -> {
                val modalBanner = eligibleBanners.firstOrNull {
                    BannerEligibilityEngine.effectiveBannerKey(it) !in activeModalShownKeys
                }
                if (modalBanner != null) {
                    val bKey = BannerEligibilityEngine.effectiveBannerKey(modalBanner)
                    LaunchedEffect(bKey) {
                        adminViewModel.recordBannerImpression(modalBanner, username, position.name)
                    }
                    AlertDialog(
                        onDismissRequest = {
                            activeModalShownKeys = activeModalShownKeys + bKey
                            if (modalBanner.dismissible) {
                                adminViewModel.dismissBannerForUser(modalBanner, username, position.name)
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = bannerIconVector(modalBanner.iconKey, BannerType.fromString(modalBanner.bannerType)),
                                    contentDescription = null,
                                    tint = BannerColorUtils.parseColorOrDefault(modalBanner.buttonColorHex, Color(0xFF1D4ED8))
                                )
                                Text(modalBanner.title, fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val img = BannerImageHelper.resolveCoilModel(modalBanner.imageUrl)
                                if (img != null) {
                                    AsyncImage(
                                        model = img,
                                        contentDescription = modalBanner.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(modalBanner.subtitle, fontWeight = FontWeight.SemiBold)
                                if (modalBanner.description.isNotBlank()) {
                                    Text(modalBanner.description, fontSize = 13.sp)
                                }
                            }
                        },
                        confirmButton = {
                            if (modalBanner.ctaEnabled && modalBanner.ctaText.isNotBlank()) {
                                Button(
                                    onClick = {
                                        activeModalShownKeys = activeModalShownKeys + bKey
                                        adminViewModel.recordBannerClick(modalBanner, username, position.name)
                                        BannerCtaHandler.executeCta(context, modalBanner, wrappedCallbacks)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(modalBanner.ctaText)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    activeModalShownKeys = activeModalShownKeys + bKey
                                    adminViewModel.dismissBannerForUser(modalBanner, username, position.name)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    )
                }
            }

            BannerPosition.TOP_STICKY, BannerPosition.BOTTOM_STICKY, BannerPosition.FLOATING -> {
                val sticky = eligibleBanners.first()
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPaddingDp.dp, vertical = 4.dp)
                ) {
                    DynamicBannerCard(
                        banner = sticky,
                        compact = true,
                        onImpression = {
                            adminViewModel.recordBannerImpression(sticky, username, position.name)
                        },
                        onCtaClick = {
                            adminViewModel.recordBannerClick(sticky, username, position.name)
                            BannerCtaHandler.executeCta(context, sticky, wrappedCallbacks)
                        },
                        onDismiss = {
                            adminViewModel.dismissBannerForUser(sticky, username, position.name)
                        },
                        onReadMore = {
                            detailBanner = sticky
                        }
                    )
                }
            }

            else -> {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    eligibleBanners.take(3).forEach { banner ->
                        DynamicBannerCard(
                            banner = banner,
                            onImpression = {
                                adminViewModel.recordBannerImpression(banner, username, position.name)
                            },
                            onCtaClick = {
                                adminViewModel.recordBannerClick(banner, username, position.name)
                                BannerCtaHandler.executeCta(context, banner, wrappedCallbacks)
                            },
                            onDismiss = {
                                adminViewModel.dismissBannerForUser(banner, username, position.name)
                            },
                            onReadMore = {
                                detailBanner = banner
                            }
                        )
                    }
                }
            }
        }
    }

    // View Details Dialog
    detailBanner?.let { banner ->
        AlertDialog(
            onDismissRequest = { detailBanner = null },
            title = { Text(banner.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val img = BannerImageHelper.resolveCoilModel(banner.imageUrl)
                    if (img != null) {
                        AsyncImage(
                            model = img,
                            contentDescription = banner.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(banner.subtitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (banner.description.isNotBlank()) {
                        Text(banner.description, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    if (banner.bannerType == BannerType.CLINICAL_TIP.name && banner.reviewedBy.isNotBlank()) {
                        Text(
                            "Clinical Review: Approved by @${banner.reviewedBy}",
                            fontSize = 11.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (banner.ctaEnabled && BannerCtaAction.fromString(banner.ctaActionType) != BannerCtaAction.NONE) {
                    Button(
                        onClick = {
                            detailBanner = null
                            adminViewModel.recordBannerClick(banner, username, position.name)
                            BannerCtaHandler.executeCta(context, banner, wrappedCallbacks)
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(banner.ctaText.ifBlank { "Open" })
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detailBanner = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Close")
                }
            }
        )
    }

    // SHOW_POPUP CTA Handler Dialog
    popupData?.let { (banner, msg) ->
        AlertDialog(
            onDismissRequest = { popupData = null },
            title = { Text(banner.title, fontWeight = FontWeight.Bold) },
            text = { Text(msg, fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = { popupData = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("OK")
                }
            }
        )
    }
}
