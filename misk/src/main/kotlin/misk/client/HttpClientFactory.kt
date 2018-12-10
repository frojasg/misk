package misk.client

import misk.logging.getLogger
import misk.security.ssl.SslContextFactory
import misk.security.ssl.SslLoader
import okhttp3.OkHttpClient
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

@Singleton
class HttpClientFactory {
  @Inject lateinit var sslLoader: SslLoader
  @Inject lateinit var sslContextFactory: SslContextFactory

  /** Returns a client initialized based on `config`. */
  fun create(config: HttpClientEndpointConfig): OkHttpClient {
    // TODO(mmihic): Cache, proxy, etc
    val builder = unconfiguredClient.newBuilder()
    config.connectTimeout?.let { builder.connectTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }
    config.readTimeout?.let { builder.readTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }
    config.writeTimeout?.let { builder.writeTimeout(it.toMillis(),
        TimeUnit.MILLISECONDS) }

    builder.readTimeout(Duration.ofHours(1))
    builder.writeTimeout(Duration.ofHours(1))
    builder.connectTimeout(Duration.ofHours(1))

    config.ssl?.let {
      val trustStore = sslLoader.loadTrustStore(it.trust_store)!!
      val trustManagers = sslContextFactory.loadTrustManagers(trustStore.keyStore)
      val x509TrustManager = trustManagers.mapNotNull { it as? X509TrustManager }.firstOrNull()
          ?: throw IllegalStateException("no x509 trust manager in ${it.trust_store}")
      val sslContext = sslContextFactory.create(it.cert_store, it.trust_store)
      builder.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
    }
    builder.proxy(Proxy.NO_PROXY)
    builder.addInterceptor(MyHttpLoggingInterceptor(log::info).apply {
      level = MyHttpLoggingInterceptor.Level.HEADERS
    })
    return builder.build()
  }

  companion object {
    private val unconfiguredClient = OkHttpClient()
    private val log = getLogger<HttpClientFactory>()
  }
}