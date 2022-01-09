ui <- navbarPage(id = "nav",
  theme = bslib::bs_theme(bootswatch = "darkly"),
  header = tags$head(
    tags$link(rel = "stylesheet", type = "text/css", href = "custom.css")
  ),
  "DWELLINGS",
  tabPanel("map",
    leafletOutput("map", height = "100%")
  ),
  tabPanel("table",
    div(class = "content", DT::dataTableOutput("table", height = "100%"))
  )
)
