package misk.grpc.protocserver

import io.grpc.stub.StreamObserver
import routeguide.RouteGuideGrpc.RouteGuideImplBase
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Singleton

@Singleton
class RouteGuideProtocService : RouteGuideImplBase() {
  var afterFeatureThrowable: Throwable? = null

  override fun getFeature(point: Point, responseObserver: StreamObserver<Feature>) {
    responseObserver.onNext(Feature.newBuilder()
        .setName("pine tree")
        .setLocation(point)
        .build())

    if (afterFeatureThrowable != null) {
      responseObserver.onError(afterFeatureThrowable)
    } else {
      responseObserver.onCompleted()
    }
  }
}