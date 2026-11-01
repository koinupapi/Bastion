package com.koinupapi.bastion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BastionScreenDestinationTest {
    @Test fun browsePaginationAndFiltersKeepTheSameIdentity() {
        val first = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:123", 1)
        val next = BastionScreenDestination("seerr", 0, "browse:genre:28:4:unavailable:456", 1)
        assertEquals(first.identity, next.identity)
    }

    @Test fun distinctBrowseDestinationsHaveDistinctIdentities() {
        val action = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val comedy = BastionScreenDestination("seerr", 0, "browse:genre:35:1:all:0", 1)
        assertNotEquals(action.identity, comedy.identity)
    }

    @Test fun nestedDetailsAreSeparateEntries() {
        val first = BastionScreenDestination("seerr", 0, "media:1:123", 1)
        val second = BastionScreenDestination("seerr", 0, "media:2:123", 2)
        assertNotEquals(first.identity, second.identity)
    }

    @Test fun tabAndServiceArePartOfIdentity() {
        val one = BastionScreenDestination("one", 0, "root", 0)
        val another = BastionScreenDestination("two", 0, "root", 0)
        val otherTab = BastionScreenDestination("one", 1, "root", 0)
        assertNotEquals(one.identity, another.identity)
        assertNotEquals(one.identity, otherTab.identity)
    }
    @Test fun backRevealsOriginalBrowseEntry() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val browse = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val details = BastionScreenDestination("seerr", 0, "media:1:42", 2)
        val history = reconcileBastionNavigation(
            reconcileBastionNavigation(
                reconcileBastionNavigation(emptyList(), discover), browse
            ), details
        )
        assertEquals(listOf(discover, browse, details), history)
        assertEquals(listOf(discover, browse), reconcileBastionNavigation(history, browse))
    }

    @Test fun filterChangeReplacesBrowseArgumentsWithoutPushing() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val browse = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val filtered = BastionScreenDestination("seerr", 0, "browse:genre:28:3:available:55", 1)
        assertEquals(listOf(discover, filtered),
            reconcileBastionNavigation(listOf(discover, browse), filtered))
    }

    @Test fun siblingNavigationReplacesTopWithoutGrowingStack() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val action = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val comedy = BastionScreenDestination("seerr", 0, "browse:genre:35:1:all:0", 1)
        assertEquals(listOf(discover, comedy),
            reconcileBastionNavigation(listOf(discover, action), comedy))
    }

    @Test fun poppingMultipleLevelsReturnsToOriginalRoot() {
        val root = BastionScreenDestination("seerr", 0, "root", 0)
        val browse = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val movie = BastionScreenDestination("seerr", 0, "media:1:123", 2)
        val related = BastionScreenDestination("seerr", 0, "media:2:456", 3)
        assertEquals(listOf(root), reconcileBastionNavigation(listOf(root, browse, movie, related), root))
    }

    @Test fun repeatedDestinationDoesNotDuplicateHistory() {
        val root = BastionScreenDestination("seerr", 0, "root", 0)
        val details = BastionScreenDestination("seerr", 0, "media:1:123", 1)
        val history = listOf(root, details)
        assertEquals(history, reconcileBastionNavigation(history, details))
    }

    @Test fun rootOfAnotherTabHasDifferentIdentity() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val requests = BastionScreenDestination("seerr", 1, "root", 0)
        assertNotEquals(discover.identity, requests.identity)
    }

    @Test fun searchIsSeparateFromDiscoverAndPopsBackToDiscover() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val search = BastionScreenDestination("seerr", 0, "search", 1)
        assertNotEquals(discover.identity, search.identity)
        val history = reconcileBastionNavigation(listOf(discover), search)
        assertEquals(listOf(discover, search), history)
        assertEquals(listOf(discover), reconcileBastionNavigation(history, discover))
    }

    @Test fun detailsReturnToSearchBeforeDiscover() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val search = BastionScreenDestination("seerr", 0, "search", 1)
        val details = BastionScreenDestination("seerr", 0, "media:1:123", 2)
        val history = reconcileBastionNavigation(listOf(discover, search), details)
        assertEquals(listOf(discover, search, details), history)
        assertEquals(listOf(discover, search), reconcileBastionNavigation(history, search))
    }

    @Test fun searchFromBrowseReturnsToBrowseBeforeDiscover() {
        val discover = BastionScreenDestination("seerr", 0, "root", 0)
        val browse = BastionScreenDestination("seerr", 0, "browse:genre:28:1:all:0", 1)
        val search = BastionScreenDestination("seerr", 0, "search", 2)
        val details = BastionScreenDestination("seerr", 0, "media:1:123", 3)
        val history = listOf(discover, browse, search, details)
        assertEquals(history.dropLast(1), reconcileBastionNavigation(history, search))
        assertEquals(listOf(discover, browse), reconcileBastionNavigation(history.dropLast(1), browse))
    }

    @Test fun newSearchSessionDoesNotReusePreviousDestinationState() {
        val first = BastionScreenDestination("seerr", 0, "search:1", 1)
        val second = BastionScreenDestination("seerr", 0, "search:2", 1)
        assertNotEquals(first.identity, second.identity)
    }

    @Test fun returningFromDetailsKeepsTheSameSearchSession() {
        val root = BastionScreenDestination("seerr", 0, "root", 0)
        val search = BastionScreenDestination("seerr", 0, "search:4", 1)
        val detail = BastionScreenDestination("seerr", 0, "media:1:42", 2)
        assertEquals(listOf(root, search),
            reconcileBastionNavigation(listOf(root, search, detail), search))
    }

}
