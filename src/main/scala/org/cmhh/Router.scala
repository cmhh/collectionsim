package org.cmhh

import scala.util.{Try, Success, Failure}
import java.net.{URL, HttpURLConnection}
import com.typesafe.config.ConfigFactory
import upickle.default._

object Router {  
  private val conf = ConfigFactory.load()
  private val routingService = conf.getString("routing.url")

  def route0(
    origin: Coordinates,
    destination: Coordinates,
    aveSpeed: Int = 60
  ): (Double, Double) = {
    val d = origin.distance(destination) * 1.35
    (d, d / 1000 / aveSpeed * 60 * 60)
  }

  def route(
    origin: Coordinates, 
    destination: Coordinates,
    connectTimeout: Int = 5000,
    readTimeout: Int = 5000
  ): Try[(Double, Double)] = Try {
    val coords = s"""$origin;$destination"""
    val service = s"${routingService}/route/v1/driving/$coords"
    val par = s"""skip_waypoints=true&overview=false&""" + 
      s"""approaches=curb;curb"""
    val url = s"$service?$par"
    val conn = (new URL(url)).openConnection
    conn.setConnectTimeout(connectTimeout)
    conn.setReadTimeout(readTimeout)
    val is = conn.getInputStream
    val content = io.Source.fromInputStream(is).mkString
    if (is != null) is.close
    val pattern = """.*"legs":\s*\[.*\].*"distance":\s*(\d+.\d+).*"duration":\s*(\d+.\d+).*""".r
    val pattern(distance, duration) = content
    (distance.toDouble, duration.toDouble)
  }

  /**
   * Naive greedy trip solution
   * 
   * Useful for smoke tests, or if routing service is unavailable / overwhelmed.
   * 
   * @param coordinates Set of coordinates, with origin at start
   * @return List of tuples containing each leg of overall trip--coordinate pairs, distance, and minutes.
   */
  def trip0(
    coordinates: Seq[Coordinates], aveSpeed: Int = 60
  ): List[(Coordinates, Coordinates, Double, Double)] = {
    def loop(
      coords: Seq[Coordinates], last: Coordinates, accum: List[(Coordinates, Coordinates, Double, Double)]
    ): List[(Coordinates, Coordinates, Double, Double)] = {
      if (coords.isEmpty) accum
      else {
        val d = coords.map(x => {
          val r = route0(last, x, aveSpeed)
          (last, x, r._1, r._2)
        })
        val res = d.minBy(_._3)
        loop(coords.filter(_ != res._2), res._2, accum :+ res)
      }
    }

    val res = loop(coordinates.drop(1), coordinates.head, List.empty)
    val d = route0(res.last._2, coordinates.head)
    res :+ (res.last._2, coordinates.head, d._1, d._2)
  }

  def trip(
    coordinates: Seq[Coordinates],
    connectTimeout: Int = 5000,
    readTimeout: Int = 5000
  ): Try[List[(Coordinates, Coordinates, Double, Double)]] = Try {
    val coords = coordinates.map(_.toString).mkString(";")
    val service = s"${routingService}/trip/v1/driving/$coords"
    val par = s"""skip_waypoints=true&overview=false&source=first&""" + 
      s"""approaches=${coordinates.map(i => "curb").mkString(";")}"""
    val url = s"$service?$par"
    val conn = (new URL(url)).openConnection
    conn.setConnectTimeout(connectTimeout)
    conn.setReadTimeout(readTimeout)
    val is = conn.getInputStream
    val content = io.Source.fromInputStream(is).mkString
    if (is != null) is.close
    val json = ujson.read(content)
    val dd = json("trips")(0)("legs").arr.map(x => (x("distance").num, x("duration").num))
    val od = coordinates.dropRight(1).zip(coordinates.drop(1)) :+ (coordinates.last, coordinates.head) take dd.size
    od.zip(dd).toList.map(x => (x._1._1, x._1._2, x._2._1, x._2._2))
  } 

  def trip(
    origin: Coordinates,
    destinations: Seq[Coordinates],
    connectTimeout: Int,
    readTimeout: Int
  ): Try[List[(Coordinates, Coordinates, Double, Double)]] = 
    trip(origin +: destinations, connectTimeout, readTimeout)
}

/*

import org.cmhh._

val origin = Coordinates(174.14842652,-35.63495896)
val p1 = Coordinates(174.2380,-35.74954)
val p2 = Coordinates(174.2331,-35.75471)
val p3 = Coordinates(174.2446,-35.75021)
val p4 = Coordinates(174.2401,-35.75146)
val p5 = Coordinates(174.2457,-35.75738)

val coordinates = origin :: List(p1, p2, p3, p4, p5)

val path = Router.trip(coordinates)
path.foreach(x => x.foreach(println))

private val conf = ConfigFactory.load()
private val routingService = conf.getString("routing.url")

val connectTimeout = 5000
val readTimeout = 5000
val coords = coordinates.map(_.toString).mkString(";")
val service = s"${routingService}/trip/v1/driving/$coords"
val par = s"""skip_waypoints=true&overview=false&source=first&""" + 
  s"""approaches=${coordinates.map(i => "curb").mkString(";")}"""
val url = s"$service?$par"
val conn = (new URL(url)).openConnection
conn.setConnectTimeout(connectTimeout)
conn.setReadTimeout(readTimeout)
val is = conn.getInputStream
val content = io.Source.fromInputStream(is).mkString


val route = Router.route(coordinates)


val url = "https://cmhh.hopto.org/osrm/trip/v1/driving/174.14842652,-35.63495896;174.2380,-35.74954;174.2331,-35.75471;174.2446,-35.75021;174.2401,-35.75146;174.2457,-35.75738?source=first&destination=any&approaches=curb;curb;curb;curb;curb;curb"
*/