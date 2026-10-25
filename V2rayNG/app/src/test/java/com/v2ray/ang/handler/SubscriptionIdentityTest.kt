package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionIdentityTest {
    @Test
    fun resolveNewId_emptyList_claimsDefault() {
        assertEquals(
            DEFAULT_SUBSCRIPTION_ID,
            SubscriptionIdentity.resolveNewId("", emptyList()),
        )
    }

    @Test
    fun resolveNewId_onlyDefaultPlaceholder_claimsDefault() {
        val existing = listOf(
            SubscriptionIdentity.ExistingSub(DEFAULT_SUBSCRIPTION_ID, "Default", ""),
        )
        assertEquals(
            DEFAULT_SUBSCRIPTION_ID,
            SubscriptionIdentity.resolveNewId("", existing),
        )
    }

    @Test
    fun resolveNewId_onlyNamedPlaceholder_reusesThatId() {
        val existing = listOf(
            SubscriptionIdentity.ExistingSub("legacy", "Default", ""),
        )
        assertEquals(
            "legacy",
            SubscriptionIdentity.resolveNewId("", existing),
        )
    }

    @Test
    fun resolveNewId_whenRealSubExists_returnsNullForNewUuid() {
        val existing = listOf(
            SubscriptionIdentity.ExistingSub("real", "Satra", "https://example.com/sub"),
        )
        assertNull(SubscriptionIdentity.resolveNewId("", existing))
    }

    @Test
    fun resolveNewId_editKeepsRequestedGuid() {
        assertEquals(
            "keep-me",
            SubscriptionIdentity.resolveNewId(
                "keep-me",
                listOf(SubscriptionIdentity.ExistingSub(DEFAULT_SUBSCRIPTION_ID, "Default", "")),
            ),
        )
    }
}
