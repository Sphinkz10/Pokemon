package com.rui.pvpgo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.rui.pvpgo.engine.PokemonSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Lookup loaded from the same catalog that powers Room/Collection. */
object PokemonArtworkIndex {
    @Volatile private var catalogDex: Map<String, Int> = emptyMap()
    @Volatile private var nationalDex: Map<String, Int> = emptyMap()

    fun update(catalog: List<PokemonSpecies>) {
        // Prefer Normal form when several records share the same name.
        val byName = linkedMapOf<String, Int>()
        for (species in catalog.sortedBy { !PokemonArtworkSources.supportedBaseForm(it.form) }) {
            if (PokemonArtworkSources.supportedBaseForm(species.form) && species.dex in 1..NationalDexPolicy.MAX_DEX) {
                byName.putIfAbsent(PokemonArtworkSources.normalizedName(species.name), species.dex)
                byName[PokemonArtworkSources.normalizedName(species.speciesId)] = species.dex
            }
        }
        catalogDex = byName.toMap()
    }

    fun updateNationalDex(entries: List<NationalDexEntry>) {
        nationalDex = entries.associate { it.slug to it.dex }
    }

    fun dexFor(name: String): Int? {
        val key = PokemonArtworkSources.normalizedName(name)
        return (nationalDex[key] ?: catalogDex[key] ?: PokemonArtworkSources.dexFor(name))
            ?.takeIf { it in 1..NationalDexPolicy.MAX_DEX }
    }
}

/** On-device cache, no Coil/Glide dependency; downloads only from a fixed PokéAPI sprites host. */
internal object PokemonImageCache {
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val failedAt = mutableMapOf<String, Long>()
    private val imageLocks = ConcurrentHashMap<String, Any>()
    private val diskLock = Any()
    private const val MAX_DISK_BYTES = 120L * 1024 * 1024
    private const val MAX_DISK_FILES = 750

    /** Downsample oversized future assets before they reach the 16-MB bitmap cache. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        if (probe.outWidth < 1 || probe.outHeight < 1 ||
            probe.outWidth > 20000 || probe.outHeight > 20000) return null
        var factor = 1
        while (maxOf(probe.outWidth, probe.outHeight) / factor > 512) factor *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = factor }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun trimDisk(directory: File) {
        synchronized(diskLock) {
            val images = directory.listFiles { file -> file.isFile && file.extension == "png" }
                ?.sortedBy { it.lastModified() } ?: return
            var total = images.sumOf { it.length() }
            var count = images.size
            for (file in images) {
                if (total <= MAX_DISK_BYTES && count <= MAX_DISK_FILES) break
                val bytes = file.length()
                if (file.delete()) { total -= bytes; count-- }
            }
        }
    }

    fun load(context: Context, dex: Int, shiny: Boolean, form: String?): Bitmap? {
        val urls = PokemonArtworkSources.urls(dex, shiny, form)
        if (urls.isEmpty()) return null
        val key = "v1_${dex}_${if (shiny) "shiny" else "normal"}"
        synchronized(bitmaps) { bitmaps.get(key)?.let { return it } }
        // One fetch per species+shiny key, avoiding duplicate downloads on list recompositions.
        return synchronized(imageLocks.computeIfAbsent(key) { Any() }) {
            readOrFetch(context, key, urls)
        }
    }

    private fun readOrFetch(context: Context, key: String, urls: List<String>): Bitmap? {
        synchronized(bitmaps) { bitmaps.get(key)?.let { return it } }
        val directory = File(context.cacheDir, "pokemon-artwork-v1").apply { mkdirs() }
        val destination = File(directory, "$key.png")
        if (destination.exists()) {
            runCatching { decode(destination.readBytes()) }.getOrNull()?.let { decoded ->
                destination.setLastModified(System.currentTimeMillis())
                synchronized(bitmaps) { bitmaps.put(key, decoded) }
                return decoded
            }
            destination.delete()
        }
        val now = System.currentTimeMillis()
        synchronized(failedAt) {
            if (now - (failedAt[key] ?: 0L) in 0..299_999L) return null
        }
        for (url in urls) {
            val bytes = download(url) ?: continue
            val bitmap = decode(bytes) ?: continue
            try {
                val staging = File(directory, "$key.part")
                staging.writeBytes(bytes)
                if (!staging.renameTo(destination)) {
                    destination.writeBytes(bytes)
                    staging.delete()
                }
            } catch (_: Exception) { /* artwork still works in memory */ }
            synchronized(bitmaps) { bitmaps.put(key, bitmap) }
            synchronized(failedAt) { failedAt.remove(key) }
            trimDisk(directory)
            return bitmap
        }
        synchronized(failedAt) { failedAt[key] = now }
        return null
    }

    private fun download(address: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(address)
            if (url.protocol != "https" || url.host != "raw.githubusercontent.com" ||
                !url.path.startsWith("/PokeAPI/sprites/master/sprites/pokemon/")) return null
            val active = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 6500
                instanceFollowRedirects = false
                setRequestProperty("Accept", "image/png")
                setRequestProperty("User-Agent", "PokemonPvP-Companion/1.51")
            }
            connection = active
            if (active.responseCode != 200) return null
            if (!active.contentType.orEmpty().lowercase().startsWith("image/")) return null
            if (active.contentLengthLong > 3 * 1024 * 1024) return null
            active.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 3 * 1024 * 1024) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

/** Bound simultaneous image fetches; Compose LazyColumn only composes visible rows. */
private val imageFetchSlots = Semaphore(4)

/** Shared renderer for all the screens. No automatic form guesses or misleading shiny artwork. */
@Composable
internal fun RemotePokemonArtwork(
    name: String,
    modifier: Modifier = Modifier,
    shiny: Boolean = false,
    form: String? = null,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val dex = PokemonArtworkIndex.dexFor(name)
    var bitmap by remember(dex, shiny, form) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(dex, shiny, form) {
        bitmap = if (dex != null) imageFetchSlots.withPermit {
            withContext(Dispatchers.IO) {
                PokemonImageCache.load(context, dex, shiny, form)
            }
        } else null
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = "$name${if (shiny) " Shiny" else ""}",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else fallback()
}
