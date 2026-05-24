package plex

private[plex] case class TokenWatchlistItem(
    title: Option[String] = None,
    guid: Option[String] = None,
    `type`: String,
    key: Option[String] = None,
    Guid: List[Guid] = List.empty
)

private[plex] case class Guid(id: String)
