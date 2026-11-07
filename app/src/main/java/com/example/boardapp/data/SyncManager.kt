package com.example.boardapp.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class SyncManager(private val context: Context, private val baseUrl: String) {
    private val client = OkHttpClient()
    private val gson = Gson()

    data class RemoteItem(
        val id: Long,
        val title: String,
        val imageFileName: String,
        val audioFileName: String,
        val parentId: Long?
    )

    data class SyncPayload(val items: List<RemoteItem>)

    // کپی امن فایل انتخاب‌شده از گالری به فضای ذخیره‌سازی دائمی اپلیکیشن
    fun copyUriToInternalStorage(uriString: String, prefix: String): String {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") return uri.path ?: uriString

            val ext = if (prefix == "img") ".jpg" else ".m4a"
            val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            uriString
        }
    }

    // دریافت داده‌ها از سرور
    suspend fun pullData(dao: ItemDao) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/sync.php?action=pull").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext

            val body = response.body?.string() ?: return@withContext
            val payload = gson.fromJson(body, SyncPayload::class.java) ?: return@withContext

            for (remote in payload.items) {
                val localImg = downloadFileIfMissing(remote.imageFileName)
                val localAud = if (remote.audioFileName.isNotBlank()) {
                    downloadFileIfMissing(remote.audioFileName).absolutePath
                } else ""

                dao.insertItem(
                    BoardItem(
                        id = remote.id,
                        title = remote.title,
                        imageUri = localImg.absolutePath,
                        audioUri = localAud,
                        parentId = remote.parentId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ارسال داده‌ها و آپلود فایل‌ها به هاست
    suspend fun pushData(items: List<BoardItem>) = withContext(Dispatchers.IO) {
        try {
            val remoteList = items.map { item ->
                val uploadedImg = uploadFile(File(item.imageUri))
                val uploadedAud = if (item.audioUri.isNotBlank()) uploadFile(File(item.audioUri)) else ""

                RemoteItem(
                    id = item.id,
                    title = item.title,
                    imageFileName = uploadedImg,
                    audioFileName = uploadedAud,
                    parentId = item.parentId
                )
            }

            val jsonBody = gson.toJson(SyncPayload(remoteList))
            val reqBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$baseUrl/sync.php?action=push")
                .post(reqBody)
                .build()

            client.newCall(request).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadFile(file: File): String {
        if (!file.exists()) return ""
        val reqFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, reqFile)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(body)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/sync.php?action=upload_file")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return file.name
        }
        return file.name
    }

    private fun downloadFileIfMissing(fileName: String): File {
        val destFile = File(context.filesDir, fileName)
        if (destFile.exists()) return destFile

        val request = Request.Builder().url("$baseUrl/uploads/$fileName").build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return destFile
    }
}
