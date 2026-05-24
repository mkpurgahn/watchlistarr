package http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.circe._
import org.http4s.client.middleware.FollowRedirect
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, Method, Request, Uri}
import org.typelevel.ci.CIString
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class HttpClient {

  private val logger = LoggerFactory.getLogger(getClass)

  private val client = EmberClientBuilder
    .default[IO]
    .build
    .map(FollowRedirect(5))

  private val cacheTtl = 5.seconds

    private val cache
      : AsyncLoadingCache[(Method, Uri, Option[String], Option[Json], List[Header.ToRaw]), Either[Throwable, Json]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheTtl)
      .maximumSize(1000)
      .buildAsyncFuture { case (method, url, apiKey, payload, extraHeaders) =>
        makeHttpRequest(method, url, apiKey, payload, extraHeaders).unsafeToFuture()
      }

    def httpRequest(
      method: Method,
      url: Uri,
      apiKey: Option[String] = None,
      payload: Option[Json] = None,
      extraHeaders: List[Header.ToRaw] = Nil
    ): IO[Either[Throwable, Json]] = IO.fromFuture(IO(cache.get(method, url, apiKey, payload, extraHeaders)))

    private def makeHttpRequest(
      method: Method,
      url: Uri,
      apiKey: Option[String] = None,
      payload: Option[Json] = None,
      extraHeaders: List[Header.ToRaw] = Nil
    ): IO[Either[Throwable, Json]] = {
    // Note: do NOT set Host explicitly. Plex's rss.plex.tv now 302-redirects to S3,
    // and a manual Host header survives FollowRedirect and triggers 403 from S3.
    // Let http4s/ember derive Host from the (possibly redirected) target URI.
    val baseRequest = Request[IO](method = method, uri = url)
      .withHeaders(
        Header.Raw(CIString("Accept"), "application/json"),
        Header.Raw(CIString("Content-Type"), "application/json"),
        Header.Raw(CIString("User-Agent"), "watchlistarr/1.0")
      )
    val requestWithApiKey = apiKey.fold(baseRequest)(key =>
      baseRequest.withHeaders(
        Header.Raw(CIString("X-Api-Key"), key),
        Header.Raw(CIString("X-Plex-Token"), key),
        baseRequest.headers
      )
    )
    val requestWithPayload = payload.fold(requestWithApiKey)(p => requestWithApiKey.withEntity(p))
    val requestWithHeaders = requestWithPayload.putHeaders(extraHeaders: _*)

    logger.debug(s"HTTP Request: ${requestWithHeaders.toString()}")

    val responseIO = client.use(_.expect[Json](requestWithHeaders).attempt)

    responseIO.map { response =>
      logger.debug(s"HTTP Response: $response")
      response
    }
  }
}
