package com.example.boardapp.ui

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
import coil.compose.AsyncImage
import com.example.boardapp.audio.AudioPlayer
import com.example.boardapp.data.BoardItem

@Composable
fun DisplayScreen(
    items: List<BoardItem>,
    allItems: List<BoardItem>,
    isRootScreen: Boolean,
    isPersian: Boolean,
    scaleFactor: Float,
    audioPlayer: AudioPlayer,
    onNavigateToChild: (Long) -> Unit,
    onBack: () -> Unit,
    onUnlockEditMode: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    // فیلتر کردن آیتم‌ها: فقط گزینه‌هایی که نمایش آن‌ها فعال است
    val visibleItems = remember(items) { items.filter { it.isVisible } }

    // محاسبه سایز کارت بر اساس ضریب مقیاس (پایه 140dp)
    val itemMinSize = (140 * scaleFactor).dp
    val fontSize = (16 * (1f + (scaleFactor - 1f) * 0.35f)).sp

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
                    Text(
                        if (isPersian) "بازگشت" else "Back",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = itemMinSize),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy((16 * scaleFactor).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * scaleFactor).dp),
                contentPadding = PaddingValues(bottom = if (isRootScreen) 80.dp else 16.dp)
            ) {
                items(visibleItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val audio = item.getAudio(isPersian)
                                if (audio.isNotBlank()) {
                                    audioPlayer.playAudio(audio)
                                }
                                // فقط اگر فرزند قابل نمایش داشته باشد وارد صفحه بعدی شود
                                if (allItems.any { it.parentId == item.id && it.isVisible }) {
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
                                contentDescription = item.getTitle(isPersian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.getTitle(isPersian),
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = fontSize,
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

        if (isRootScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(54.dp)
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
                    contentDescription = if (isPersian) "تنظیمات" else "Settings",
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF0D47A1)
                )
            }
        }
    }
}
