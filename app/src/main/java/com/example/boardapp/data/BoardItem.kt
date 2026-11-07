package com.example.boardapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class BoardItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleFa: String = "",
    val titleEn: String = "",
    val imageUri: String = "",
    val audioUriFa: String = "",
    val audioUriEn: String = "",
    val parentId: Long? = null
) {
    // تابع کمکی برای دریافت عنوان متناسب با زبان فعال
    fun getTitle(isFa: Boolean): String {
        return if (isFa) {
            titleFa.ifBlank { titleEn }
        } else {
            titleEn.ifBlank { titleFa }
        }
    }

    // تابع کمکی برای دریافت مسیر صوت متناسب با زبان فعال
    fun getAudio(isFa: Boolean): String {
        return if (isFa) {
            audioUriFa.ifBlank { audioUriEn }
        } else {
            audioUriEn.ifBlank { audioUriFa }
        }
    }
}
