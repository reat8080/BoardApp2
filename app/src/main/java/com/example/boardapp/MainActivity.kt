package com.example.boardapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.room.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

// 1. Entity
@Entity(tableName = "items")
data class BoardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val imageUri: String,
    val audioUri: String = "",
    val parentId: Long? = null
)

// 2. DAO
@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE (:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId")
    fun getItemsByParent(parentId: Long?): Flow<List<BoardItem>>

    @Query("SELECT * FROM items")
    fun getAllItems(): Flow<List<BoardItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BoardItem)

    @Delete
    suspend fun deleteItem(item: BoardItem)
}

// 3. Database
@Database(entities = [BoardItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "board_db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}

// 4. Audio Player
class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    fun playAudio(uriString: String) {
        if (uriString.isBlank()) return
        player?.release()
        try {
            player = MediaPlayer().apply {
                setDataSource(context, Uri.parse(uriString))
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// 5. Audio Recorder Helper
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var currentOutputFile: File? = null

    fun startRecording(): Boolean {
        return try {
            val audioDir = File(context.filesDir, "audios")
            if (!audioDir.exists()) audioDir.mkdirs()
            currentOutputFile = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentOutputFile!!.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun stopRecording(): String? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentOutputFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val dao = db.itemDao()
        val audioPlayer = AudioPlayer(this)

        setContent {
            MaterialTheme {
                MainApp(dao = dao, audioPlayer = audioPlayer, context = this)
            }
        }
    }
}

// رنگ آبی روشن ملایم و کودک‌پسند
val CalmLiteBlue = Color(0xFFEDF6FD)

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
                onSelectToEdit = { item ->
                    itemToEdit = item
                },
                onCancelEditItem = {
                    itemToEdit = null
                },
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
                onUnlockEditMode = {
                    isEditMode = true
                }
            )
        }
    }
}

// --- حالت نمایش ---
@Composable
fun DisplayScreen(
    items: List<BoardItem>,
    allItems: List<BoardItem>,
    isRootScreen: Boolean,
    audioPlayer: AudioPlayer,
    onNavigateToChild: (Long) -> Unit,
    onBack: () -> Unit,
    onUnlockEditMode: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isRootScreen) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("بازگشت", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // شبکه ریسپانسیو: در گوشی ۲ ستون، در تبلت خودکار ۳ یا ۴ ستون متناسب ایجاد می‌کند
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 145.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = if (isRootScreen) 80.dp else 16.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.audioUri.isNotBlank()) {
                                    audioPlayer.playAudio(item.audioUri)
                                }
                                if (allItems.any { it.parentId == item.id }) {
                                    onNavigateToChild(item.id)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = item.imageUri,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // دکمه تنظیمات با اندازه ۱/۴ بقیه (کوچک و ظریف در پایین صفحه) فقط در صفحه اصلی
        if (isRootScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(54.dp) // اندازه یک‌چهارم کارت‌های اصلی
                    .clip(CircleShape)
                    .background(Color(0xFFBBDEFB).copy(alpha = 0.85f))
                    .border(1.5.dp, Color(0xFF90CAF9), CircleShape)
                    .clickable {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 1000) {
                            clickCount++
                        } else {
                            clickCount = 1
                        }
                        lastClickTime = currentTime

                        if (clickCount >= 3) {
                            clickCount = 0
                            onUnlockEditMode()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "تنظیمات",
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF0D47A1)
                )
            }
        }
    }
}

// --- حالت ساخت، ویرایش و مدیریت ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    context: Context,
    allItems: List<BoardItem>,
    editingItem: BoardItem?,
    onSave: (id: Long?, title: String, imgUri: String, audUri: String, parentId: Long?) -> Unit,
    onDelete: (BoardItem) -> Unit,
    onSelectToEdit: (BoardItem) -> Unit,
    onCancelEditItem: () -> Unit,
    onExitEditMode: () -> Unit,
    onPersistUri: (Uri) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var audioUri by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder(context) }

    LaunchedEffect(editingItem) {
        if (editingItem != null) {
            title = editingItem.title
            imageUri = editingItem.imageUri
            audioUri = editingItem.audioUri
            selectedParentId = editingItem.parentId
        } else {
            title = ""
            imageUri = null
            audioUri = ""
            selectedParentId = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onPersistUri(it)
            imageUri = it.toString()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onPersistUri(it)
            audioUri = it.toString()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (audioRecorder.startRecording()) {
                isRecording = true
            }
        } else {
            Toast.makeText(context, "دسترسی میکروفون رد شد", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (editingItem != null) "ویرایش جایگاه" else "افزودن جایگاه جدید",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1)
            )
            Button(
                onClick = onExitEditMode,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("بازگشت به نمایش")
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("عنوان جایگاه") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Button(
            onClick = { imagePicker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (imageUri == null) Color(0xFF1976D2) else Color(0xFF388E3C))
        ) {
            Text(if (imageUri == null) "انتخاب عکس (الزامی)" else "عکس ثبت شد ✓", fontSize = 13.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (audioUri.isBlank()) Color(0xFF1976D2) else Color(0xFF388E3C))
            ) {
                Text(if (audioUri.isBlank()) "فایل صوت" else "صوت ثبت شد ✓", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (!isRecording) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            if (audioRecorder.startRecording()) isRecording = true
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        val filePath = audioRecorder.stopRecording()
                        isRecording = false
                        if (filePath != null) {
                            audioUri = filePath
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFD32F2F) else Color(0xFF0288D1))
            ) {
                Text(if (isRecording) "■ توقف ضبط" else "● ضبط با میکروفون", fontSize = 12.sp)
            }

            if (audioUri.isNotBlank()) {
                IconButton(
                    onClick = { audioUri = "" },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "حذف صوت", tint = Color(0xFFD32F2F))
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            val parentName = allItems.find { it.id == selectedParentId }?.title ?: "بدون مادر (صفحه اصلی)"
            OutlinedTextField(
                value = parentName,
                onValueChange = {},
                readOnly = true,
                label = { Text("محل قرارگیری (جایگاه مادر)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("بدون مادر (صفحه اصلی)") },
                    onClick = {
                        selectedParentId = null
                        expanded = false
                    }
                )
                allItems.filter { it.id != editingItem?.id }.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.title) },
                        onClick = {
                            selectedParentId = parent.id
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (editingItem != null) {
                OutlinedButton(
                    onClick = onCancelEditItem,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("لغو ویرایش")
                }
            }
            Button(
                onClick = {
                    if (title.isNotEmpty() && imageUri != null) {
                        onSave(editingItem?.id, title, imageUri!!, audioUri, selectedParentId)
                        title = ""
                        imageUri = null
                        audioUri = ""
                    }
                },
                enabled = title.isNotEmpty() && imageUri != null,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(if (editingItem != null) "ثبت تغییرات" else "ذخیره جایگاه")
            }
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFF90CAF9))

        Text(
            text = "تمام جایگاه‌ها (جهت ویرایش یا حذف):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0D47A1)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 135.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allItems) { item ->
                val parentName = allItems.find { it.id == item.parentId }?.title ?: "صفحه اصلی"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = item.imageUri,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مادر: $parentName" + if (item.audioUri.isNotBlank()) " | با صوت" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = { onSelectToEdit(item) }) {
                                Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = Color(0xFF1976D2))
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}
