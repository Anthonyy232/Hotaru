package com.anthonyla.hotaru.quest

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.anthonyla.hotaru.ui.kit.HotaruMotion
import com.anthonyla.hotaru.ui.kit.ServerImageMapper
import dev.zacsweers.metro.createGraphFactory

class HotaruApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
        graph.shell.start()
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ServerImageMapper(graph.imageUrls))
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.okHttp }))
            }
            .crossfade(HotaruMotion.standardMs)
            .build()
}
