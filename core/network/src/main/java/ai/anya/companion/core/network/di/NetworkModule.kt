package ai.anya.companion.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object NetworkModule {

    @Provides
    @Singleton
    public fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        explicitNulls = false
    }

    @Provides
    @Singleton
    public fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            // Fail fast so the reconnect loop can rotate attempts quickly; a
            // handshake that needs >15s is unusable for interactive chat anyway.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived WS
            .writeTimeout(30, TimeUnit.SECONDS)
            // Cloudflare / NAT often drop WebSocket protocol pings. Keep-alive is
            // app-level ping/pong from the desktop; protocol pings would kill a
            // healthy tunnel when the pong never comes back.
            .pingInterval(0, TimeUnit.SECONDS)
            // WebSocket upgrade is HTTP/1.1. HTTP/2 coalescing to trycloudflare.com
            // is a common source of mid-handshake RST / "connection closed".
            .protocols(listOf(Protocol.HTTP_1_1))
            // TLS 1.3 ClientHello is often RST in China; Compatible adds 1.2 fallback.
            // CLEARTEXT is required for LAN `ws://` — without it
            // OkHttp throws "CLEARTEXT communication not enabled for client" even when
            // network_security_config permits cleartext.
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT,
                ),
            )
            // Never reuse a pooled TCP/TLS session — a dead Cloudflare socket reused
            // on the next connect shows up as SSLHandshakeException: connection closed
            // in ~200ms.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            // Companion owns reconnect. OkHttp retries of a canceled upgrade
            // show up as overlapping GETs and `IOException: Canceled`.
            .retryOnConnectionFailure(false)
            .addInterceptor(logging)
            .build()
    }
}
