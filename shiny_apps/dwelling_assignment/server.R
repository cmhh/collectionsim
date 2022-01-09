library(shiny)

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

  assignment_tab_data <- reactive({
    if (is.null(cache$selected)) return(NULL)
    if (nrow(assigned()) == 0) return(NULL)
    data.frame(
      collector = cache$selected,
      st_drop_geometry(assigned())
    )
  })

  spokes <- reactive({
    if (is.null(collector())) return(NULL)
    if (is.null(assigned())) return(NULL)
    radial_lines(collector(), assigned(), "collector", "dwelling")
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
          "assignment"
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

  output$assignmenttab <- DT::renderDT({
    if (is.null(cache$selected)) return(NULL)
    DT::datatable(
      assignment_tab_data(),
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

  observeEvent(cache$selected, {
    updateNavbarPage(session, "nav", selected = "map")
    if (is.null(spokes())) {
      leafletProxy("map") |>
        clearGroup("assignment")
    } else {
      bbox <- st_bbox(spokes()) |> as.numeric()

      leafletProxy("map") |>
        clearGroup("assignment") |>
        flyToBounds(bbox[1], bbox[2], bbox[3], bbox[4]) |>
        addPolylines(
          data = spokes(),
          color = "#000000",
          weight = 1,
          opacity = 0.8,
          group = "assignment"
        )
    }
  })
})
