package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSpec
import jakarta.inject.Inject

/** Resolves against whichever session is active when an image loads; null when signed out. */
class ImageUrls @Inject constructor(private val sessions: SessionManager) {
    fun url(ref: ImageRef, spec: ImageSpec): String? = sessions.active.value?.connection?.images?.url(ref, spec)
}
