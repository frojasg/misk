package misk.grpc

import com.google.inject.util.Modules
import io.grpc.ManagedChannel
import misk.MiskServiceModule
import misk.grpc.protocclient.ProtocGrpcClientModule
import misk.grpc.protocserver.RouteGuideProtocServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE.JAVA_8
import routeguide.RouteGuideGrpc
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class ProtocClientProtocServerTest {
  @MiskTestModule
  val module = Modules.combine(
      ProtocGrpcClientModule(),
      RouteGuideProtocServiceModule(),
      MiskServiceModule())

  @Inject lateinit var channelProvider: Provider<ManagedChannel>

  @Test
  @DisabledOnJre(JAVA_8) // gRPC needs HTTP/2 which needs ALPN which needs Java 9+.
  fun requestResponse() {
    val channel = channelProvider.get()
    val stub = RouteGuideGrpc.newBlockingStub(channel)

    val feature = stub.getFeature(Point.newBuilder()
        .setLatitude(43)
        .setLongitude(-80)
        .build())
    assertThat(feature).isEqualTo(Feature.newBuilder()
        .setName("pine tree")
        .setLocation(Point.newBuilder()
            .setLatitude(43)
            .setLongitude(-80)
            .build())
        .build())
  }
}
