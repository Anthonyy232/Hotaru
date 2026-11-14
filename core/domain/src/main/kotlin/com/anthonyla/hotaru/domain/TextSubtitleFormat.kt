package com.anthonyla.hotaru.domain

enum class TextSubtitleFormat(val mimeType: String, vararg val aliases: String) {
    SubRip("application/x-subrip", "srt", "subrip"),
    Ass("text/x-ssa", "ass", "ssa"),
    WebVtt("text/vtt", "vtt", "webvtt"),
    Ttml("application/ttml+xml", "ttml");

    companion object {
        fun fromExtension(extension: String?): TextSubtitleFormat? = entries.firstOrNull { extension in it.aliases }
    }
}
