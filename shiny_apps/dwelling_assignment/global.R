library(leaflet)
library(sf)
library(config)
library(RSQLite)

source("utils.R")

collection_areas <- readRDS("../collection_areas.rds")

con <- dbConnect(RSQLite::SQLite(), config::get("db"))

collectors <-
  DBI::dbGetQuery(con, "select * from collectors") |>
  st_as_sf(coords = c("location_x", "location_y"), crs = 2193) |>
  st_transform(4326)

dwellings <-
  DBI::dbGetQuery(con, "select * from dwellings") |>
  st_as_sf(coords = c("location_x", "location_y"), crs = 2193) |>
  st_transform(4326)

dwelling_assignment <-
  DBI::dbGetQuery(con, "select * from dwelling_assignment")

DBI::dbDisconnect(con)