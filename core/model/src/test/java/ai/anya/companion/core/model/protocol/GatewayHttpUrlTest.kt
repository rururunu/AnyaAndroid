package ai.anya.companion.core.model.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GatewayHttpUrlTest {

    @Test
    fun rewrite_loopbackLan_usesConnectedLanHost() {
        val connected = cred(host = "192.168.1.5", port = 8787, scheme = "ws")
        assertThat(connected.rewriteHttpUrl("http://127.0.0.1:8787/d/token?sig=abc"))
            .isEqualTo("http://192.168.1.5:8787/d/token?sig=abc")
        assertThat(connected.rewriteHttpUrl("http://localhost:8787/d/token"))
            .isEqualTo("http://192.168.1.5:8787/d/token")
    }

    @Test
    fun rewrite_loopbackTunnel_usesHttpsOrigin() {
        val connected = cred(host = "abc.trycloudflare.com", port = 443, scheme = "wss")
        assertThat(connected.rewriteHttpUrl("http://127.0.0.1:8787/d/token"))
            .isEqualTo("https://abc.trycloudflare.com/d/token")
    }

    @Test
    fun rewrite_relativePath_resolvesAgainstOrigin() {
        val connected = cred(host = "192.168.1.5", port = 8787, scheme = "ws")
        assertThat(connected.rewriteHttpUrl("/d/token?sig=abc"))
            .isEqualTo("http://192.168.1.5:8787/d/token?sig=abc")
    }

    @Test
    fun transportEndpoint_prefersLastGood_andIgnoresLoopback() {
        val withLan = cred(host = "abc.trycloudflare.com", port = 443, scheme = "wss").copy(
            lastGoodHost = "192.168.1.5",
            lastGoodPort = 8787,
            lastGoodScheme = "ws",
        )
        assertThat(withLan.transportEndpoint().httpOrigin())
            .isEqualTo("http://192.168.1.5:8787")

        val loopback = cred(host = "abc.trycloudflare.com", port = 443, scheme = "wss").copy(
            lastGoodHost = "127.0.0.1",
            lastGoodPort = 8787,
            lastGoodScheme = "ws",
        )
        assertThat(loopback.transportEndpoint().httpOrigin())
            .isEqualTo("https://abc.trycloudflare.com")
    }

    private fun cred(host: String, port: Int, scheme: String) = DeviceCredential(
        deviceId = "dev",
        credential = "tok",
        host = host,
        port = port,
        scheme = scheme,
        pairedAtEpochMs = 1L,
    )
}
