# Household Collection Simulation using Akka

This repository contains a rough agent-based simulation of a household survey using [Akka](https://akka.io).  Akka is an actor system implementation for the JVM.  A more detailed slideshow can be found in the `doc` subdirectory of this repository.

## Data

The project includes code to output random names, in turn dependent on bundled lists of names.  Those files, and their original source, are as follows:

file                   | source
-----------------------|--------
`dist.all.last.gz`     | [Frequently Occurring Surnames from the 2010 Census](https://www.census.gov/topics/population/genealogy/data/2010_surnames.html)
`dist.male.first.gz`   | [Social Security - Beyond the Top 1000 Names](https://www.ssa.gov/oact/babynames/names.zip)
`dist.female.first.gz` | [Social Security - Beyond the Top 1000 Names](https://www.ssa.gov/oact/babynames/names.zip)

In each case, the names have been sorted in descending order of frequency, and the frequencies themselves have been converted to overall proportions, and cumulative proportions (so names can be selected probability proportional to size).

## Installation

The project is written in Scala and requires [sbt](https://www.scala-sbt.org/) to be built.  Simply run:

```bash
sbt assembly
```

which will create a fat jar in the folder:

```plaintext
target/scala-2.13/collectionsim.jar
```

There are a number of parameters controlled by configuration, using `https://github.com/lightbend/config`, controlled by `./src/main/resources/application.conf`, which essentially contains defaults.  The relevant variables are as follows:

```hocon
router-settings {
  url = "http://localhost:5001"
  connect-timeout = 10
  read-timeout = 10
  average-speed = 37
  rateup = 2.0
}

dwelling-settings {
  prob-vacant = 0.1
}

collection-settings {
  collector {
    max-cases = 50
    max-daily-work-minutes = 500
  }
  household {
    proportion-empty = 0.1
    probs {
      refusal = 0.1
      noncontact = 0.2
      response = 0.7
    }
    duration {
      empty-mean = 3
      empty-stdev = 0.5
      refusal-mean = 3
      refusal-stdev = 0.5
      noncontact-mean = 3
      noncontact-stdev = 0.5
      response-mean = 6
      response-stdev = 1
    }
  }
  individual {
    probs {
      refusal = 0.1
      noncontact = 0.2
      response = 0.7
    }
    duration {
      refusal-mean = 3
      refusal-stdev = 0.5
      noncontact-mean = 3
      noncontact-stdev = 0.5
      response-mean = 6
      response-stdev = 1
    }
  }
}

demographic-settings {
  proportion-male = 0.5
  max-age = 120
  min-age-couple = 18
  household-type {
    one-person = 0.227441234181339
    one-family = 0.686168716018560
    two-family = 0.035188671940655
    other-mult = 0.051201377859446
  }
  family-type {
    couple-only = 0.372531912434152
    couple-only-and-others = 0.037557315777596
    couple-with-children = 0.398532495912896
    couple-with-children-and-others = 0.037309612537087
    one-parent-with-children = 0.124743351920251
    one-parent-with-children-and-others = 0.029325311418019
  }
}
```

Default parameters can be overridden on a case-by-case basis by passing `-Dparameter=value` in the usual way.

Note there's an issue with the JDBC interface using JDK 9 or above when using the REPL which I haven't resolved.  If wishing to run this interactively, start sbt with JDK 8.  On Ubuntu, this would be something like:

```bash
sbt -java-home /usr/lib/jvm/java-1.8.0-openjdk-amd64
```

The service also requires an [Open Source Routing Machine](http://project-osrm.org/) instance to find optimal paths between collectors and sample addresses, along with drivetime and distance.  A basic setup can be run locally via Docker, and an example is provided here:

[cmhh/osrm-backend-nz](https://github.com/cmhh/osrm-backend-nz)

If OSRM is not available, routes will be approximated using a straight line, with the distance scaled up by a factor (default of 2.0).

## Usage

The fat jar can be used as a library, but a simple entry-point is provided.  Command-line options can be assessed as follows:

```bash
java -cp target/scala-2.13/collectionsim.jar org.cmhh.Main --help
```
```plaintext
version 0.1.0-SNAPSHOT
  -d, --db-path  <arg>            name of output sqlite database
  -i, --input-collectors  <arg>   path to input file containing collectors
      --input-dwellings  <arg>    path to input file containing dwellings
  -n, --num-days  <arg>           number of consecutive days to simulate
  -s, --start-datetime  <arg>     datetime respresenting the start time of the
                                  simulation
  -w, --wait-interval  <arg>      time (in milliseconds) to wait between each
                                  simulated day
  -h, --help                      Show help message
  -v, --version                   Show version of this program
```

Some sample inputs are provided in the `data` folder as follows (to save space, these are all gzipped, but uncompressed csv files can be used also):

file                  | description
----------------------|------------
`interviewers.csv.gz` | A sample of 100 addresses to be used as interviewer locations.
`sample1.csv.gz`      | A sample of 20000 addresses, drawn from a random set of nearly 2000 meshblocks, to be used as a dwelling sample.
`sample2.csv.gz`      | A sample of 20000 addresses to be used as a dwelling sample.
`sample1_nn.csv.gz`   | `sample1.csv.gz` contains 13 groups of roughly equal size, so `nn` can be any of `01` through `13`.
`sample2_nn.csv.gz`   | `sample2.csv.gz` contains 13 groups of roughly equal size, so `nn` can be any of `01` through `13`.

So, for example, we could run:

```bash
java -cp target/scala-2.13/collectionsim.jar org.cmhh.Main \
  --db-path collectionsim.db \
  --input-collectors data/interviewers.csv.gz \
  --input-dwellings data/sample1_01.csv.gz \
  --start-datetime "2021-12-13 09:00:00" \
  --num-days 7
```

This will produce a SQLite database named `collectionsim.db` which can be opened in the usual way.  For example, with sqlitebrowser:

![](img/collectionsim01.png)

![](img/collectionsim02.png)

N.b. that it is easy to make things go wrong.  In particular, if a new `RunDay` message is sent before the previous day has been fully simulated, then some unusual behaviour will be observed.  Things are actually fast, but appealing to an external routing service can cause things to take longer than expected.

## Analysing Output

The repository contains two test databases, `collectionsim1.db` and `collectionsim2.db`, which are the result of running a simulation with `data/sample1_01.csv.gz` and `data/sample2_01.csv.gz` as inputs, respectively--so one clustered, and the other unclustered.  We could query these in R:

```r
library(RSQLite)

db1 <- dbConnect(RSQLite::SQLite(), "collectionsim1.db")
db2 <- dbConnect(RSQLite::SQLite(), "collectionsim2.db")

kms1 <- DBI::dbGetQuery(db1, "select sum(distance) / 1000 from trips")
kms2 <- DBI::dbGetQuery(db2, "select sum(distance) / 1000 from trips")

as.numeric(kms2 / kms1)

DBI::dbDisconnect(db1)
DBI::dbDisconnect(db2)
```
```plaintext
[1] 1.481211
```

Similarly, we could create some interactive visuals, and several R Shiny applications are included in a [`shiny_apps`](./shiny_apps) folder for illustration:

app name              | description
----------------------|-------------------------------------------------------------------
`collectors`          | present all field collectors on a leaflet map.
`dwellings`           | present all dwellings on a leaflet map.
`dwelling_assignment` | visualise assignment of dwellings to collectors on a leaflet map.
`trips`               | view trips data by day on a leaflet map

For example, screen grabs of the `trips` application (with and without a routing service used) are as follows:

![](img/trips1.webp)

![](img/trips2.webp)