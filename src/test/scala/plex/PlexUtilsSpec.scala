package plex

import cats.effect.IO
import http.HttpClient
import io.circe.parser._
import model.{GraphQLQuery, Item}
import org.http4s.{Header, Method, Uri}
import org.scalamock.scalatest.MockFactory
import cats.effect.unsafe.implicits.global
import configuration.{Configuration, PlexConfiguration}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.generic.extras.auto._
import io.circe.syntax.EncoderOps
import org.typelevel.ci.CIString

import scala.concurrent.duration.DurationInt
import scala.io.Source

class PlexUtilsSpec extends AnyFlatSpec with Matchers with PlexUtils with MockFactory {

  "PlexUtils" should "successfully fetch a watchlist from RSS feeds" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        *,
        None,
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("watchlist.json").getLines().mkString("\n"))))
      .once()

    val result = fetchWatchlistFromRss(mockClient)(Uri.unsafeFromString("http://localhost:9090")).unsafeRunSync()

    result.size shouldBe 7
  }

  it should "not fail when the list returned is empty" in {
    val mockClient = mock[HttpClient]
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        *,
        None,
        None,
        *
      )
      .returning(IO.pure(parse("{}")))
      .once()

    val result = fetchWatchlistFromRss(mockClient)(Uri.unsafeFromString("http://localhost:9090")).unsafeRunSync()

    result.size shouldBe 0
  }

  it should "successfully ping the Plex server" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://plex.tv/api/v2/ping?X-Plex-Token=test-token&X-Plex-Client-Identifier=watchlistarr"
        ),
        None,
        None,
        *
      )
      .returning(IO.pure(parse("{}")))
      .once()

    val result: Unit = ping(mockClient)(config).unsafeRunSync()

    result shouldBe ()
  }

  it should "successfully fetch the watchlist using the plex token" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("self-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("empty-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/library/metadata/5df46a38237002001dce338d?X-Plex-Token=test-token"
        ),
        None,
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/library/metadata/617d3ab142705b2183b1b20b?X-Plex-Token=test-token"
        ),
        None,
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n"))))
      .once()

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[Item])
    result.size shouldBe 2
    result.head shouldBe Item("The Test", List("imdb://tt11347692", "tmdb://95837", "tvdb://372848"), "show")
  }

  it should "successfully fetch an empty watchlist using the plex token" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("empty-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("empty-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[Item])
    result.size shouldBe 0
  }

  it should "fetch the healthy part of a watchlist using the plex token" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("self-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"
        ),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("empty-watchlist-from-token.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/library/metadata/5df46a38237002001dce338d?X-Plex-Token=test-token"
        ),
        None,
        None,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("single-item-plex-metadata.json").getLines().mkString("\n"))))
      .once()
    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString(
          "https://discover.provider.plex.tv/library/metadata/617d3ab142705b2183b1b20b?X-Plex-Token=test-token"
        ),
        None,
        None,
        *
      )
      .returning(IO.pure(Left(new Exception("404"))))
      .once()

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[Item])
    result.size shouldBe 1
    result.head shouldBe Item("The Test", List("imdb://tt11347692", "tmdb://95837", "tvdb://372848"), "show")
  }

  it should "successfully fetch friends from Plex" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    val query = GraphQLQuery("""query GetAllFriends {
        |        allFriendsV2 {
        |          user {
        |            id
        |            username
        |          }
        |        }
        |      }""".stripMargin)
    (mockClient.httpRequest _)
      .expects(
        Method.POST,
        Uri.unsafeFromString("https://community.plex.tv/api"),
        Some("test-token"),
        Some(query.asJson),
        *
      )
      .returning(IO.pure(parse(Source.fromResource("plex-get-all-friends.json").getLines().mkString("\n"))))
      .once()

    val eitherResult = getFriends(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[User])
    result.size shouldBe 2
    result.head shouldBe (User("ecdb6as0230e2115", "friend-1"), "test-token")
    result.last shouldBe (User("a31281fd8s413643", "friend-2"), "test-token")
  }

  it should "successfully fetch a watchlist from a friend on Plex" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.POST,
        Uri.unsafeFromString("https://community.plex.tv/api"),
        Some("test-token"),
        *,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend.json").getLines().mkString("\n"))))
      .once()

    val eitherResult = getWatchlistIdsForUser(config, mockClient, "test-token")(
      User("ecdb6as0230e2115", "friend-1")
    ).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[TokenWatchlistItem])
    result.size shouldBe 2
    result.head shouldBe TokenWatchlistItem(
      Some("The Twilight Saga: Breaking Dawn - Part 2"),
      Some("5d77688b9ab54400214e789b"),
      "movie",
      "/library/metadata/5d77688b9ab54400214e789b"
    )
  }

  it should "successfully fetch multiple watchlist pages from a friend on Plex" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))
    (mockClient.httpRequest _)
      .expects(
        Method.POST,
        Uri.unsafeFromString("https://community.plex.tv/api"),
        Some("test-token"),
        *,
        *
      )
      .returning(
        IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend-page-1.json").getLines().mkString("\n")))
      )
      .repeat(13)
    (mockClient.httpRequest _)
      .expects(
        Method.POST,
        Uri.unsafeFromString("https://community.plex.tv/api"),
        Some("test-token"),
        *,
        *
      )
      .returning(IO.pure(parse(Source.fromResource("plex-get-watchlist-from-friend.json").getLines().mkString("\n"))))
      .once()

    val eitherResult = getWatchlistIdsForUser(config, mockClient, "test-token")(
      User("ecdb6as0230e2115", "friend-1")
    ).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    val result = eitherResult.getOrElse(Set.empty[TokenWatchlistItem])
    result.size shouldBe 2
    result.head shouldBe TokenWatchlistItem(
      Some("The Twilight Saga: Breaking Dawn - Part 2"),
      Some("5d77688b9ab54400214e789b"),
      "movie",
      "/library/metadata/5d77688b9ab54400214e789b"
    )
  }

  it should "paginate through self watchlist using container headers" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))

    def watchlistPage(total: Int, key: String, title: String): String =
      s"""{ "MediaContainer": { "totalSize": $total, "Metadata": [{ "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key" }] } }"""

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(watchlistPage(120, "one", "First"))))
      .once()

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(watchlistPage(120, "two", "Second"))))
      .once()

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse("{\"MediaContainer\":{\"totalSize\":0,\"Metadata\":[]}}")))
      .once()

    List("one" -> "First", "two" -> "Second").foreach { case (key, title) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString(s"https://discover.provider.plex.tv/library/metadata/$key?X-Plex-Token=test-token"),
          None,
          None,
          *
        )
        .returning(IO.pure(parse(s"""{ "MediaContainer": { "Metadata": [ { "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key", "Guid": [ { "id": "imdb://tt$key" } ] } ], "totalSize": 1 } }""")))
        .once()
    }

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    eitherResult.getOrElse(Set.empty[Item]).map(_.title) should contain allOf ("First", "Second")
  }

  it should "stop pagination when total size is an exact multiple of the page size" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))

    def watchlistPage(total: Int, key: String, title: String): String =
      s"""{ "MediaContainer": { "totalSize": $total, "Metadata": [{ "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key" }] } }"""

    List(0 -> ("one", "First"), 100 -> ("two", "Second")).foreach { case (start, (key, title)) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"),
          Some("test-token"),
          None,
          *
        )
        .returning(IO.pure(parse(watchlistPage(200, key, title))))
        .once()
    }

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse("{\"MediaContainer\":{\"totalSize\":0,\"Metadata\":[]}}")))
      .once()

    List("one" -> "First", "two" -> "Second").foreach { case (key, title) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString(s"https://discover.provider.plex.tv/library/metadata/$key?X-Plex-Token=test-token"),
          None,
          None,
          *
        )
        .returning(IO.pure(parse(s"""{ "MediaContainer": { "Metadata": [ { "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key", "Guid": [ { "id": "imdb://tt$key" } ] } ], "totalSize": 1 } }""")))
        .once()
    }

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    eitherResult.getOrElse(Set.empty[Item]).map(_.title) should contain allOf ("First", "Second")
  }

  it should "paginate through all pages for large watchlists" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))

    def watchlistPage(total: Int, key: String, title: String): String =
      s"""{ "MediaContainer": { "totalSize": $total, "Metadata": [{ "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key" }] } }"""

    List(
      0 -> ("one", "First"),
      100 -> ("two", "Second"),
      200 -> ("three", "Third")
    ).foreach { case (start, (key, title)) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"),
          Some("test-token"),
          None,
          *
        )
        .returning(IO.pure(parse(watchlistPage(250, key, title))))
        .once()
    }

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse("{\"MediaContainer\":{\"totalSize\":0,\"Metadata\":[]}}")))
      .once()

    List("one" -> "First", "two" -> "Second", "three" -> "Third").foreach { case (key, title) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString(s"https://discover.provider.plex.tv/library/metadata/$key?X-Plex-Token=test-token"),
          None,
          None,
          *
        )
        .returning(IO.pure(parse(s"""{ "MediaContainer": { "Metadata": [ { "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key", "Guid": [ { "id": "imdb://tt$key" } ] } ], "totalSize": 1 } }""")))
        .once()
    }

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    eitherResult.getOrElse(Set.empty[Item]).map(_.title) should contain allOf ("First", "Second", "Third")
  }

  it should "merge watchlist sections and keep all items" in {
    val mockClient = mock[HttpClient]
    val config     = createConfiguration(Set("test-token"))

    def watchlistSingle(key: String, title: String): String =
      s"""{ "MediaContainer": { "totalSize": 1, "Metadata": [{ "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key" }] } }"""

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/recently-added"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(watchlistSingle("alpha", "Alpha"))))
      .once()

    (mockClient.httpRequest _)
      .expects(
        Method.GET,
        Uri.unsafeFromString("https://discover.provider.plex.tv/hubs/sections/watchlist/coming-soon"),
        Some("test-token"),
        None,
        *
      )
      .returning(IO.pure(parse(watchlistSingle("beta", "Beta"))))
      .once()

    List("alpha" -> "Alpha", "beta" -> "Beta").foreach { case (key, title) =>
      (mockClient.httpRequest _)
        .expects(
          Method.GET,
          Uri.unsafeFromString(s"https://discover.provider.plex.tv/library/metadata/$key?X-Plex-Token=test-token"),
          None,
          None,
          *
        )
        .returning(IO.pure(parse(s"""{ "MediaContainer": { "Metadata": [ { "title": "$title", "guid": "plex://movie/$key", "type": "movie", "key": "/library/metadata/$key", "Guid": [ { "id": "imdb://$key" } ] } ], "totalSize": 1 } }""")))
        .once()
    }

    val eitherResult = getSelfWatchlist(config, mockClient).value.unsafeRunSync()

    eitherResult shouldBe a[Right[_, _]]
    eitherResult.getOrElse(Set.empty[Item]).map(_.title) should contain allOf ("Alpha", "Beta")
  }

  private def createConfiguration(plexTokens: Set[String]): PlexConfiguration = PlexConfiguration(
    plexWatchlistUrls = Set(Uri.unsafeFromString("https://localhost:9090")),
    plexTokens = plexTokens,
    skipFriendSync = false,
    hasPlexPass = true
  )
}
