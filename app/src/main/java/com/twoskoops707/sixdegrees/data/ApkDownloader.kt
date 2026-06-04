package com.twoskoops707.sixdegrees.data

import android.content.Context
import com.twoskoops707.sixdegrees.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File

sealed class DownloadState {
    data class Downloading(val percent: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ApkDownloader(private val context: Context) {

    fun download(url: String, fileName: String): Flow<DownloadState> = flow {
        val cacheDir = File(context.cacheDir, "apks").also { it.mkdirs() }
        val outFile = File(cacheDir, fileName)

        try {
            val request = Request.Builder().url(url).build()
            val response = RetrofitClient.fastHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response"))
                return@flow
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = outFile.outputStream()
            val buffer = ByteArray(8192)
            var bytesRead = 0L
            var read: Int

            outputStream.use { out ->
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        emit(DownloadState.Downloading(((bytesRead * 100) / contentLength).toInt()))
                    }
                }
            }

            emit(DownloadState.Done(outFile))
        } catch (e: Exception) {
            outFile.delete()
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
