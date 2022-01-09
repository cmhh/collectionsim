library(shiny)

shinyServer(function(input, output, session) {
  cache <- reactiveValues()

  pal <- colorFactor(
    "Set1",
    levels = collection_areas$area
  )

  output$map <- renderLeaflet({
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
        color = "white",
        opacity = 1,
        radius = 6,
        group = "collectors"
      ) |>
      addCircleMarkers(
        data = collectors,
        color = "black",
        opacity = 1,
        radius = 3,
        label = ~collector,
        group = "collectors"
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
          "selected"
        ),
        options = layersControlOptions(collapsed = FALSE)
      )
  })

  output$table <- DT::renderDT({
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

  observeEvent(input$table_rows_selected, {
    row <- input$table_rows_selected[1]
    selected <- collectors[row, ]
    xy <- st_coordinates(selected)
    updateNavbarPage(session, "nav", selected = "map")

    leafletProxy("map") |>
      clearGroup(selected) |>
      leaflet::flyTo(xy[1], xy[2], zoom = 11) |>
      addCircleMarkers(
        data = selected,
        color = "yellow",
        opacity = 1,
        radius = 6,
        group = "selected"
      ) |>
      addCircleMarkers(
        data = selected,
        color = "red",
        opacity = 1,
        radius = 3,
        label = ~collector,
        group = "selected"
      )
  })
})
