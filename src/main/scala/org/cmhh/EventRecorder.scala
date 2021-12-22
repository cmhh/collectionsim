package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import java.sql.{Connection, DatabaseMetaData, DriverManager}
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EventRecorder {
  def apply(path: String, batchSize: Int): Behavior[messages.EventRecorderCommand] = Behaviors.setup{ context => 
    new EventRecorder(path, batchSize, context).behavior(List.empty)
  }
}

private class EventRecorder (path: String, batchSize: Int, context: ActorContext[messages.EventRecorderCommand]) {
  import messages._

  if (new File(path).exists) {
    val f = new File(path)
    f.delete()
  }

  Class.forName("org.sqlite.JDBC")
  val conn = DriverManager.getConnection(s"jdbc:sqlite:$path")
  conn.setAutoCommit(false)
  val stmt = conn.createStatement()

  stmt.execute(
    """
    create table collectors (
      collector text, id int, address text, area text, 
      location_x real, location_y real
    )
    """
  )

  stmt.execute(
    """
    create table dwellings (
      dwelling text, id int, address text, area text, 
      location_x real, location_y real
    )
    """
  )

  stmt.execute(
    """
    create table dwelling_assignment (
      dwelling text, collector text
    )
    """
  )

  stmt.execute(
    """
    create table dwelling_response(
      dwelling text, response text
    )
    """
  )

  stmt.execute(
    """
    create table individual_response(
       dwelling text, respondent text, response text 
    )
    """
  )

  stmt.execute(
    """
    create table interviews (
      collector text, dwelling text, respondent text, datetime text, status text
    )"""
  )

  stmt.execute(
    """
    create table trips (
      collector text, start text, end text, distance real, 
      origin_x real, origin_y real, destination_x real, destination_y real
    )
    """
  )

  conn.commit()

  def behavior(queries: List[String]): Behavior[EventRecorderCommand] = Behaviors.receiveMessage { message => 
    message match {
      case CollectorRecord(ref, id, address, location, area) =>
        val (x, y) = location.xy
        val query = s"""insert into collectors values('${q(ref)}', $id, '${q(address)}', '$area', $x, $y)"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case DwellingRecord(ref, id, address, location, area) =>
        val (x, y) = location.xy
        val query = s"""insert into dwellings values('${q(ref)}', $id, '${q(address)}', '$area', $x, $y)"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case DwellingAssignment(dwelling, collector) =>
        val query = s"""insert into dwelling_assignment values ('${q(dwelling)}', '${q(collector)}')"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case DwellingData(ref, response) =>
        val query = s"""insert into dwelling_response values ('${q(ref)}', '$response')"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case IndividualData(ref, href, response) =>
        val query = s"""insert into individual_response values ('${q(href)}', '${q(ref)}', '${q(response)}')"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case Interview(collectorRef, dwellingRef, ref, datetime, status) =>
        val query = s"""insert into interviews values ('${q(collectorRef)}', '${q(dwellingRef)}', '${q(ref)}', '${d(datetime)}', '$status')"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case m: Trip =>
        val (origin_x, origin_y) = m.origin.xy
        val (dest_x, dest_y) = m.destination.xy
        val query = 
          "insert into trips values (" +
            s"""'${q(m.ref)}', '${d(m.startTime)}', '${d(m.endTime)}', """ +
            s"""${m.distance}, $origin_x, $origin_y, $dest_x, $dest_y)"""
        if (queries.size == batchSize - 1) context.self ! Flush
        behavior(queries :+ query)
      case Flush =>
        queries.foreach(qq => stmt.addBatch(qq))
        stmt.executeBatch()
        conn.commit()
        behavior(List.empty)
    }
  }

  private def q(str: String) = str.replaceAll("'", "''")
  private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private def d(dt: LocalDateTime): String = dt.format(fmt)
}
