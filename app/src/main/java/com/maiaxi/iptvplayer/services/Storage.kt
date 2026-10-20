package com.maiaxi.iptvplayer.services

import android.content.Context
import android.util.AtomicFile
import com.maiaxi.iptvplayer.models.LibraryData
import com.maiaxi.iptvplayer.models.Source
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/** Sources live in the app-private files dir, so other apps can't read the credentials. */
@OptIn(ExperimentalSerializationApi::class)
class Storage(context: Context) {
    private val sourcesFile = AtomicFile(File(context.filesDir, "sources.json"))
    private val cacheDir = context.cacheDir
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSources(): List<Source> = read(sourcesFile) ?: emptyList()

    fun saveSources(sources: List<Source>) = write(sourcesFile, sources)

    /** Bump the version when LibraryData gains fields that old caches can't provide. */
    private fun libraryFile(id: String) = AtomicFile(File(cacheDir, "$id-v1.json"))

    fun loadLibrary(id: String): LibraryData? = read(libraryFile(id))

    fun saveLibrary(library: LibraryData, id: String) = write(libraryFile(id), library)

    /** The raw guide as downloaded, possibly gzipped. */
    fun guideFile(id: String) = File(cacheDir, "$id-epg.xml")

    fun removeLibrary(id: String) {
        libraryFile(id).delete()
        guideFile(id).delete()
    }

    private inline fun <reified T> read(file: AtomicFile): T? = try {
        file.openRead().use { json.decodeFromStream<T>(it) }
    } catch (e: Exception) {
        null
    }

    private inline fun <reified T> write(file: AtomicFile, value: T) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            return
        }
        try {
            json.encodeToStream(value, out)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
