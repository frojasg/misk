package misk.grpc

import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.Internal
import okhttp3.internal.Util
import okhttp3.internal.duplex.HeadersListener
import okhttp3.internal.http2.Header.TARGET_AUTHORITY_UTF8
import okhttp3.internal.http2.Header.TARGET_METHOD_UTF8
import okhttp3.internal.http2.Header.TARGET_PATH_UTF8
import okhttp3.internal.http2.Header.TARGET_SCHEME_UTF8
import java.lang.reflect.Field
import java.lang.reflect.Modifier

// TODO(jwilson): Replace this awkward beast with a Retrofit2 interface.
class GrpcClient(
  val client: OkHttpClient,
  val url: HttpUrl
) {
  fun <S, R> call(method: GrpcMethod<S, R>, request: S): List<R> {
    hackOkHttp()

    val headersListener = HeadersListener { headers -> println("HELLO  headers $headers") }

    val requestBuilder = Request.Builder()
    requestBuilder
        .url(url.resolve(method.path)!!)
        .addHeader("content-type", MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.toString())
        .addHeader("te", "trailers")
        .addHeader("grpc-trace-bin", "")
        .addHeader("grpc-accept-encoding", "gzip")

    val duplexMethod = Request.Builder::class.java.getDeclaredMethod("duplex", String::class.java)
    duplexMethod.isAccessible = true
    duplexMethod.invoke(requestBuilder, "POST")

    val httpRequest = requestBuilder.build()
    val call = client.newCall(httpRequest)
    val response = call.execute()

    val headersListenerMethod =
        Response::class.java.getDeclaredMethod("headersListener", HeadersListener::class.java)
    headersListenerMethod.isAccessible = true
    headersListenerMethod.invoke(response, headersListener)

    val httpSink = Internal.instance.httpSink(response)
    val sink = httpSink.sink()
    val grpcWriter = GrpcWriter.get(sink, method.requestAdapter)
    grpcWriter.writeMessage(request)
    grpcWriter.flush()
//    httpSink.headers(Headers.of("grpc-status", "0"))
    grpcWriter.close()

    val grpcEncoding = response.header("grpc-encoding")
    val responseSource = response.body()!!.source()

    println("Some response: ${responseSource.readUtf8Line()}")

    val result = mutableListOf<R>()

    GrpcReader.get(responseSource, method.responseAdapter, grpcEncoding).use {
      while (true) {
        val message = it.readMessage() ?: break
        println("RECEIVED $message")
        result += message
      }
    }

    return result.toList()
  }

  private fun hackOkHttp() {
    val CONNECTION = "connection"
    val HOST = "host"
    val KEEP_ALIVE = "keep-alive"
    val PROXY_CONNECTION = "proxy-connection"
    val TRANSFER_ENCODING = "transfer-encoding"
    val ENCODING = "encoding"
    val UPGRADE = "upgrade"

    val declaredField = okhttp3.internal.http2.Http2Codec::class.java.getDeclaredField(
        "HTTP_2_SKIPPED_REQUEST_HEADERS")
    declaredField.isAccessible = true
    // make the field not final no more
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(declaredField, declaredField.modifiers and Modifier.FINAL.inv())

    declaredField.set(null, Util.immutableList(
        CONNECTION,
        HOST,
        KEEP_ALIVE,
        PROXY_CONNECTION,
        TRANSFER_ENCODING,
        ENCODING,
        UPGRADE,
        TARGET_METHOD_UTF8,
        TARGET_PATH_UTF8,
        TARGET_SCHEME_UTF8,
        TARGET_AUTHORITY_UTF8))

  }
}