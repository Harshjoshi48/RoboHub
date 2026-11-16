package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

data class BannerNavigationCallbacks(
    val onOpenScreen: (String) -> Unit = {},
    val onOpenCategory: (String) -> Unit = {},
    val onOpenProduct: (String) -> Unit = {},
    val onShowPopup: (BannerContentEntity, String) -> Unit = { _, _ -> }
)

object BannerCtaHandler {

    fun validateCta(
        ctaEnabled: Boolean,
        ctaText: String,
        actionType: BannerCtaAction,
        targetValue: String
    ): String? {
        if (!ctaEnabled || actionType == BannerCtaAction.NONE) {
            return null
        }
        if (ctaText.trim().isEmpty()) {
            return "CTA Button Text is required when CTA is enabled."
        }
        val cleanTarget = targetValue.trim()
        return when (actionType) {
            BannerCtaAction.NONE -> null
            BannerCtaAction.OPEN_URL -> {
                if (!cleanTarget.startsWith("https://", ignoreCase = true) &&
                    !cleanTarget.startsWith("http://", ignoreCase = true)
                ) {
                    "URL CTA target must start with https:// or http://"
                } else null
            }
            BannerCtaAction.OPEN_SCREEN -> {
                if (cleanTarget.isEmpty()) "Select a destination screen for OPEN_SCREEN CTA." else null
            }
            BannerCtaAction.OPEN_CATEGORY -> {
                if (cleanTarget.isEmpty()) "Enter a category identifier for OPEN_CATEGORY CTA." else null
            }
            BannerCtaAction.OPEN_PRODUCT -> {
                if (cleanTarget.isEmpty()) "Enter a module or product ID for OPEN_PRODUCT CTA." else null
            }
            BannerCtaAction.SHOW_POPUP -> {
                if (cleanTarget.isEmpty()) "Enter popup dialog message text for SHOW_POPUP CTA." else null
            }
            BannerCtaAction.CALL -> {
                val digits = cleanTarget.filter { it.isDigit() }
                if (digits.length < 6) "Enter a valid phone number (at least 6 digits)." else null
            }
            BannerCtaAction.WHATSAPP -> {
                val digits = cleanTarget.filter { it.isDigit() }
                if (digits.length < 7) "Enter a valid WhatsApp number with country code." else null
            }
        }
    }

    fun executeCta(
        context: Context,
        banner: BannerContentEntity,
        callbacks: BannerNavigationCallbacks
    ) {
        if (!banner.ctaEnabled) {
            callbacks.onShowPopup(
                banner,
                banner.description.ifBlank { banner.subtitle }
            )
            return
        }

        val action = BannerCtaAction.fromString(banner.ctaActionType)
        val target = banner.ctaTargetValue.ifBlank { banner.targetRoute }.trim()

        when (action) {
            BannerCtaAction.NONE -> {
                callbacks.onShowPopup(
                    banner,
                    banner.description.ifBlank { banner.subtitle }
                )
            }
            BannerCtaAction.OPEN_URL -> {
                val safeUrl = if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                    target
                } else {
                    "https://$target"
                }
                launchExternalIntent(
                    context = context,
                    intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    fallbackMsg = "Unable to open URL: $safeUrl"
                )
            }
            BannerCtaAction.OPEN_SCREEN -> {
                val route = target.ifBlank { "reports" }
                callbacks.onOpenScreen(route)
            }
            BannerCtaAction.OPEN_CATEGORY -> {
                callbacks.onOpenCategory(target.ifBlank { "rehab_gestures" })
            }
            BannerCtaAction.OPEN_PRODUCT -> {
                callbacks.onOpenProduct(target.ifBlank { "neurogrip_pro" })
            }
            BannerCtaAction.SHOW_POPUP -> {
                val msg = target.ifBlank { banner.description.ifBlank { banner.subtitle } }
                callbacks.onShowPopup(banner, msg)
            }
            BannerCtaAction.CALL -> {
                val sanitized = target.filter { it.isDigit() || it == '+' }
                if (sanitized.isBlank()) {
                    Toast.makeText(context, "Phone number is not configured", Toast.LENGTH_SHORT).show()
                } else {
                    launchExternalIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        fallbackMsg = "No dialer app available"
                    )
                }
            }
            BannerCtaAction.WHATSAPP -> {
                val digitsOnly = target.filter { it.isDigit() }
                if (digitsOnly.isBlank()) {
                    Toast.makeText(context, "WhatsApp number is not configured", Toast.LENGTH_SHORT).show()
                } else {
                    val waUri = Uri.parse("https://wa.me/$digitsOnly")
                    launchExternalIntent(
                        context = context,
                        intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        fallbackMsg = "Unable to open WhatsApp"
                    )
                }
            }
        }
    }

    private fun launchExternalIntent(context: Context, intent: Intent, fallbackMsg: String) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
        }
    }
}
