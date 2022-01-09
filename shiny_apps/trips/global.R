library(shiny)
library(shinyjs)
library(leaflet)
library(sf)
library(config)
library(RSQLite)
library(googlePolylines)

source("utils.R")

service <- config::get("service")
route <- config::get("route")

trips_to_sf <- function(x)
  if (route) trips_to_sf1(x, service) else trips_to_sf0(x)

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

trips <-
  DBI::dbGetQuery(
    con,
    "
    select
      collector,
      substr(start, 1, 10) as date,
      start, end, distance,
      origin_x, origin_y, destination_x, destination_y
    from
      trips
    "
  )

trips$date <- as.Date(trips$date)
trips$start <- as.POSIXct(trips$start, format="%Y-%m-%d %H:%M:%S", tz="NZST")
trips$end <- as.POSIXct(trips$end, format="%Y-%m-%d %H:%M:%S", tz="NZST")

DBI::dbDisconnect(con)