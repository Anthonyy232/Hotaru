package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.domain.withUserData

internal fun MediaItem.withUserData(id: ItemId, data: UserData): MediaItem = if (this.id == id) withUserData(data) else this
