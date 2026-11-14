package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anthonyla.hotaru.presentation.connect.ServerPickerIntent
import com.anthonyla.hotaru.presentation.connect.ServerPickerViewModel
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.PrimaryTarget
import com.anthonyla.hotaru.ui.kit.Spinner
import com.anthonyla.hotaru.ui.kit.TextInput
import com.anthonyla.hotaru.ui.kit.Wordmark
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.theme.icons.regular.Computer
import com.meta.spatial.uiset.theme.icons.regular.WifiOn
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun ServerPickerScreen(vm: ServerPickerViewModel) {
    val state by vm.state.collectAsState()
    CenteredPane(maxWidth = 760) {
        Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
            Wordmark()
            Text("Connect to your Jellyfin server", style = SpatialTheme.typography.body1, color = HotaruColors.TextSecondary)
        }

        if (state.servers.isNotEmpty()) {
            Section("Your servers") {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
                    items(state.servers, key = { it.id.value }) { server ->
                        ListTile(
                            icon = SpatialIcons.Regular.Computer, title = server.name, subtitle = server.baseUrl, trailing = state.lastAccountFor(server)?.name,
                            onClick = { vm.onIntent(ServerPickerIntent.Pick(server)) },
                        )
                    }
                }
            }
        }

        if (state.discovered.isNotEmpty()) {
            Section("Found on your network") {
                Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
                    state.discovered.forEach { d -> ListTile(icon = SpatialIcons.Regular.WifiOn, title = d.name, subtitle = d.address, onClick = { vm.onIntent(ServerPickerIntent.ConnectDiscovered(d)) }) }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
            TextInput(
                value = state.address,
                label = "Add a server",
                onValueChange = { vm.onIntent(ServerPickerIntent.AddressChanged(it)) },
                onSubmit = { vm.onIntent(ServerPickerIntent.Connect) },
                modifier = Modifier.weight(1f),
                placeholder = "192.168.1.10:8096 or https://jellyfin.example.com",
                keyboardType = KeyboardType.Uri,
            )
            if (state.busy) Spinner()
            else PrimaryTarget { PrimaryButton("Connect", { vm.onIntent(ServerPickerIntent.Connect) }, isEnabled = state.address.isNotBlank()) }
        }
        state.error?.let { ErrorLine(it) }
    }
}

@Composable
private fun ListTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HotaruDimens.hitTargetPrimary)
            .clip(HotaruShapes.group)
            .background(HotaruColors.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = HotaruDimens.lg, vertical = HotaruDimens.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg),
    ) {
        Icon(icon, null, modifier = Modifier.size(HotaruDimens.icon))
        Column(Modifier.weight(1f)) {
            Text(title, style = SpatialTheme.typography.body1Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary)
    }
}
