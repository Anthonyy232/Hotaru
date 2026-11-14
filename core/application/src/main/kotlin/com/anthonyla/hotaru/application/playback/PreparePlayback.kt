package com.anthonyla.hotaru.application.playback

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.flatMap
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AudioMode
import com.anthonyla.hotaru.application.ports.DeviceCapabilitiesProvider
import com.anthonyla.hotaru.application.ports.PlaybackInfoRequest
import com.anthonyla.hotaru.application.ports.PlaybackInfoResult
import com.anthonyla.hotaru.application.ports.PlaybackRefusal
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.ExternalSubtitle
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.PlayMethod
import com.anthonyla.hotaru.domain.ResumePolicy
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.SubtitleDelivery
import com.anthonyla.hotaru.domain.Ticks
import jakarta.inject.Inject

data class PlaybackRequest(
    val item: MediaItem,
    /** Null starts at the resume point, else the beginning. */
    val startAt: Ticks? = null,
    val audioStreamIndex: Int? = null,
    val subtitles: SubtitleChoice = SubtitleChoice.Default,
)

/** Prefers direct play, then remux, then transcode, like the official clients. TranscodingUrl is opaque. */
class PreparePlayback @Inject constructor(
    private val capabilitiesProvider: DeviceCapabilitiesProvider,
    private val settings: SettingsStore,
) {
    suspend operator fun invoke(session: ActiveSession, request: PlaybackRequest): Outcome<PlaybackPlan> {
        val item = request.item
        if (!item.isPlayable) return AppError.InvalidInput("${item.name} cannot be played").failure()

        val prefs = settings.settings.value
        val caps = capabilitiesProvider.capabilities()
            .let { if (prefs.forceSdr) it.withoutHdr() else it }
            // The server transcodes multichannel audio to stereo instead of the device downmixing it.
            .let { if (prefs.audioMode == AudioMode.ServerStereoAac) it.copy(maxAudioChannels = 2) else it }
        val startAt = request.startAt ?: ResumePolicy.resumePoint(item) ?: Ticks.ZERO
        val playbackRequest = PlaybackInfoRequest(
            itemId = item.id,
            startAt = startAt,
            audioStreamIndex = request.audioStreamIndex,
            subtitles = request.subtitles,
            maxStreamingBitrate = prefs.maxStreamingBitrate,
            capabilities = caps,
        )
        val gateway = session.connection.playback
        return gateway.playbackInfo(playbackRequest).flatMap { result ->
            val info = when (result) {
                is PlaybackInfoResult.Refused -> return@flatMap AppError.PlaybackUnsupported(
                    when (result.reason) {
                        PlaybackRefusal.NotAllowed -> "This account isn't allowed to play this"
                        PlaybackRefusal.NoCompatibleStream -> "The server has no stream this headset can play"
                        PlaybackRefusal.RateLimitExceeded -> "The server is at its stream limit"
                    },
                ).failure()
                is PlaybackInfoResult.Playable -> result
            }
            val source = info.sources.filter { it.isLocalFile }.ifEmpty { info.sources }.firstOrNull()
                ?: return@flatMap AppError.PlaybackUnsupported("No playable media source").failure()

            val transcodingUrl = source.transcodingUrl
            val method: PlayMethod
            val url = when {
                source.supportsDirectPlay -> { method = PlayMethod.DirectPlay; gateway.directPlayUrl(item.id, source) }
                source.supportsDirectStream && transcodingUrl != null -> { method = PlayMethod.DirectStream; gateway.resolveServerUrl(transcodingUrl) }
                source.supportsTranscoding && transcodingUrl != null -> { method = PlayMethod.Transcode; gateway.resolveServerUrl(transcodingUrl) }
                else -> return@flatMap AppError.PlaybackUnsupported("Server offered no way to play this").failure()
            }
            val subtitleIndex = when (val subtitles = request.subtitles) {
                SubtitleChoice.Default -> source.defaultSubtitleStreamIndex
                SubtitleChoice.Off -> null
                is SubtitleChoice.Stream -> subtitles.index
            }
            val external = source.subtitleStreams.filter { it.delivery == SubtitleDelivery.External }.mapNotNull { stream ->
                ExternalSubtitle(stream, gateway.resolveServerUrl(stream.deliveryUrl ?: return@mapNotNull null), stream.deliveryFormat ?: return@mapNotNull null)
            }
            PlaybackPlan(
                item = item,
                source = source,
                method = method,
                streamUrl = url,
                audioStreamIndex = request.audioStreamIndex ?: source.defaultAudioStreamIndex,
                subtitleStreamIndex = subtitleIndex,
                startAt = startAt,
                playSessionId = info.playSessionId,
                externalSubtitles = external,
            ).success()
        }
    }
}
