package com.anthonyla.hotaru.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.presentation.AppShellModel
import com.anthonyla.hotaru.presentation.BackStackEntry
import com.anthonyla.hotaru.presentation.connect.ServerPickerViewModel
import com.anthonyla.hotaru.presentation.connect.SignInViewModel
import com.anthonyla.hotaru.presentation.library.DetailViewModel
import com.anthonyla.hotaru.presentation.library.HomeViewModel
import com.anthonyla.hotaru.presentation.library.LibraryViewViewModel
import com.anthonyla.hotaru.presentation.library.SearchViewModel
import com.anthonyla.hotaru.presentation.player.PlayerViewModel
import com.anthonyla.hotaru.presentation.Route
import com.anthonyla.hotaru.presentation.settings.SettingsViewModel
import com.anthonyla.hotaru.presentation.ShellNotice
import com.anthonyla.hotaru.presentation.showsTabBar
import com.anthonyla.hotaru.presentation.tabSelection
import com.anthonyla.hotaru.presentation.ViewModel
import com.anthonyla.hotaru.ui.kit.glass
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruMotion
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.libraryIcon
import com.anthonyla.hotaru.ui.kit.ProvideWindowLayout
import com.meta.spatial.uiset.theme.icons.regular.Home
import com.meta.spatial.uiset.theme.icons.regular.Search
import com.meta.spatial.uiset.theme.icons.regular.Settings
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/** The screens' view model factories, implemented by the app graph. */
interface ScreenViewModels {
    val serverPicker: ServerPickerViewModel.Factory
    val signIn: SignInViewModel.Factory
    val home: HomeViewModel.Factory
    val libraryView: LibraryViewViewModel.Factory
    val detail: DetailViewModel.Factory
    val search: SearchViewModel.Factory
    val settings: SettingsViewModel.Factory
}

/** The entry keeps its view model while it is on the back stack; the shell takes its effects while it shows. */
@Composable
private fun <VM : ViewModel<*, *>> AppShellModel.screenViewModel(entry: BackStackEntry, create: (CoroutineScope) -> VM): VM {
    val vm = entry.viewModel(create)
    LaunchedEffect(vm) { vm.effects.collect(::handle) }
    return vm
}

/**
 * The panel window: browsing, with the in-window player over it (so the screens keep their state) while playback is in the
 * window, and the server's notices over both.
 */
@Composable
fun WindowContent(shell: AppShellModel, viewModels: ScreenViewModels, player: PlayerViewModel, video: @Composable (Modifier) -> Unit) {
    val state by player.state.collectAsState()
    val shellState by shell.state.collectAsState()
    ProvideWindowLayout {
        BrowseContent(shell, shellState.views, viewModels)
        if (state.stage.mode == StageMode.Window) WindowPlayer(player, video)
        NoticeStack(shellState.notices)
    }
}

/**
 * Signed in, the tab bar floats beside a window of glass that holds every screen; the connect screens float alone as panes.
 * Each entry keeps its saved UI state, such as scroll, until it leaves the stack.
 */
@Composable
private fun BrowseContent(shell: AppShellModel, views: List<LibraryView>, viewModels: ScreenViewModels) {
    val stack by shell.backStack.stack.collectAsState()
    Row(Modifier.fillMaxSize()) {
        if (stack.showsTabBar) TabBar(views, stack.tabSelection, onOpen = shell.backStack::openTopLevel)
        AnimatedContent(
            targetState = stack.last(),
            modifier = Modifier.weight(1f).fillMaxHeight().then(if (stack.showsTabBar) Modifier.glass(HotaruShapes.window).clip(HotaruShapes.window) else Modifier),
            transitionSpec = {
                (fadeIn(tween(HotaruMotion.standardMs)) + scaleIn(initialScale = 0.98f, animationSpec = tween(HotaruMotion.standardMs)))
                    .togetherWith(fadeOut(tween(HotaruMotion.quickMs)) + scaleOut(targetScale = 1.02f, animationSpec = tween(HotaruMotion.quickMs)))
            },
            label = "route",
        ) { entry ->
            SavedUi(entry) { Screen(shell, viewModels, entry) }
        }
    }
}

/** Restores what [entry]'s screen saved of its UI, and saves it again when the screen leaves, including when the window closes. */
@Composable
private fun SavedUi(entry: BackStackEntry, content: @Composable () -> Unit) {
    // Kept in memory on the entry, never in a Bundle, so any value can be saved.
    val registry = remember(entry) { SaveableStateRegistry(entry.savedUi) { true } }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
    DisposableEffect(registry) { onDispose { entry.savedUi = registry.performSave() } }
}

@Composable
private fun Screen(shell: AppShellModel, viewModels: ScreenViewModels, entry: BackStackEntry) {
    when (val route = entry.route) {
        Route.ServerPicker -> ServerPickerScreen(shell.screenViewModel(entry) { viewModels.serverPicker.create(it) })
        is Route.SignIn -> SignInScreen(shell.screenViewModel(entry) { viewModels.signIn.create(it, route.server) }, onBack = shell::back)
        Route.Home -> HomeScreen(shell.screenViewModel(entry) { viewModels.home.create(it) })
        is Route.View -> LibraryViewScreen(shell.screenViewModel(entry) { viewModels.libraryView.create(it, route.view) })
        is Route.Item -> DetailScreen(shell.screenViewModel(entry) { viewModels.detail.create(it, route.itemId) }, onBack = shell::back)
        Route.Search -> SearchScreen(shell.screenViewModel(entry) { viewModels.search.create(it) })
        Route.Settings -> SettingsScreen(shell.screenViewModel(entry) { viewModels.settings.create(it) })
    }
}

private val TabSize = HotaruDimens.hitTarget

/**
 * visionOS's tab bar: a capsule of round tabs floating beside the window, which opens over the window to name them while
 * the pointer rests on it. Settings stays at the bottom while the libraries scroll.
 */
@Composable
private fun TabBar(views: List<LibraryView>, selected: Route?, onOpen: (Route) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        // A pointer on its way to the window crosses the bar without opening it.
        if (hovered) delay(HotaruMotion.standardMs.milliseconds)
        expanded = hovered
    }
    Box(Modifier.zIndex(1f).fillMaxHeight().padding(vertical = HotaruDimens.lg).width(TabSize + HotaruDimens.sm * 2 + HotaruDimens.lg * 2), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier
                .padding(start = HotaruDimens.lg)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .hoverable(interaction)
                .glass(RoundedCornerShape(TabSize / 2 + HotaruDimens.sm))
                .animateContentSize(tween(HotaruMotion.quickMs))
                .padding(HotaruDimens.sm)
                .width(IntrinsicSize.Max)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs),
        ) {
            @Composable
            fun Item(icon: ImageVector, label: String, route: Route) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(TabSize)
                        .clip(HotaruShapes.pill)
                        .background(if (selected == route) HotaruColors.FillSelected else Color.Transparent)
                        .selectable(selected == route, role = Role.Tab) { onOpen(route) }
                        .padding(horizontal = (TabSize - HotaruDimens.icon) / 2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md),
                ) {
                    Icon(icon, contentDescription = label.takeIf { !expanded }, modifier = Modifier.size(HotaruDimens.icon))
                    if (expanded) Text(label, style = SpatialTheme.typography.body1Strong, maxLines = 1, modifier = Modifier.padding(end = HotaruDimens.sm))
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs)) {
                Item(SpatialIcons.Regular.Home, "Home", Route.Home)
                Item(SpatialIcons.Regular.Search, "Search", Route.Search)
                views.forEach { view -> Item(libraryIcon(view.collectionType), view.name, Route.View(view)) }
            }
            Item(SpatialIcons.Regular.Settings, "Settings", Route.Settings)
        }
    }
}

@Composable
private fun NoticeStack(notices: List<ShellNotice>) {
    Column(Modifier.fillMaxWidth().padding(top = HotaruDimens.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        notices.forEach { n ->
            Column(Modifier.widthIn(max = 640.dp).glass().padding(horizontal = HotaruDimens.xl, vertical = HotaruDimens.lg)) {
                n.header?.let { Text(it, style = SpatialTheme.typography.body1Strong) }
                Text(n.text, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary)
            }
        }
    }
}
