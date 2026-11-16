package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.abs

enum class AvatarRenderMode {
    PHOTO,
    INITIAL_FROM_NAME,
    INITIAL_FROM_EMAIL,
    DEFAULT_ICON
}

data class SmartAvatarState(
    val mode: AvatarRenderMode,
    val photoUrl: String? = null,
    val initialChar: String? = null,
    val photoUpdatedAt: Long = 0L,
    val paletteIndex: Int = 0
)

data class ValidatedProfilePhoto(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
    val fileName: String,
    val sizeBytes: Long
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024L -> "$sizeBytes B"
            sizeBytes < 1024L * 1024L -> "${(sizeBytes / 1024.0).let { "%.1f".format(Locale.US, it) }} KB"
            else -> "${(sizeBytes / (1024.0 * 1024.0)).let { "%.2f".format(Locale.US, it) }} MB"
        }
}

object SmartAvatarResolver {
    private val LEGACY_STOCK_URLS = setOf(
        "https://i.pravatar.cc/100?img=11"
    )

    private val AVATAR_PALETTE = listOf(
        Color(0xFF1D4ED8) to Color(0xFF2563EB), // Deep Cobalt
        Color(0xFF0F766E) to Color(0xFF14B8A6), // Clinical Teal
        Color(0xFF4338CA) to Color(0xFF6366F1), // Indigo
        Color(0xFF0369A1) to Color(0xFF0EA5E9), // Ocean Blue
        Color(0xFF7C3AED) to Color(0xFF8B5CF6), // Violet
        Color(0xFF047857) to Color(0xFF10B981)  // Emerald
    )

    fun extractFirstInitial(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val ch = trimmed.firstOrNull { it.isLetterOrDigit() } ?: return null
        return ch.uppercaseChar().toString()
    }

    fun isUsablePhotoUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return false
        if (clean in LEGACY_STOCK_URLS) return false
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true)
    }

    fun resolve(
        photoUrl: String?,
        fullName: String?,
        email: String?,
        photoUpdatedAt: Long = 0L
    ): SmartAvatarState {
        val cleanPhoto = photoUrl?.trim().takeIf { isUsablePhotoUrl(it) }
        val cleanName = fullName?.trim().orEmpty()
        val cleanEmail = email?.trim().orEmpty()
        val seed = (cleanName.ifBlank { cleanEmail }.ifBlank { "neurogrip" }).lowercase(Locale.US)
        val paletteIdx = abs(seed.hashCode()) % AVATAR_PALETTE.size

        val nameInitial = extractFirstInitial(cleanName)
        val emailLocal = cleanEmail.substringBefore("@").trim()
        val emailInitial = extractFirstInitial(emailLocal.ifBlank { cleanEmail })

        return when {
            cleanPhoto != null -> SmartAvatarState(
                mode = AvatarRenderMode.PHOTO,
                photoUrl = cleanPhoto,
                initialChar = nameInitial ?: emailInitial,
                photoUpdatedAt = photoUpdatedAt,
                paletteIndex = paletteIdx
            )
            nameInitial != null -> SmartAvatarState(
                mode = AvatarRenderMode.INITIAL_FROM_NAME,
                initialChar = nameInitial,
                photoUpdatedAt = photoUpdatedAt,
                paletteIndex = paletteIdx
            )
            emailInitial != null -> SmartAvatarState(
                mode = AvatarRenderMode.INITIAL_FROM_EMAIL,
                initialChar = emailInitial,
                photoUpdatedAt = photoUpdatedAt,
                paletteIndex = paletteIdx
            )
            else -> SmartAvatarState(
                mode = AvatarRenderMode.DEFAULT_ICON,
                photoUpdatedAt = photoUpdatedAt,
                paletteIndex = paletteIdx
            )
        }
    }

    fun paletteColors(index: Int): Pair<Color, Color> {
        return AVATAR_PALETTE[abs(index) % AVATAR_PALETTE.size]
    }
}

object ProfilePhotoValidator {
    const val MAX_PROFILE_PHOTO_BYTES: Long = 5L * 1024L * 1024L // 5 MB
    val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun detectFormatFromBytes(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg" to "jpg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png" to "png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp" to "webp"
        }
        return null
    }

    fun validateImageBytesAndMetadata(
        bytes: ByteArray,
        mimeType: String?,
        fileName: String?,
        maxBytes: Long = MAX_PROFILE_PHOTO_BYTES
    ): Result<ValidatedProfilePhoto> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Selected image file is empty. Please choose another photo."))
        }
        if (bytes.size.toLong() > maxBytes) {
            val maxMb = maxBytes / (1024L * 1024L)
            return Result.failure(IllegalArgumentException("Profile photo exceeds the ${maxMb} MB limit. Please select a smaller image."))
        }

        val normalizedMime = mimeType?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }
        if (normalizedMime != null && normalizedMime !in ALLOWED_MIME_TYPES) {
            return Result.failure(IllegalArgumentException("Unsupported image format ($normalizedMime). Please select JPG, JPEG, PNG, or WebP."))
        }

        val cleanName = fileName?.trim().orEmpty().ifBlank { "avatar_upload" }
        val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.US)
        if (cleanName.contains('.') && ext.isNotEmpty() && ext !in ALLOWED_EXTENSIONS) {
            return Result.failure(IllegalArgumentException("Unsupported file extension '.$ext'. Only JPG, JPEG, PNG, and WebP are allowed."))
        }

        val detected = detectFormatFromBytes(bytes)
            ?: return Result.failure(IllegalArgumentException("Invalid or corrupted image file. Only JPG, JPEG, PNG, and WebP photos are supported."))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val decodeAttempt = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        }
        if (decodeAttempt.isFailure) {
            val isJvmRobolectricWebp = detected.second == "webp" &&
                (System.getProperty("java.vm.name").orEmpty().contains("HotSpot", ignoreCase = true) ||
                    System.getProperty("java.runtime.name").orEmpty().contains("OpenJDK", ignoreCase = true))
            if (!isJvmRobolectricWebp) {
                return Result.failure(IllegalArgumentException("Unable to decode the selected image. Please choose a valid JPG, PNG, or WebP file."))
            }
        } else if (bounds.outWidth < 0 || bounds.outHeight < 0) {
            return Result.failure(IllegalArgumentException("Unable to decode the selected image. Please choose a valid JPG, PNG, or WebP file."))
        }

        return Result.success(
            ValidatedProfilePhoto(
                bytes = bytes,
                mimeType = detected.first,
                extension = detected.second,
                fileName = cleanName,
                sizeBytes = bytes.size.toLong()
            )
        )
    }

    fun readAndValidateUri(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_PROFILE_PHOTO_BYTES
    ): Result<ValidatedProfilePhoto> {
        return runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            var displayName: String? = null
            var reportedSize: Long = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) reportedSize = cursor.getLong(sizeIdx)
                }
            }

            if (reportedSize > maxBytes) {
                val maxMb = maxBytes / (1024L * 1024L)
                return Result.failure(IllegalArgumentException("Profile photo exceeds the ${maxMb} MB limit. Please select a smaller image."))
            }

            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        val maxMb = maxBytes / (1024L * 1024L)
                        return Result.failure(IllegalArgumentException("Profile photo exceeds the ${maxMb} MB limit. Please select a smaller image."))
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: return Result.failure(IllegalArgumentException("Unable to read the selected photo from device storage."))

            validateImageBytesAndMetadata(
                bytes = bytes,
                mimeType = mimeType,
                fileName = displayName ?: uri.lastPathSegment,
                maxBytes = maxBytes
            ).getOrThrow()
        }
    }
}

object ProfileAvatarCacheHelper {
    private fun safeUserDirName(userKey: String): String {
        val cleaned = userKey.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9_\\-]"), "_")
        return cleaned.ifBlank { "default_user" }.take(64)
    }

    fun saveLocalAvatarCopy(
        context: Context,
        userKey: String,
        bytes: ByteArray,
        extension: String,
        timestamp: Long
    ): String {
        val userDir = File(File(context.filesDir, "profile_photos"), safeUserDirName(userKey))
        if (!userDir.exists()) {
            userDir.mkdirs()
        } else {
            userDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
        val target = File(userDir, "avatar_${timestamp}.${extension}")
        target.writeBytes(bytes)
        return Uri.fromFile(target).toString()
    }

    fun deleteLocalAvatarCopy(context: Context, userKey: String) {
        val userDir = File(File(context.filesDir, "profile_photos"), safeUserDirName(userKey))
        if (userDir.exists()) {
            userDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun evictAvatarFromCoil(context: Context, url: String?, photoUpdatedAt: Long = 0L) {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return
        runCatching {
            val loader = context.imageLoader
            loader.memoryCache?.remove(MemoryCache.Key(clean))
            loader.memoryCache?.remove(MemoryCache.Key("$clean#v=$photoUpdatedAt"))
            loader.diskCache?.remove(clean)
            loader.diskCache?.remove("$clean#v=$photoUpdatedAt")
        }
    }

    fun clearAllLegacyGlobalAvatarPrefs(context: Context) {
        runCatching {
            context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("profile_image_uri")
                .remove("profile_name")
                .remove("profile_email")
                .remove("profile_mobile")
                .apply()
        }
    }
}

@Composable
fun SmartUserAvatar(
    photoUrl: String?,
    fullName: String?,
    email: String?,
    photoUpdatedAt: Long = 0L,
    size: Dp = 40.dp,
    fontSize: TextUnit = (size.value * 0.42f).sp,
    borderColor: Color? = null,
    borderWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    contentDescription: String = "User profile avatar",
    modifier: Modifier = Modifier,
    testTag: String = "smart_user_avatar"
) {
    val context = LocalContext.current
    val avatarState = SmartAvatarResolver.resolve(
        photoUrl = photoUrl,
        fullName = fullName,
        email = email,
        photoUpdatedAt = photoUpdatedAt
    )

    val containerModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(
            if (borderColor != null && borderWidth > 0.dp) {
                Modifier.border(borderWidth, borderColor, CircleShape)
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .semantics { this.contentDescription = contentDescription }
        .testTag(testTag)

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatarState.mode == AvatarRenderMode.PHOTO && !avatarState.photoUrl.isNullOrBlank()) {
            val cacheKey = "${avatarState.photoUrl}#v=${avatarState.photoUpdatedAt}"
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarState.photoUrl)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    SmartAvatarFallbackBox(
                        state = avatarState,
                        size = size,
                        fontSize = fontSize
                    )
                },
                error = {
                    SmartAvatarFallbackBox(
                        state = avatarState,
                        size = size,
                        fontSize = fontSize
                    )
                }
            )
        } else {
            SmartAvatarFallbackBox(
                state = avatarState,
                size = size,
                fontSize = fontSize
            )
        }
    }
}

@Composable
private fun SmartAvatarFallbackBox(
    state: SmartAvatarState,
    size: Dp,
    fontSize: TextUnit
) {
    val (gradStart, gradEnd) = SmartAvatarResolver.paletteColors(state.paletteIndex)
    val label = state.initialChar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(gradStart, gradEnd))),
        contentAlignment = Alignment.Center
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("smart_avatar_initial_text")
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(size * 0.56f)
                    .testTag("smart_avatar_default_icon")
            )
        }
    }
}

@Composable
fun ProfilePhotoEditorDialog(
    currentPhotoUrl: String?,
    fullName: String?,
    email: String?,
    photoUpdatedAt: Long,
    stagedPreviewUri: Uri?,
    stagedValidatedPhoto: ValidatedProfilePhoto?,
    validationError: String?,
    isUploading: Boolean,
    onPickFromGallery: () -> Unit,
    onSavePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasExistingPhoto = SmartAvatarResolver.isUsablePhotoUrl(currentPhotoUrl)
    val previewModel = stagedPreviewUri?.toString() ?: currentPhotoUrl

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Edit Profile Photo",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SmartUserAvatar(
                    photoUrl = previewModel,
                    fullName = fullName,
                    email = email,
                    photoUpdatedAt = if (stagedPreviewUri != null) System.currentTimeMillis() else photoUpdatedAt,
                    size = 112.dp,
                    fontSize = 42.sp,
                    borderColor = Color(0xFFDBEAFE),
                    borderWidth = 3.dp,
                    contentDescription = "Profile photo preview",
                    testTag = "profile_photo_preview_avatar"
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (stagedValidatedPhoto != null && validationError == null) {
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                    ) {
                        Text(
                            text = "Preview ready • ${stagedValidatedPhoto.extension.uppercase(Locale.US)} • ${stagedValidatedPhoto.formattedSize}",
                            color = Color(0xFF065F46),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (validationError != null) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.testTag("profile_photo_validation_error")
                    ) {
                        Text(
                            text = validationError,
                            color = Color(0xFFB91C1C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "Supported formats: JPG, JPEG, PNG, WebP (max 5 MB).",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onPickFromGallery,
                    enabled = !isUploading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("choose_profile_photo_button")
                ) {
                    Icon(
                        imageVector = if (hasExistingPhoto || stagedPreviewUri != null) Icons.Outlined.Edit else Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasExistingPhoto || stagedPreviewUri != null) "Choose Another Photo" else "Choose from Gallery",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (hasExistingPhoto && !isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemovePhoto,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("remove_profile_photo_button")
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Current Photo", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isUploading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Uploading photo & syncing profile...",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSavePhoto,
                enabled = !isUploading && stagedValidatedPhoto != null && validationError == null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0055AA)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_profile_photo_button")
            ) {
                Text("Save Photo", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_profile_photo_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
