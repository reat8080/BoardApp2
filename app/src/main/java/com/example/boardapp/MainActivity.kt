package com.example.boardapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.example.boardapp.data.BackgroundSyncWorker
import com.example.boardapp.data.BoardItem
import com.example.boardapp.data.ItemDao
import com.example.boardapp.data.SyncManager
import com.example.boardapp.ui.DisplayScreen
import com.example.boardapp.ui.EditScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val CalmLiteBlue = Color(0xFFEDF6FD)

class MainActivity : ComponentActivity() {
    private lateinit var audioPlayer: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val dao = db.itemDao()
        audioPlayer = AudioPlayer(this)

        BackgroundSyncWorker.triggerInitialSync(this)
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
    val prefs = remember { context.getSharedPreferences("board_settings", Context.MODE_PRIVATE) }
    var isPersian by remember { mutableStateOf(prefs.getBoolean("is_persian", true)) }
    var scaleFactor by remember { mutableFloatStateOf(prefs.getFloat("scale_factor", 1.0f)) }

    var isEditMode by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BoardItem?>(null) }
    var currentParentId by remember { mutableStateOf<Long?>(null) }
    val backStack = remember { mutableStateListOf<Long?>() }

    val currentItems by dao.getItemsByParent(currentParentId).collectAsState(initial = emptyList())
    val allItems by dao.getAllItems().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val serverBaseUrl = "https://omidanit.ca/boardapp"
    val syncManager = remember { SyncManager(context, serverBaseUrl) }

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
                isPersian = isPersian,
                scaleFactor = scaleFactor,
                isSyncing = isSyncing,
                onToggleLanguage = {
                    isPersian = !isPersian
                    prefs.edit().putBoolean("is_persian", isPersian).apply()
                },
                onChangeScale = { newScale ->
                    scaleFactor = newScale
                    prefs.edit().putFloat("scale_factor", newScale).apply()
                },
                onManualSync = {
                    coroutineScope.launch {
                        isSyncing = true
                        val success = syncManager.pullData(dao)
                        isSyncing = false
                        Toast.makeText(
                            context,
                            if (success) (if (isPersian) "همگام‌سازی با موفقیت انجام شد" else "Sync completed successfully")
                            else (if (isPersian) "خطا در برقراری ارتباط با سرور" else "Sync failed"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onSave = { id, titleFa, titleEn, imgUri, audFa, audEn, parentId ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val localImagePath = syncManager.copyUriToInternalStorage(imgUri, "img")
                        val localAudFa = if (audFa.isNotBlank()) syncManager.copyUriToInternalStorage(audFa, "aud_fa") else ""
                        val localAudEn = if (audEn.isNotBlank()) syncManager.copyUriToInternalStorage(audEn, "aud_en") else ""

                        dao.insertItem(
                            BoardItem(
                                id = id ?: 0,
                                titleFa = titleFa,
                                titleEn = titleEn,
                                imageUri = localImagePath,
                                audioUriFa = localAudFa,
                                audioUriEn = localAudEn,
                                parentId = parentId
                            )
                        )
                        itemToEdit = null

                        val updatedList = dao.getAllItems().first()
                        syncManager.pushData(updatedList)
                    }
                },
                onDelete = { item ->
                    coroutineScope.launch(Dispatchers.IO) {
                        dao.deleteItem(item)
                        if (itemToEdit?.id == item.id) {
                            itemToEdit = null
                        }
                        val updatedList = dao.getAllItems().first()
                        syncManager.pushData(updatedList)
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
                isPersian = isPersian,
                scaleFactor = scaleFactor,
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
