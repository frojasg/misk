package misk.web.marshal

import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import com.squareup.protos.test.parsing.Robot
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class ProtoBufRequestTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun passAsObject() {
    val request = HelloRequest.Builder().name("foo").build()
    val response = post("/as-object", request)
    assertThat(response.message).isEqualTo("foo as-object")
  }

  @Test
  fun passAsString() {
    val request = HelloRequest.Builder().name("foo").build()
    val response = post("/as-string", request)
    assertThat(response.message).isEqualTo("foo as-string")
  }

  @Test
  fun passAsByteString() {
    val request = HelloRequest.Builder().name("foo").build()
    val response = post("/as-byte-string", request)
    assertThat(response.message).isEqualTo("foo as-byte-string")
  }

  @Test
  fun returnsBadRequestForRequestWithWrongSchema() {
    val request = Robot.Builder()
      .robot_id(123)
      .build()
    val response = webTestClient.call("/as-object") {
      post(request.encode().toRequestBody(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE))
    }.response
    assertThat(response.code).isEqualTo(400)

  }

  class PassAsObject @Inject constructor() : WebAction {
    @Post("/as-object")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun call(@RequestBody request: HelloRequest): HelloReply {
      return HelloReply.Builder()
        .message("${request.name} as-object")
        .build()
    }
  }

  class PassAsString @Inject constructor() : WebAction {
    @Post("/as-string")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun call(@RequestBody encodedRequest: String): HelloReply {
      val request = HelloRequest.ADAPTER.decode(encodedRequest.toByteArray())
      return HelloReply.Builder()
        .message("${request.name} as-string")
        .build()
    }
  }

  class PassAsByteString @Inject constructor() : WebAction {
    @Post("/as-byte-string")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun call(@RequestBody encodedRequest: ByteString): HelloReply {
      val request = HelloRequest.ADAPTER.decode(encodedRequest.toByteArray())
      return HelloReply.Builder()
        .message("${request.name} as-byte-string")
        .build()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<PassAsObject>())
      install(WebActionModule.create<PassAsString>())
      install(WebActionModule.create<PassAsByteString>())
    }
  }

  private fun post(path: String, request: HelloRequest): HelloReply = webTestClient.call(path) {
    post(request.encode().toRequestBody(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE))
  }
    .apply {
      assertThat(response.code).isEqualTo(200)
      assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_PROTOBUF)

    }.response.let { HelloReply.ADAPTER.decode(it.body.byteStream()) }!!
}
