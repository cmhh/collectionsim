ui <- navbarPage(id = "nav",
  theme = bslib::bs_theme(bootswatch = "darkly"),
  header = tags$head(
    tags$link(rel = "stylesheet", type = "text/css", href = "custom.css")
  ),
  "DWELLING ASSIGNMENT",
  tabPanel("map",
    leafletOutput("map", height = "100%")
  ),
  tabPanel("collectors",
    div(class = "content", DT::dataTableOutput("collectorstab", height = "100%"))
  ),
  tabPanel("assignment",
    div(class = "content", DT::dataTableOutput("assignmenttab", height = "100%"))
  )
)
