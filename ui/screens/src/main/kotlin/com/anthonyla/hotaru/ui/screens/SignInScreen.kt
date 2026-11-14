package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anthonyla.hotaru.presentation.connect.SignInIntent
import com.anthonyla.hotaru.presentation.connect.SignInViewModel
import com.anthonyla.hotaru.ui.kit.BackButton
import com.anthonyla.hotaru.ui.kit.CodeDisplay
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.PrimaryTarget
import com.anthonyla.hotaru.ui.kit.ServerImage
import com.anthonyla.hotaru.ui.kit.Spinner
import com.anthonyla.hotaru.ui.kit.TextInput
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.icons.regular.CheckAlt
import com.meta.spatial.uiset.theme.icons.regular.Profile
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun SignInScreen(vm: SignInViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    CenteredPane(maxWidth = 820) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
            BackButton(onBack)
            Column {
                Text(state.server.name, style = SpatialTheme.typography.headline1Strong)
                Text(state.server.baseUrl, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary)
            }
        }

        if (state.users.isNotEmpty()) {
            Section("Who's watching?") {
                Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                    state.users.forEach { user ->
                        val selected = state.selectedUser?.id == user.id
                        Column(
                            Modifier
                                .width(120.dp)
                                .clip(HotaruShapes.group)
                                .background(if (selected) HotaruColors.FillSelected else Color.Transparent)
                                .selectable(selected, role = Role.RadioButton) { vm.onIntent(SignInIntent.SelectUser(user)) }
                                .padding(HotaruDimens.md),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm),
                        ) {
                            Avatar(user.avatarUrl, selected)
                            Text(user.name, style = SpatialTheme.typography.body1Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        val code = state.shownQuickConnectCode
        if (code != null) {
            Section("Quick Connect") {
                Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                    Text("On your phone or computer, open Jellyfin, go to your user menu, choose Quick Connect and enter this code.", style = SpatialTheme.typography.body1, color = HotaruColors.TextSecondary)
                    CodeDisplay(code.code)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                        Spinner()
                        Text("Waiting for approval…", color = HotaruColors.TextSecondary, style = SpatialTheme.typography.body1)
                        Spacer(Modifier.weight(1f))
                        SecondaryButton("Use password instead", { vm.onIntent(SignInIntent.UsePassword) })
                    }
                }
            }
        } else {
            Section("Sign in") {
                Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                    if (state.selectedUser == null) {
                        TextInput(state.username, "Username", { vm.onIntent(SignInIntent.UsernameChanged(it)) }, { vm.onIntent(SignInIntent.Submit) }, Modifier.fillMaxWidth())
                    }
                    TextInput(state.password, "Password", { vm.onIntent(SignInIntent.PasswordChanged(it)) }, { vm.onIntent(SignInIntent.Submit) }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Password)
                    Row(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap), verticalAlignment = Alignment.CenterVertically) {
                        if (state.busy) Spinner()
                        else PrimaryTarget { PrimaryButton("Sign in", { vm.onIntent(SignInIntent.Submit) }, isEnabled = state.effectiveUsername.isNotBlank()) }
                        if (state.canUseQuickConnect) SecondaryButton("Use Quick Connect", { vm.onIntent(SignInIntent.UseQuickConnect) })
                    }
                }
            }
        }
        state.error?.let { ErrorLine(it) }
    }
}

@Composable
private fun Avatar(url: String?, selected: Boolean) {
    Box(Modifier.size(72.dp)) {
        ServerImage(
            url,
            Modifier.fillMaxSize().clip(HotaruShapes.pill).border(3.dp, if (selected) HotaruColors.Glow else Color.Transparent, HotaruShapes.pill),
            placeholderIcon = SpatialIcons.Regular.Profile,
        )
        if (selected) {
            Icon(SpatialIcons.Regular.CheckAlt, null, Modifier.align(Alignment.BottomEnd).size(HotaruDimens.icon).background(HotaruColors.Glow, HotaruShapes.pill).padding(HotaruDimens.xs), tint = Color.Black)
        }
    }
}
