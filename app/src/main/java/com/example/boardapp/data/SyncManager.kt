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
        val titleFa: String,
        val titleEn: String,
        val imageFileName: String,
        val audioFileNameFa: String,
        val audioFileNameEn: String,
        val parentId: Long?
    )

    data class SyncPayload(val items: List<RemoteItem>)

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

    suspend fun pullData(dao: ItemDao): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/sync.php?action=pull").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body?.string() ?: return@withContext false
            val payload = gson.fromJson(body, SyncPayload::class.java) ?: return@withContext false

            for (remote in payload.items) {
                val localImg = downloadFileIfMissing(remote.imageFileName)
                val localAudFa = if (remote.audioFileNameFa.isNotBlank()) downloadFileIfMissing(remote.audioFileNameFa).absolutePath else ""
                val localAudEn = if (remote.audioFileNameEn.isNotBlank()) downloadFileIfMissing(remote.audioFileNameEn).absolutePath else ""

                dao.insertItem(
                    BoardItem(
                        id = remote.id,
                        titleFa = remote.titleFa,
                        titleEn = remote.titleEn,
                        imageUri = localImg.absolutePath,
                        audioUriFa = localAudFa,
                        audioUriEn = localAudEn,
                        parentId = remote.parentId
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun pushData(items: List<BoardItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteList = items.map { item ->
                val uploadedImg = uploadFile(File(item.imageUri))
                val uploadedAudFa = if (item.audioUriFa.isNotBlank()) uploadFile(File(item.audioUriFa)) else ""
                val uploadedAudEn = if (item.audioUriEn.isNotBlank()) uploadFile(File(item.audioUriEn)) else ""

                RemoteItem(
                    id = item.id,
                    titleFa = item.titleFa,
                    titleEn = item.titleEn,
                    imageFileName = uploadedImg,
                    audioFileNameFa = uploadedAudFa,
                    audioFileNameEn = uploadedAudEn,
                    parentId = item.parentId
                )
            }

            val jsonBody = gson.toJson(SyncPayload(remoteList))
            val reqBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$baseUrl/sync.php?action=push")
                .post(reqBody)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
