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
}
