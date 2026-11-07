package com.example.boardapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.boardapp.audio.AudioPlayer
import com.example.boardapp.data.AppDatabase
import com.example.boardapp.data.BoardItem
import com.example.boardapp.data.ItemDao
import com.example.boardapp.ui.DisplayScreen
import com.example.boardapp.ui.EditScreen
import kotlinx.coroutines.launch

val CalmLiteBlue = Color(0xFFEDF6FD)

class MainActivity : ComponentActivity() {
    private lateinit var audioPlayer: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val dao = db.itemDao()
        audioPlayer = AudioPlayer(this)

            // ۱. بررسی و دانلود اطلاعات اولیه در اولین نصب یا راه‌اندازی
        BackgroundSyncWorker.triggerInitialSync(this)
        
        // ۲. برنامه‌ریزی برای چک روزانه در پس‌زمینه بدون اختلال در کار کاربر
        BackgroundSyncWorker.scheduleDailySync(this)

        setContent {
            MaterialTheme {
                MainApp(dao = dao, audioPlayer = audioPlayer, context = this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
    }
}

@Composable
fun MainApp(dao: ItemDao, audioPlayer: AudioPlayer, context: Context) {
    var isEditMode by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BoardItem?>(null) }
    var currentParentId by remember { mutableStateOf<Long?>(null) }
    val backStack = remember { mutableStateListOf<Long?>() }

    val currentItems by dao.getItemsByParent(currentParentId).collectAsState(initial = emptyList())
    val allItems by dao.getAllItems().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CalmLiteBlue)
    ) {
        if (isEditMode) {
            EditScreen(
                context = context,
                allItems = allItems,
                editingItem = itemToEdit,
                onSave = { id, title, imgUri, audUri, parentId ->
                    coroutineScope.launch {
                        dao.insertItem(
                            BoardItem(
                                id = id ?: 0,
                                title = title,
                                imageUri = imgUri,
                                audioUri = audUri,
                                parentId = parentId
                            )
                        )
                        itemToEdit = null
                    }
                },
                onDelete = { item ->
                    coroutineScope.launch {
                        dao.deleteItem(item)
                        if (itemToEdit?.id == item.id) {
                            itemToEdit = null
                        }
                    }
                },
                onSelectToEdit = { item -> itemToEdit = item },
                onCancelEditItem = { itemToEdit = null },
                onExitEditMode = {
                    itemToEdit = null
                    isEditMode = false
                },
                onPersistUri = { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }
            )
        } else {
            DisplayScreen(
                items = currentItems,
                allItems = allItems,
                isRootScreen = backStack.isEmpty(),
                audioPlayer = audioPlayer,
                onNavigateToChild = { childId ->
                    backStack.add(currentParentId)
                    currentParentId = childId
                },
                onBack = {
                    if (backStack.isNotEmpty()) {
                        currentParentId = backStack.removeAt(backStack.size - 1)
                    }
                },
                onUnlockEditMode = { isEditMode = true }
            )
        }
    }
}
