package misk.web.exceptions

import UnmarshallingDataException
import UnmarshallingEncodingException
import jakarta.inject.Inject
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.slf4j.event.Level
import java.net.HttpURLConnection

internal class UnmarshallingEncodingExceptionMapper @Inject internal constructor() : ExceptionMapper<UnmarshallingEncodingException> {
  override fun toResponse(th: UnmarshallingEncodingException): Response<ResponseBody> = Response(
    body = th.message?.toResponseBody() ?: "Failed to decode body".toResponseBody(),
    headers = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
    statusCode = HttpURLConnection.HTTP_BAD_REQUEST
  )

  override fun loggingLevel(th: UnmarshallingEncodingException): Level = Level.INFO
}

internal class UnmarshallingDataExceptionMapper @Inject internal constructor() : ExceptionMapper<UnmarshallingDataException> {
  override fun toResponse(th: UnmarshallingDataException): Response<ResponseBody> = Response(
    body = th.message?.toResponseBody() ?: "Failed to decode body".toResponseBody(),
    headers = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
    statusCode = HttpURLConnection.HTTP_BAD_REQUEST
  )

  override fun loggingLevel(th: UnmarshallingDataException): Level = Level.INFO
} 
