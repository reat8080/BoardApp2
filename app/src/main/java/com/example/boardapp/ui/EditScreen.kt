package com.example.boardapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.boardapp.audio.AudioRecorder
import com.example.boardapp.data.BoardItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    context: Context,
    allItems: List<BoardItem>,
    editingItem: BoardItem?,
    isPersian: Boolean,
    scaleFactor: Float,
    isSyncing: Boolean,
    onToggleLanguage: () -> Unit,
    onChangeScale: (Float) -> Unit,
    onManualSync: () -> Unit,
    onSave: (id: Long?, titleFa: String, titleEn: String, imgUri: String, audFa: String, audEn: String, parentId: Long?) -> Unit,
    onDelete: (BoardItem) -> Unit,
    onSelectToEdit: (BoardItem) -> Unit,
    onCancelEditItem: () -> Unit,
    onExitEditMode: () -> Unit,
    onPersistUri: (Uri) -> Unit
) {
    var titleFa by remember { mutableStateOf("") }
    var titleEn by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var audioUriFa by remember { mutableStateOf("") }
    var audioUriEn by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // نگهدارنده زبانی که هم‌اکنون در حال ضبط است ("FA" یا "EN")
    var recordingTarget by remember { mutableStateOf<String?>(null) }
    val audioRecorder = remember { AudioRecorder(context) }

    LaunchedEffect(editingItem) {
        if (editingItem != null) {
            titleFa = editingItem.titleFa
            titleEn = editingItem.titleEn
            imageUri = editingItem.imageUri
            audioUriFa = editingItem.audioUriFa
            audioUriEn = editingItem.audioUriEn
            selectedParentId = editingItem.parentId
        } else {
            titleFa = ""
            titleEn = ""
            imageUri = null
            audioUriFa = ""
            audioUriEn = ""
            selectedParentId = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onPersistUri(it)
            imageUri = it.toString()
        }
    }

    val audioFaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onPersistUri(it)
            audioUriFa = it.toString()
        }
    }

    val audioEnPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onPersistUri(it)
            audioUriEn = it.toString()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (audioRecorder.startRecording()) {
                // ضبط شروع شد
            } else {
                recordingTarget = null
            }
        } else {
            Toast.makeText(context, if (isPersian) "دسترسی میکروفون رد شد" else "Microphone permission denied", Toast.LENGTH_SHORT).show()
            recordingTarget = null
        }
    }

    fun toggleRecording(target: String) {
        if (recordingTarget == null) {
            // شروع ضبط
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                if (audioRecorder.startRecording()) {
                    recordingTarget = target
                }
            } else {
                recordingTarget = target
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else if (recordingTarget == target) {
            // توقف و ثبت مسیر
            val savedPath = audioRecorder.stopRecording()
            if (!savedPath.isNullOrBlank()) {
                if (target == "FA") {
                    audioUriFa = savedPath
                } else if (target == "EN") {
                    audioUriEn = savedPath
                }
            }
            recordingTarget = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPersian) "تنظیمات و ساخت" else "Settings & Create",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1)
                )
                Button(
                    onClick = onExitEditMode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text(if (isPersian) "بازگشت به نمایش" else "Back to Display")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isPersian) "تنظیمات عمومی:" else "General Settings:",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onToggleLanguage,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                        ) {
                            Text(if (isPersian) "زبان: فارسی (سوئیچ به EN)" else "Language: English (Switch to FA)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onManualSync,
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isSyncing) (if (isPersian) "در حال سینک..." else "Syncing...")
                                else (if (isPersian) "سینک با سرور" else "Sync Server"),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = (if (isPersian) "اندازه کارت‌ها: " else "Card Scale: ") + "${scaleFactor}x",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { scale ->
                            FilterChip(
                                selected = (scaleFactor == scale),
                                onClick = { onChangeScale(scale) },
                                label = { Text("${scale}x", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (editingItem != null) (if (isPersian) "ویرایش جایگاه" else "Edit Item") else (if (isPersian) "افزودن جایگاه جدید" else "Add New Item"),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )

                    OutlinedTextField(
                        value = titleFa,
                        onValueChange = { titleFa = it },
                        label = { Text(if (isPersian) "عنوان فارسی" else "Persian Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = titleEn,
                        onValueChange = { titleEn = it },
                        label = { Text(if (isPersian) "عنوان انگلیسی" else "English Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (imageUri == null) Color(0xFF1976D2) else Color(0xFF388E3C))
                    ) {
                        Text(if (imageUri == null) (if (isPersian) "انتخاب عکس (الزامی)" else "Select Image (Required)") else (if (isPersian) "عکس انتخاب شد ✓" else "Image Selected ✓"))
                    }

                    // صوت فارسی
                    Text(if (isPersian) "صوت فارسی:" else "Persian Audio:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { audioFaPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (audioUriFa.isBlank()) Color(0xFF1976D2) else Color(0xFF388E3C))
                        ) {
                            Text(if (audioUriFa.isBlank()) (if (isPersian) "فایل صوت FA" else "Audio File FA") else "FA ✓", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { toggleRecording("FA") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (recordingTarget == "FA") Color(0xFFD32F2F) else Color(0xFF0288D1))
                        ) {
                            Text(if (recordingTarget == "FA") (if (isPersian) "■ توقف FA" else "■ Stop FA") else (if (isPersian) "● ضبط FA" else "● Rec FA"), fontSize = 11.sp)
                        }

                        if (audioUriFa.isNotBlank()) {
                            IconButton(onClick = { audioUriFa = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }

                    // صوت انگلیسی
                    Text(if (isPersian) "صوت انگلیسی:" else "English Audio:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { audioEnPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (audioUriEn.isBlank()) Color(0xFF1976D2) else Color(0xFF388E3C))
                        ) {
                            Text(if (audioUriEn.isBlank()) (if (isPersian) "فایل صوت EN" else "Audio File EN") else "EN ✓", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { toggleRecording("EN") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (recordingTarget == "EN") Color(0xFFD32F2F) else Color(0xFF0288D1))
                        ) {
                            Text(if (recordingTarget == "EN") (if (isPersian) "■ توقف EN" else "■ Stop EN") else (if (isPersian) "● ضبط EN" else "● Rec EN"), fontSize = 11.sp)
                        }

                        if (audioUriEn.isNotBlank()) {
                            IconButton(onClick = { audioUriEn = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }

                    // جایگاه مادر
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        val parentName = allItems.find { it.id == selectedParentId }?.getTitle(isPersian)
                            ?: (if (isPersian) "بدون مادر (صفحه اصلی)" else "Root (Main Page)")
                        OutlinedTextField(
                            value = parentName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isPersian) "جایگاه والد" else "Parent Item") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isPersian) "بدون مادر (صفحه اصلی)" else "Root (Main Page)") },
                                onClick = { selectedParentId = null; expanded = false }
                            )
                            allItems.filter { it.id != editingItem?.id }.forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text(parent.getTitle(isPersian)) },
                                    onClick = { selectedParentId = parent.id; expanded = false }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingItem != null) {
                            OutlinedButton(onClick = onCancelEditItem, modifier = Modifier.weight(1f)) {
                                Text(if (isPersian) "لغو" else "Cancel")
                            }
                        }
                        val canSave = (titleFa.isNotBlank() || titleEn.isNotBlank()) && imageUri != null
                        Button(
                            onClick = {
                                if (canSave) {
                                    onSave(editingItem?.id, titleFa, titleEn, imageUri!!, audioUriFa, audioUriEn, selectedParentId)
                                    titleFa = ""
                                    titleEn = ""
                                    imageUri = null
                                    audioUriFa = ""
                                    audioUriEn = ""
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text(if (editingItem != null) (if (isPersian) "ثبت تغییرات" else "Update") else (if (isPersian) "ذخیره جایگاه" else "Save Item"))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (isPersian) "مدیریت جایگاه‌های ثبت شده:" else "Manage Existing Items:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1)
            )
        }

        items(allItems.size) { index ->
            val item = allItems[index]
            val parentName = allItems.find { it.id == item.parentId }?.getTitle(isPersian) ?: (if (isPersian) "صفحه اصلی" else "Root")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = item.imageUri,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "FA: ${item.titleFa} | EN: ${item.titleEn}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "صوت: " + (if (item.audioUriFa.isNotBlank()) "FA ✓ " else "FA ✕ ") + (if (item.audioUriEn.isNotBlank()) "EN ✓" else "EN ✕"),
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0)
                        )
                        Text(text = "${if (isPersian) "والد:" else "Parent:"} $parentName", fontSize = 11.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { onSelectToEdit(item) }) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1976D2))
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}
