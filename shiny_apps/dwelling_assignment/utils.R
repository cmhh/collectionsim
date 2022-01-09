radial_lines <- function(x, y, x_id, y_id) {
  if (nrow(x) == 0 | nrow(y) == 0) return(NULL)
  X <- st_coordinates(x)
  Y <- st_coordinates(y)
  x <- st_drop_geometry(x)
  y <- st_drop_geometry(y)

  xs <- c()
  ys <- c()
  l <- list()

  for (i in 1:nrow(x)) {
    idx <- x[i, x_id]
    for (j in 1:nrow(y)) {
      idy <- y[j, y_id]
      xs <- append(xs, idx)
      ys <- append(ys, idy)
      l[[length(l) + 1]] <-
        st_multilinestring(list(st_linestring(rbind(X[i,], Y[j,]))))
    }
  }

  st_sf(setNames(
    list(xs, ys, st_sfc(l, crs = 4326)),
    c(x_id, y_id, "geom")
  ))
}
