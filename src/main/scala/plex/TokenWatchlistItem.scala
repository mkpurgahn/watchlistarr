package plex

private[plex] case class TokenWatchlistItem(
    title: Option[String] = None,
    guid: String,
    `type`: String,
    key: String,
    Guid: List[Guid] = List.empty
)

private[plex] case class Guid(id: String)
