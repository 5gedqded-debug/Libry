package app.libry.unofficial

import android.app.Application
import app.libry.unofficial.data.Api
import app.libry.unofficial.data.Prefs
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class LibryApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var api: Api
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { api.http }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
