package org.cmhh

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.LoggerOps
import scala.concurrent.ExecutionContext.Implicits.global
import org.rogach.scallop._

protected class MainConf(args: Array[String]) extends ScallopConf(args) {
  version("version 0.1.0-SNAPSHOT")
  val dbpath = opt[String](
    "db-path", 
    descr = "name of output sqlite database", 
    required = true
  )
  val collectors = opt[String](
    "input-collectors", 
    descr = "path to input file containing collectors", 
    required = true,
    validate = (s: String) => (new java.io.File(s)).exists
  )
  val dwellings = opt[String](
    "input-dwellings", 
    descr = "path to input file containing dwellings", 
    required = true,
    validate = (s: String) => (new java.io.File(s)).exists
  )
  val start = opt[String](
    "start-datetime",
    descr = "datetime respresenting the start time of the simulation",
    default = Some("2021-12-13 09:00:00"),
    validate = (s: String) => s.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")
  )
  val ndays = opt[Int](
    "num-days",
    descr = "number of consecutive days to simulate",
    validate = (n: Int) => (n <= 14) & (n >= 1)
  )
  val interval = opt[Int](
    "wait-interval",
    descr = "time (in milliseconds) to wait between each simulated day",
    default = Some(3000)
  )
  verify()
}

/**
 * Basic entrpoint.
 * 
 * Beware: this is a guide only and may not work quite right for different inputs.
 * Specifically, the program waits for 3 seconds between days, but this could be
 * too short or too long depending on size of input.  
 */
object Main extends App {
  val conf = new MainConf(args)
  
  import org.cmhh._
  import akka.actor.typed.ActorSystem
  import akka.actor.typed.scaladsl.LoggerOps
  import scala.concurrent.ExecutionContext.Implicits.global
  import messages._
  import area._
  import java.time.LocalDateTime
  import java.time.format.DateTimeFormatter
  import messages._

  println()
  println("creating actor system...")
  val system = ActorSystem(Coordinator(conf.dbpath()), "collectionsim")
  
  println()
  println("loading field collectors...")
  val cit = load.FieldCollectorIterator(conf.collectors())
  while (cit.hasNext) {
    system ! cit.next()
  }

  println()
  println("loading dwellings...")
  val dit = load.DwellingIterator(conf.dwellings())
  while (dit.hasNext) {
    system ! dit.next()
  }

  val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val s = LocalDateTime.parse(conf.start(), fmt)

  (0 until conf.ndays()).foreach(i => {
    println()
    println(s"simulating day, ${s.plusDays(i).format(fmt)}...")
    Thread.sleep(conf.interval())
    system ! RunDay(s.plusDays(i))
  })

  Thread.sleep(conf.interval())
  println()
  println("flushing event recorder...")
  system ! Flush
  Thread.sleep(conf.interval())

  println()
  println("All done!")
  system.terminate()
}

/*
runMain org.cmhh.Main --db-path collectionsim_clustered.db --input-collectors data/interviewers.csv.gz --input-dwellings data/sample1_01.csv.gz --start-datetime "2021-12-13 09:00:00" --num-days 7
runMain org.cmhh.Main --db-path collectionsim_nonclustered.db --input-collectors data/interviewers.csv.gz --input-dwellings data/sample2_01.csv.gz --start-datetime "2021-12-13 09:00:00" --num-days 7
*/