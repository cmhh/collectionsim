lnglat <- function(x, y) {
  p <- st_sfc(st_point(x = c(x, y)), crs = 2193) |> st_transform(4326)
  as.numeric(st_coordinates(p))
}

get_route <- function(x1, y1, x2, y2, service) {
  o <- lnglat(x1, y1)
  d <- lnglat(x2, y2)

  url <- sprintf(
    "%s/driving/%s,%s;%s,%s?overview=full",
    service, o[1], o[2], d[1], d[2]
  )

  route <- jsonlite::fromJSON(url, simplifyVector = FALSE)
  path <- googlePolylines::decode(route$routes[[1]]$geometry)[[1]]
  geom <- st_geometry(st_linestring(as.matrix(path[, c(2,1)])))
  st_crs(geom) <- 4326
  geom
}

trips_to_sf0 <- function(x) {
  paths <- lapply(1:nrow(x), \(i) {
    ox <- x[i, "origin_x"]
    oy <- x[i, "origin_y"]
    dx <- x[i, "destination_x"]
    dy <- x[i, "destination_y"]
    op <- st_point(c(ox, oy))
    dp <- st_point(c(dx, dy))
    st_multilinestring(list(st_linestring(rbind(op, dp))))
  })
  st_sf(x, geom = st_sfc(paths, crs = 2193)) |> st_transform(crs = 4326)
}

trips_to_sf1 <- function(x, service) {
  paths <- lapply(1:nrow(x), \(i) {
    x1 <- x[i, "origin_x"]
    y1 <- x[i, "origin_y"]
    x2 <- x[i, "destination_x"]
    y2 <- x[i, "destination_y"]
    st_multilinestring(
      get_route(x1, y1, x2, y2, service)
    )
  })
  st_sf(x, geom = st_sfc(paths, crs = 4326))
}
