ui <- navbarPage(id = "nav",
  theme = bslib::bs_theme(bootswatch = "darkly"),
  header = tags$head(
    tags$link(rel = "stylesheet", type = "text/css", href = "custom.css")
  ),
  "FIELD COLLECTOR TRIPS",
  tabPanel("map",
    useShinyjs(),
    leafletOutput("map", height = "100%"),
    hidden(fixedPanel(id = "datepanel",
      draggable = TRUE, bottom = 10, left = 10, style = "z-index: 999",
      selectInput(
        selectize = FALSE,
        inputId = "date", label = NULL, choices = c()
      )
    ))
  ),
  tabPanel("collectors",
    div(class = "content", DT::dataTableOutput("collectorstab", height = "100%"))
  ),
  tabPanel("trips",
    div(class = "content", DT::dataTableOutput("tripstab", height = "100%"))
  )
)
