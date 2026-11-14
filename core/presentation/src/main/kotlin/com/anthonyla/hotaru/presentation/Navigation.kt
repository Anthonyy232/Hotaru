package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.Server
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

sealed interface Route {
    data object ServerPicker : Route
    data class SignIn(val server: Server) : Route
    data object Home : Route
    data class View(val view: LibraryView) : Route
    data class Item(val itemId: ItemId) : Route
    data object Search : Route
    data object Settings : Route

    val isTopLevel: Boolean get() = this == Home || this == Search || this == Settings || this is View
}

/** What a screen asks of the app shell, which outlives it. */
sealed interface ScreenEffect {
    data class OpenItem(val id: ItemId) : ScreenEffect
    /** [queue] is what autoplay follows. */
    data class Play(val item: MediaItem, val fromStart: Boolean = false, val queue: List<MediaItem> = emptyList()) : ScreenEffect
    data class SignIn(val server: Server) : ScreenEffect
}

val List<BackStackEntry>.showsTabBar: Boolean get() = first().route == Route.Home

val List<BackStackEntry>.tabSelection: Route? get() = lastOrNull { it.route.isTopLevel }?.route

/**
 * A screen on the stack. Its view model and [scope] live until the entry leaves the stack, so going back finds the screen
 * as it was.
 */
class BackStackEntry internal constructor(val route: Route, internal val scope: CoroutineScope) {
    /** What the screen saved of its UI, such as scroll, while it is not shown; the window can close and reopen meanwhile. */
    var savedUi: Map<String, List<Any?>>? = null

    private var viewModel: ViewModel<*, *>? = null

    /** Made by [create] on first use; an entry's route always asks for the same view model type. */
    @Suppress("UNCHECKED_CAST")
    fun <VM : ViewModel<*, *>> viewModel(create: (CoroutineScope) -> VM): VM = (viewModel ?: create(scope).also { viewModel = it }) as VM
}

class BackStack(private val scope: CoroutineScope, initial: Route) {
    private val _stack = MutableStateFlow(listOf(entry(initial)))
    val stack: StateFlow<List<BackStackEntry>> = _stack.asStateFlow()

    fun push(route: Route) {
        if (stack.value.last().route != route) set(stack.value + entry(route))
    }

    fun pop() {
        if (stack.value.size > 1) set(stack.value.dropLast(1))
    }

    /** Opens [route] over the root, keeping its entry when it is already there. */
    fun openTopLevel(route: Route) {
        val root = stack.value.first()
        val open = stack.value.getOrNull(1)?.takeIf { it.route == route }
        set(if (route == root.route) listOf(root) else listOf(root, open ?: entry(route)))
    }

    fun replaceAll(route: Route) = set(listOf(entry(route)))

    private fun entry(route: Route) =
        BackStackEntry(route, CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])))

    private fun set(entries: List<BackStackEntry>) {
        val left = stack.value - entries.toSet()
        _stack.value = entries
        left.forEach { it.scope.cancel() }
    }
}
