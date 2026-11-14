package ai.dejavroom.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Downloads photo bytes for [RoadViewContent.StillImage]; the UI decodes them. No image library needed. */
fun interface ImageBytesLoader {
    suspend fun load(url: String): ByteArray?
}

object HttpImageBytesLoader : ImageBytesLoader {
    private const val MAX_BYTES = 4 * 1024 * 1024

    override suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                bytes.takeIf { it.size <= MAX_BYTES }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
