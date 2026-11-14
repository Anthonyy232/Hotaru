package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class BackStackTest {
    private val BackStack.routes: List<Route> get() = stack.value.map { it.route }

    @Test
    fun `push ignores duplicates, pop keeps a root, replaceAll resets`() = runTest {
        val stack = BackStack(backgroundScope, Route.Home)
        stack.push(Route.Search); stack.push(Route.Item(ItemId("a"))); stack.push(Route.Item(ItemId("a")))
        assertEquals(3, stack.routes.size)
        stack.pop(); stack.pop(); stack.pop()
        assertEquals(listOf<Route>(Route.Home), stack.routes)
        stack.push(Route.Settings)
        stack.replaceAll(Route.ServerPicker)
        assertEquals(listOf<Route>(Route.ServerPicker), stack.routes)
    }

    @Test
    fun `an entry keeps its view model until it leaves the stack, and a route opened twice gets its own`() = runTest {
        val stack = BackStack(backgroundScope, Route.Home)
        stack.push(Route.Item(ItemId("a"))); stack.push(Route.Item(ItemId("b"))); stack.push(Route.Item(ItemId("a")))
        val (home, first, _, second) = stack.stack.value
        var created = 0
        val create = { scope: CoroutineScope ->
            created++
            object : ViewModel<Unit, Unit>(Unit, scope) { override fun onIntent(intent: Unit) = Unit }
        }
        val vm = first.viewModel(create)
        assertNotSame(vm, second.viewModel(create))

        stack.pop(); stack.pop()
        assertSame(first, stack.stack.value.last())
        assertSame(vm, first.viewModel(create))
        assertEquals(2, created)
        assertTrue(first.scope.isActive)
        assertFalse(second.scope.isActive)

        stack.replaceAll(Route.Home)
        assertFalse(home.scope.isActive)
        assertFalse(first.scope.isActive)
    }

    @Test
    fun `tab destinations open over the root and stay selected under what they open`() = runTest {
        val stack = BackStack(backgroundScope, Route.Home)
        val movies = Route.View(Fixtures.moviesView)
        stack.openTopLevel(movies)
        val moviesEntry = stack.stack.value.last()
        stack.push(Route.Item(ItemId("a")))
        assertEquals(movies, stack.stack.value.tabSelection)
        stack.openTopLevel(movies)
        assertSame(moviesEntry, stack.stack.value.last())
        stack.openTopLevel(Route.Search)
        assertEquals(listOf(Route.Home, Route.Search), stack.routes)
        stack.openTopLevel(Route.Home)
        assertEquals(listOf<Route>(Route.Home), stack.routes)
        assertTrue(stack.stack.value.showsTabBar)
        stack.replaceAll(Route.ServerPicker)
        assertFalse(stack.stack.value.showsTabBar)
    }
}
