package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFirestoreDatabaseTab(
    adminViewModel: AdminViewModel,
    auditLogs: List<AuditLogEntity>
) {
    val context = LocalContext.current
    val databaseId = stringResource(id = R.string.firestore_database_id)
    val pageState by adminViewModel.firestorePageState.collectAsStateWithLifecycle()
    val isLoading by adminViewModel.firestoreLoading.collectAsStateWithLifecycle()
    val errorMsg by adminViewModel.firestoreError.collectAsStateWithLifecycle()
    val exportedJson by adminViewModel.exportedCollectionJson.collectAsStateWithLifecycle()

    var selectedCollection by remember { mutableStateOf("users") }
    var searchQuery by remember { mutableStateOf("") }
    var filterFieldKey by remember { mutableStateOf("") }
    var filterFieldValue by remember { mutableStateOf("") }
    var pageSize by remember { mutableIntStateOf(10) }
    var currentPage by remember { mutableIntStateOf(0) }

    var showAuditDrawer by remember { mutableStateOf(false) }
    var showCreateDocModal by remember { mutableStateOf(false) }
    var docToEdit by remember { mutableStateOf<FirestoreDocumentRecord?>(null) }
    var docToDelete by remember { mutableStateOf<FirestoreDocumentRecord?>(null) }
    var showImportModal by remember { mutableStateOf(false) }

    val currentMeta = remember(selectedCollection) {
        FirestoreCollectionAllowlist.getMeta(selectedCollection)
            ?: FirestoreCollectionAllowlist.ALLOWLISTED_COLLECTIONS.first()
    }

    LaunchedEffect(selectedCollection, pageSize) {
        currentPage = 0
        adminViewModel.loadFirestoreCollection(
            collectionPath = selectedCollection,
            searchQuery = searchQuery,
            fieldFilterKey = filterFieldKey,
            fieldFilterValue = filterFieldValue,
            pageIndex = 0,
            pageSize = pageSize
        )
    }

    val dbAuditLogs = remember(auditLogs) {
        auditLogs.filter {
            it.actionType.startsWith("FIRESTORE_") ||
                it.actionType.startsWith("DB_") ||
                it.actionType.contains("CONFIG") ||
                it.actionType.contains("USER_") ||
                it.actionType.contains("UNAUTHORIZED")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Card: Provisioned Firestore Database Info & Security Allowlist Status (Clean Light Card)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.5.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Surface(
                                color = Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Storage,
                                        contentDescription = "Cloud Firestore",
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Firebase Cloud Firestore Explorer",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "DB ID: $databaseId",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFF2563EB),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = pageState?.source ?: "FIRESTORE_READY",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Per-Collection Operation Permissions Matrix Banner (Horizontally Scrollable so badges never squish)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PermissionBadge("READ: ALLOWED", true)
                        PermissionBadge(
                            if (currentMeta.canCreate) "CREATE: ALLOWED" else "CREATE: LOCKED",
                            currentMeta.canCreate
                        )
                        PermissionBadge(
                            if (currentMeta.canUpdate) "UPDATE: ALLOWED" else "APPEND-ONLY",
                            currentMeta.canUpdate
                        )
                        PermissionBadge(
                            if (currentMeta.canDelete) "DELETE: ALLOWED" else "DELETE: LOCKED",
                            currentMeta.canDelete
                        )
                    }
                }
            }
        }

        // 2. Allowlisted Collections Browser + Protected System Collections Guard
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Authorized Firestore Collections (${FirestoreCollectionAllowlist.ALLOWLISTED_COLLECTIONS.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FirestoreCollectionAllowlist.ALLOWLISTED_COLLECTIONS.forEach { meta ->
                            val selected = selectedCollection == meta.collectionPath
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCollection = meta.collectionPath
                                    searchQuery = ""
                                    filterFieldKey = ""
                                    filterFieldValue = ""
                                },
                                label = {
                                    Text(
                                        meta.collectionPath,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (meta.canUpdate) Icons.Outlined.FolderOpen else Icons.Outlined.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("collection_chip_${meta.collectionPath}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        currentMeta.description,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // Protected System Collections Row (Demonstrates Zero-Trust Allowlist Enforcement)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Protected System Paths:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )
                        FirestoreCollectionAllowlist.PROTECTED_SYSTEM_COLLECTIONS.forEach { protectedCol ->
                            AssistChip(
                                onClick = {
                                    adminViewModel.loadFirestoreCollection(protectedCol)
                                },
                                label = { Text(protectedCol, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.GppBad,
                                        contentDescription = "Blocked",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("protected_col_$protectedCol")
                            )
                        }
                    }
                }
            }
        }

        // 3. Error & Loading Status Banner
        if (errorMsg != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFF87171)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg ?: "",
                                color = Color(0xFF991B1B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { adminViewModel.clearFirestoreError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF991B1B))
                        }
                    }
                }
            }
        }

        // 4. Search, Field Filter, and Action Bar (Add Document, Export JSON, Import JSON, Audit Trail)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentPage = 0
                                adminViewModel.loadFirestoreCollection(
                                    collectionPath = selectedCollection,
                                    searchQuery = searchQuery,
                                    fieldFilterKey = filterFieldKey,
                                    fieldFilterValue = filterFieldValue,
                                    pageIndex = 0,
                                    pageSize = pageSize
                                )
                            },
                            placeholder = { Text("Search doc ID or field values...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("firestore_search_input")
                        )

                        IconButton(
                            onClick = {
                                adminViewModel.loadFirestoreCollection(
                                    collectionPath = selectedCollection,
                                    searchQuery = searchQuery,
                                    fieldFilterKey = filterFieldKey,
                                    fieldFilterValue = filterFieldValue,
                                    pageIndex = currentPage,
                                    pageSize = pageSize
                                )
                            },
                            modifier = Modifier.testTag("firestore_refresh_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Collection")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Field-level key/value filter
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = filterFieldKey,
                                onValueChange = { filterFieldKey = it },
                                label = { Text("Filter Field Key", fontSize = 11.sp) },
                                placeholder = { Text(currentMeta.fields.firstOrNull()?.name ?: "status", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = filterFieldValue,
                                onValueChange = { filterFieldValue = it },
                                label = { Text("Filter Value", fontSize = 11.sp) },
                                placeholder = { Text("ACTIVE", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                currentPage = 0
                                adminViewModel.loadFirestoreCollection(
                                    collectionPath = selectedCollection,
                                    searchQuery = searchQuery,
                                    fieldFilterKey = filterFieldKey,
                                    fieldFilterValue = filterFieldValue,
                                    pageIndex = 0,
                                    pageSize = pageSize
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Text("Apply", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CRUD + Export/Import + Audit Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCreateDocModal = true },
                            enabled = currentMeta.canCreate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("firestore_add_doc_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Document", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { adminViewModel.exportFirestoreCollection(selectedCollection) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("firestore_export_button")
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSON", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showImportModal = true },
                            enabled = currentMeta.canCreate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("firestore_import_button")
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import JSON", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { showAuditDrawer = !showAuditDrawer },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("firestore_audit_toggle_button")
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showAuditDrawer) "Hide Audit (${dbAuditLogs.size})" else "DB Audit (${dbAuditLogs.size})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 5. Optional Collapsible Database Audit History Panel
        if (showAuditDrawer) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Sensitive Database Operations Audit History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        dbAuditLogs.take(8).forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${log.actionType} • ${log.targetEntity} (by ${log.actorUsername})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        log.details,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    formatAdminTimestamp(log.timestamp),
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }

        // 6. Pagination Bar & Loading Indicator
        item {
            val total = pageState?.totalMatched ?: 0
            val docsCount = pageState?.documents?.size ?: 0
            val pIndex = pageState?.pageIndex ?: 0
            val maxPage = if (total == 0) 1 else ((total - 1) / pageSize) + 1

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "/$selectedCollection ($docsCount of $total docs • Page ${pIndex + 1}/$maxPage)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 25).forEach { sz ->
                            Surface(
                                color = if (pageSize == sz) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { pageSize = sz }
                            ) {
                                Text(
                                    "${sz}/pg",
                                    fontSize = 11.sp,
                                    color = if (pageSize == sz) Color(0xFF1D4ED8) else Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (currentPage > 0) {
                                    currentPage -= 1
                                    adminViewModel.loadFirestoreCollection(
                                        selectedCollection, searchQuery, filterFieldKey, filterFieldValue, currentPage, pageSize
                                    )
                                }
                            },
                            enabled = pageState?.hasPrevPage == true,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                        }
                        IconButton(
                            onClick = {
                                if (pageState?.hasNextPage == true) {
                                    currentPage += 1
                                    adminViewModel.loadFirestoreCollection(
                                        selectedCollection, searchQuery, filterFieldKey, filterFieldValue, currentPage, pageSize
                                    )
                                }
                            },
                            enabled = pageState?.hasNextPage == true,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                        }
                    }
                }
            }
        }

        // 7. Document List
        val docs = pageState?.documents.orEmpty()
        if (docs.isEmpty() && !isLoading) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No documents matched in /$selectedCollection.",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(docs, key = { "${it.collectionPath}/${it.documentId}" }) { doc ->
                FirestoreDocumentCard(
                    doc = doc,
                    meta = currentMeta,
                    onEdit = { docToEdit = doc },
                    onDelete = { docToDelete = doc }
                )
            }
        }
    }

    // --- Create / Edit Document Dialog (Supports Primitives, Nested Maps & Arrays) ---
    if (showCreateDocModal || docToEdit != null) {
        val editingDoc = docToEdit
        val isCreate = editingDoc == null
        FirestoreDocumentEditorDialog(
            meta = currentMeta,
            existingDoc = editingDoc,
            isCreate = isCreate,
            onDismiss = {
                showCreateDocModal = false
                docToEdit = null
            },
            onSave = { docId, fieldMap ->
                adminViewModel.upsertFirestoreDocument(
                    collectionPath = selectedCollection,
                    documentId = docId,
                    rawFieldValues = fieldMap,
                    isCreate = isCreate,
                    currentSearch = searchQuery,
                    currentFilterKey = filterFieldKey,
                    currentFilterVal = filterFieldValue,
                    currentPage = currentPage,
                    currentPageSize = pageSize
                ) { success ->
                    if (success) {
                        showCreateDocModal = false
                        docToEdit = null
                    }
                }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    docToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = {
                Text("Delete Firestore Document?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Path: /${target.collectionPath}/${target.documentId}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFDC2626)
                    )
                    Text(
                        "This permanently removes the document from Cloud Firestore and logs a CRITICAL security audit event.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteFirestoreDocument(
                            collectionPath = target.collectionPath,
                            documentId = target.documentId,
                            currentSearch = searchQuery,
                            currentFilterKey = filterFieldKey,
                            currentFilterVal = filterFieldValue,
                            currentPage = currentPage,
                            currentPageSize = pageSize
                        )
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_doc_button")
                ) {
                    Text("Delete Document")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Exported JSON Viewer Dialog ---
    exportedJson?.let { jsonText ->
        AlertDialog(
            onDismissRequest = { adminViewModel.clearExportedJson() },
            title = { Text("Exported /$selectedCollection JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonText,
                            color = Color(0xFF0F172A),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { adminViewModel.clearExportedJson() }) {
                    Text("Done")
                }
            }
        )
    }

    // --- Import JSON Dialog with Validation & Limits ---
    if (showImportModal) {
        val samplePayload = remember(selectedCollection) {
            val sampleFields = JSONObject()
            currentMeta.fields.forEach { spec ->
                when (spec.type) {
                    "INT" -> sampleFields.put(spec.name, spec.defaultHint.toLongOrNull() ?: 1L)
                    "DOUBLE" -> sampleFields.put(spec.name, spec.defaultHint.toDoubleOrNull() ?: 15.0)
                    "BOOLEAN" -> sampleFields.put(spec.name, spec.defaultHint.lowercase() == "true")
                    "MAP" -> sampleFields.put(spec.name, JSONObject().put("imported", true))
                    "ARRAY" -> sampleFields.put(spec.name, JSONArray().put("imported-tag"))
                    else -> sampleFields.put(spec.name, spec.defaultHint.ifBlank { "sample_val" })
                }
            }
            JSONObject().apply {
                put("collection", selectedCollection)
                put(
                    "documents",
                    JSONArray().put(
                        JSONObject()
                            .put("documentId", "${selectedCollection}_imported_1")
                            .put("fields", sampleFields)
                    )
                )
            }.toString(2)
        }
        var rawImportJson by remember { mutableStateOf(samplePayload) }

        AlertDialog(
            onDismissRequest = { showImportModal = false },
            title = { Text("Import Validated JSON into /$selectedCollection", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Limits: Max 50 documents per batch, max 256 KB payload. Every document ID and field type is validated against the collection schema.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = rawImportJson,
                        onValueChange = { rawImportJson = it },
                        label = { Text("JSON Batch Payload") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        minLines = 8,
                        maxLines = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firestore_import_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.importFirestoreCollection(selectedCollection, rawImportJson) { ok ->
                            if (ok) showImportModal = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_import_json_button")
                ) {
                    Text("Validate & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionBadge(text: String, allowed: Boolean) {
    Surface(
        color = if (allowed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
        border = BorderStroke(1.dp, if (allowed) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = if (allowed) Color(0xFF166534) else Color(0xFF991B1B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FirestoreDocumentCard(
    doc: FirestoreDocumentRecord,
    meta: FirestoreCollectionMeta,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firestore_doc_${doc.documentId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${doc.collectionPath}/${doc.documentId}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Updated ${formatAdminTimestamp(doc.updatedAtMillis)}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Render Document Fields (highlighting nested Maps and Arrays)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                doc.data.entries.filter { !it.key.startsWith("_") }.forEach { (key, value) ->
                    val typeTag = when (value) {
                        is Map<*, *> -> "MAP"
                        is List<*> -> "ARRAY"
                        is Boolean -> "BOOL"
                        is Number -> "NUM"
                        else -> "STR"
                    }
                    val formattedVal = FirestoreDatabaseRepository.formatFieldValueForEditor(value)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$key: ",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = formattedVal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (typeTag == "MAP" || typeTag == "ARRAY") Color(0xFF0369A1) else Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = when (typeTag) {
                                "MAP" -> Color(0xFFE0F2FE)
                                "ARRAY" -> Color(0xFFF3E8FF)
                                "BOOL" -> Color(0xFFDCFCE7)
                                else -> Color(0xFFF1F5F9)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = typeTag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (typeTag) {
                                    "MAP" -> Color(0xFF0369A1)
                                    "ARRAY" -> Color(0xFF6D28D9)
                                    "BOOL" -> Color(0xFF15803D)
                                    else -> Color(0xFF475569)
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("edit_doc_${doc.documentId}")
                ) {
                    Icon(
                        imageVector = if (meta.canUpdate) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (meta.canUpdate) "Edit Fields" else "Inspect (Read-Only)",
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                if (meta.canDelete) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("delete_doc_${doc.documentId}")
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete Document",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirestoreDocumentEditorDialog(
    meta: FirestoreCollectionMeta,
    existingDoc: FirestoreDocumentRecord?,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit
) {
    var docId by remember(existingDoc) {
        mutableStateOf(
            existingDoc?.documentId ?: "${meta.collectionPath.take(4)}_${System.currentTimeMillis() % 10000}"
        )
    }

    val fieldStates = remember(existingDoc, meta) {
        mutableStateMapOf<String, String>().apply {
            meta.fields.forEach { spec ->
                val initial = if (existingDoc != null && existingDoc.data.containsKey(spec.name)) {
                    FirestoreDatabaseRepository.formatFieldValueForEditor(existingDoc.data[spec.name])
                } else {
                    spec.defaultHint
                }
                put(spec.name, initial)
            }
        }
    }

    // Helper state for interactive Nested Map key/value addition
    var newMapKey by remember { mutableStateOf("") }
    var newMapVal by remember { mutableStateOf("") }
    // Helper state for interactive Array item addition
    var newArrayTag by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCreate) "Add Document to /${meta.collectionPath}"
                else if (meta.canUpdate) "Edit /${meta.collectionPath}/${existingDoc?.documentId}"
                else "Inspect /${meta.collectionPath}/${existingDoc?.documentId} (Immutable)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = docId,
                    onValueChange = { if (isCreate) docId = it },
                    enabled = isCreate,
                    label = { Text("Document ID (alphanumeric, -, _)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firestore_editor_doc_id")
                )

                meta.fields.forEach { spec ->
                    val currentVal = fieldStates[spec.name] ?: ""
                    val canEditField = if (isCreate) true else (meta.canUpdate && spec.editable)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${spec.name} (${spec.type})${if (spec.required) " *" else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E293B)
                            )
                            if (!canEditField) {
                                Text("IMMUTABLE", fontSize = 10.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (spec.allowedValues.isNotEmpty() && canEditField) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                spec.allowedValues.forEach { option ->
                                    FilterChip(
                                        selected = currentVal == option,
                                        onClick = { fieldStates[spec.name] = option },
                                        label = { Text(option, fontSize = 11.sp) }
                                    )
                                }
                            }
                        } else if (spec.type == "BOOLEAN" && canEditField) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = currentVal.lowercase() == "true",
                                    onCheckedChange = { fieldStates[spec.name] = it.toString() }
                                )
                                Text(if (currentVal.lowercase() == "true") "true" else "false", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedTextField(
                                value = currentVal,
                                onValueChange = { if (canEditField) fieldStates[spec.name] = it },
                                enabled = canEditField,
                                singleLine = spec.type != "MAP" && spec.type != "ARRAY",
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = if (spec.type == "MAP" || spec.type == "ARRAY") FontFamily.Monospace else FontFamily.Default,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editor_field_${spec.name}")
                            )
                        }

                        // Interactive Nested Map Builder
                        if (spec.type == "MAP" && canEditField) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newMapKey,
                                    onValueChange = { newMapKey = it },
                                    placeholder = { Text("Map key", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newMapVal,
                                    onValueChange = { newMapVal = it },
                                    placeholder = { Text("Value", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (newMapKey.isNotBlank()) {
                                            val json = try {
                                                JSONObject(currentVal.ifBlank { "{}" })
                                            } catch (_: Exception) {
                                                JSONObject()
                                            }
                                            json.put(newMapKey.trim(), newMapVal.trim())
                                            fieldStates[spec.name] = json.toString()
                                            newMapKey = ""
                                            newMapVal = ""
                                        }
                                    }
                                ) {
                                    Text("+KV", fontSize = 11.sp)
                                }
                            }
                        }

                        // Interactive Array Item Builder
                        if (spec.type == "ARRAY" && canEditField) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newArrayTag,
                                    onValueChange = { newArrayTag = it },
                                    placeholder = { Text("Add array item...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (newArrayTag.isNotBlank()) {
                                            val arr = try {
                                                JSONArray(currentVal.ifBlank { "[]" })
                                            } catch (_: Exception) {
                                                JSONArray()
                                            }
                                            arr.put(newArrayTag.trim())
                                            fieldStates[spec.name] = arr.toString()
                                            newArrayTag = ""
                                        }
                                    }
                                ) {
                                    Text("+Item", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreate || meta.canUpdate) {
                Button(
                    onClick = { onSave(docId, fieldStates.toMap()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.testTag("save_firestore_doc_button")
                ) {
                    Text(if (isCreate) "Create Document" else "Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
