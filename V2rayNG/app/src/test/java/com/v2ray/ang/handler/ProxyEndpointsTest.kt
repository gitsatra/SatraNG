package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyEndpointsTest {
    @Test
    fun samePortIsOneMixedEndpoint() {
        val eps = ProxyEndpoints.build("127.0.0.1", 10808, 10808)
        assertEquals(listOf(ProxyEndpoint(ProxyEndpoint.Kind.Mixed, "127.0.0.1", 10808)), eps)
        assertEquals("127.0.0.1:10808", eps.single().address)
    }

    @Test
    fun distinctPortsListSocksThenHttp() {
        val eps = ProxyEndpoints.build("127.0.0.1", 10808, 10809)
        assertEquals(listOf(ProxyEndpoint.Kind.Socks, ProxyEndpoint.Kind.Http), eps.map { it.kind })
        assertEquals(listOf("127.0.0.1:10808", "127.0.0.1:10809"), eps.map { it.address })
    }
}
