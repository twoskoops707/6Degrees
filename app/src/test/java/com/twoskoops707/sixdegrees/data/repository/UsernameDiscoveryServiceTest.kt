package com.twoskoops707.sixdegrees.data.repository

import com.twoskoops707.sixdegrees.data.osint.UsernamePlatformRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class UsernameDiscoveryServiceTest {

    @Test
    fun applyToMetadata_setsCountsEvenWhenNoHitsFound() {
        val metadata = ConcurrentHashMap<String, String>()

        UsernameDiscoveryService().applyToMetadata(
            hits = emptyList(),
            metadata = metadata,
            username = "ghostrider"
        )

        assertEquals("ghostrider", metadata["username"])
        assertEquals(UsernamePlatformRegistry.PLATFORMS.size.toString(), metadata["sites_checked"])
        assertEquals("0", metadata["sites_found"])
    }
}
