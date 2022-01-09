library(leaflet)
library(sf)
library(config)
library(RSQLite)

collection_areas <- readRDS("../collection_areas.rds")

con <- dbConnect(RSQLite::SQLite(), config::get("db"))

dwellings <-
  DBI::dbGetQuery(con, "select * from dwellings") |>
  st_as_sf(coords = c("location_x", "location_y"), crs = 2193) |>
  st_transform(4326)

DBI::dbDisconnect(con)