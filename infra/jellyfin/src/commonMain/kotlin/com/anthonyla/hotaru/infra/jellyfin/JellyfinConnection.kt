package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountGateway
import com.anthonyla.hotaru.application.ports.DeviceIdentity
import com.anthonyla.hotaru.application.ports.DiscoveredServer
import com.anthonyla.hotaru.application.ports.ImageUrlResolver
import com.anthonyla.hotaru.application.ports.LibraryGateway
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.NetworkDiscovery
import com.anthonyla.hotaru.application.ports.PlaybackGateway
import com.anthonyla.hotaru.application.ports.PublicServer
import com.anthonyla.hotaru.application.ports.RealtimeGateway
import com.anthonyla.hotaru.application.ports.ServerConnection
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.application.ports.UserDataGateway
import com.anthonyla.hotaru.domain.Session
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.Locale

/**
 * Sign-in runs on the anonymous client (base DeviceId); authenticated clients announce the per-user
 * DeviceId. This relies on the server rebinding the device record when a known token arrives with a new DeviceId.
 */
@Singleton
@ContributesBinding(AppScope::class, binding = binding<ServerConnectionFactory>())
@ContributesBinding(AppScope::class, binding = binding<NetworkDiscovery>())
class JellyfinConnectionFactory(
    identity: DeviceIdentity,
    private val logger: Logger,
    platformContext: PlatformContext,
    okHttp: OkHttpClient,
) : ServerConnectionFactory, NetworkDiscovery {

    private val baseDevice = DeviceInfo(identity.baseDeviceId.value, identity.deviceName, listOf(Locale.getDefault().toLanguageTag()))

    private val jellyfin: Jellyfin = createJellyfin {
        clientInfo = ClientInfo(identity.client.name, identity.client.version)
        deviceInfo = baseDevice
        val factory = OkHttpFactory(okHttp)
        apiClientFactory = factory
        socketConnectionFactory = factory
        applyPlatformContext(platformContext)
    }

    override fun addressCandidates(input: String): List<String> = jellyfin.discovery.getAddressCandidates(input).toList()

    override fun publicServer(baseUrl: String): PublicServer = JellyfinPublicServer(jellyfin.createApi(baseUrl = baseUrl, deviceInfo = baseDevice))

    override fun connect(baseUrl: String, session: Session): ServerConnection {
        val api = jellyfin.createApi(baseUrl = baseUrl, accessToken = session.accessToken, deviceInfo = baseDevice.copy(id = session.account.deviceId.value))
        return JellyfinServerConnection(api, session, logger)
    }

    /** UDP broadcast discovery (port 7359). Works only when headset and server share an L2 network. */
    override fun discover(): Flow<DiscoveredServer> =
        jellyfin.discovery.discoverLocalServers()
            .map { DiscoveredServer(id = it.id, name = it.name, address = it.address) }
            .catch { e -> logger.warn("Discovery", { "local discovery failed: ${e.message}" }, e) }
}

internal class JellyfinServerConnection(
    api: ApiClient,
    session: Session,
    logger: Logger,
) : ServerConnection {
    override val account: AccountGateway = JellyfinAccountGateway(api, session)
    override val library: LibraryGateway = JellyfinLibraryGateway(api)
    override val userData: UserDataGateway = JellyfinUserDataGateway(api)
    override val playback: PlaybackGateway = JellyfinPlaybackGateway(api, session)
    override val realtime: RealtimeGateway = JellyfinRealtimeGateway(api, logger)
    override val images: ImageUrlResolver = JellyfinImageUrlResolver(api)
}

/** The SDK client wraps every transport failure (IO, TLS, timeout, bad payload) in an [ApiClientException] with a readable message. */
private fun mapSdkError(e: ApiClientException): AppError = when (e) {
    is InvalidStatusException -> when (e.status) {
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        else -> AppError.Network("HTTP ${e.status}")
    }
    else -> AppError.Network(e.message)
}

/** Runs an SDK call off the caller's thread (Android forbids network on Main) and maps failures. */
internal suspend fun <T> sdk(block: suspend () -> T): Outcome<T> = withContext(Dispatchers.IO) {
    try {
        Outcome.Success(block())
    } catch (e: ApiClientException) {
        Outcome.Failure(mapSdkError(e))
    }
}
