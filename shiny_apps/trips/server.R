shinyServer(function(input, output, session) {
  cache <- reactiveValues()

  collector <- reactive({
    if (is.null(cache$selected)) return(NULL)
    collectors[collectors$collector == cache$selected,]
  })

  assigned <- reactive({
    if (is.null(cache$selected)) return(NULL)

    ids <- dwelling_assignment[
      dwelling_assignment$collector == cache$selected, "dwelling"
    ]

    dwellings[dwellings$dwelling %in% ids,]
  })

  paths1 <- reactive({
    if (is.null(cache$selected)) return(NULL)
    trips[trips$collector == cache$selected,]
  })

  paths2 <- reactive({
    if (is.null(paths1())) return(NULL)
    if (is.null(input$date)) return(NULL)
    paths <- paths1()[paths1()$date == input$date, ]
    if (nrow(paths) > 0) trips_to_sf(paths) else NULL
  })

  output$map <- renderLeaflet({
    pal <- colorFactor(
      "Set1",
      levels = collection_areas$area
    )

    leaflet() |>
      addTiles() |>
      addEasyButton(easyButton(
        icon=icon("resize-full", lib = "glyphicon"),
        title="reset zoom",
        onClick=JS(
          "
          function(btn, map){
            map.fitBounds(L.latLngBounds(
              [[-47.28999, 166.42613],[-34.39263, 178.57724]]
            ));
          }
          ")
      )) |>
      addPolygons(
        data = collection_areas,
        color = "#000000",
        opacity = 1,
        fillColor = ~pal(area),
        fillOpacity = 0.4,
        weight = 2,
        label = ~area,
        group = "collection areas"
      ) |>
      addCircleMarkers(
        data = collectors,
        color = "white", weight = 5, opacity = 1,
        fillColor = "black", radius = 8, fillOpacity = 1,
        label = ~collector,
        group = "collectors",
        layerId = ~collector
      ) |>
      addCircleMarkers(
        data = dwellings,
        color = "white", weight = 3, opacity = 1,
        fillColor = "red", radius = 5, fillOpacity = 1,
        label = ~dwelling,
        group = "dwellings"
      ) |>
      addLegend(
        "bottomright",
        pal = pal,
        values = collection_areas$area,
        labels = collection_area$area,
        opacity = 0.8,
        group = "collection areas"
      ) |>
      addLayersControl(
        overlayGroups = c(
          "collection areas",
          "collectors",
          "dwellings",
          "trips"
        ),
        options = layersControlOptions(collapsed = FALSE)
      )
  })

  output$collectorstab <- DT::renderDT({
    DT::datatable(
      sf::st_drop_geometry(collectors),
      rownames = FALSE,
      selection = "single",
      plugins = "scrollResize",
      options = list(
        scrollX = TRUE,
        scrollResize = TRUE,
        scrollY = "100%",
        scrollCollapse = TRUE,
        paging = FALSE
      )
    )
  })

  output$tripstab <- DT::renderDT({
    DT::datatable(
      paths1(),
      rownames = FALSE,
      selection = "single",
      plugins = "scrollResize",
      options = list(
        scrollX = TRUE,
        scrollResize = TRUE,
        scrollY = "100%",
        scrollCollapse = TRUE,
        paging = FALSE
      )
    )
  })

  observeEvent(input$map_marker_click, {
    e <- isolate(input$map_marker_click)
    if (!is.null(e$group) & e$group == "collectors") {
      id <- e$id
      cache$selected <- id[sample(length(id), 1)]
    }
  })

  observeEvent(input$collectorstab_rows_selected, {
    row <- input$collectorstab_rows_selected[1]
    cache$selected <- sf::st_drop_geometry(collectors)[row, "collector"]
  })

  observe({
    updateNavbarPage(session, "nav", selected = "map")
    if (is.null(collector())) return(NULL)
    if (is.null(paths2()) || nrow(paths2()) == 0) {
      xy <- st_coordinates(collector()) |> as.numeric()
      zoom <- input$map_zoom
      leafletProxy("map") |>
        clearGroup("trips") |>
        clearGroup("selected") |>
        addMarkers(
          data = collector(),
          group = "selected"
        ) |>
        flyTo(
          lng = xy[1], lat = xy[2], zoom = zoom
        )
    } else {
      bbox <- st_bbox(paths2()) |> as.numeric()

      pal <- colorNumeric("Spectral", domain = paths2()$start)

      leafletProxy("map") |>
        clearGroup("trips") |>
        clearGroup("selected") |>
        addMarkers(
          data = collector(),
          group = "selected"
        ) |>
        flyToBounds(bbox[1], bbox[2], bbox[3], bbox[4]) |>
        addPolylines(
          data = paths2(),
          color = ~pal(start),
          weight = 5,
          opacity = 1,
          label = ~sprintf(
            "start: %s, end: %s, duration: %s minutes",
            start, end,
            difftime(end, start, units = "mins")
          ),
          group = "trips"
        )
    }
  })

  observeEvent(paths1(), {
    if (is.null(paths1())) hide("datepanel")
    else if (nrow(paths1()) == 0) hide("datepanel")
    else show("datepanel")
  })

  observeEvent(paths1(), {
    if (is.null(paths1())) return(NULL)
    updateSelectInput(
      session, "date", choices = unique(paths1()$date)
    )
  })
})
