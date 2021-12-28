package org.cmhh

import scala.util.{Try, Success, Failure}
import java.net.{URL, HttpURLConnection}
import com.typesafe.config.ConfigFactory
import upickle.default._

/**
 * Calculate trips and routes
 */
case class Router(routingService: String) {  
  /**
   * Route between two locations
   * 
   * Estimate based on straight line which can be used if OSRM is unavailable.
   * 
   * @param origin [[Coordinates]]
   * @param destination [[Coordinates]]
   * @param aveSpeed Average speed for estimating time from distance
   * @return tuple containing distance (metres) and time (seconds)
   */
  def route0(
    origin: Coordinates,
    destination: Coordinates,
    aveSpeed: Double,
    rateup: Double
  ): (Double, Double) = {
    val d = origin.distance(destination) * rateup
    (d, d / 1000 / aveSpeed * 60 * 60)
  }

  /**
   * Route between two locations
   * 
   * Call open source routing machine and extract time and distance.
   * 
   * @param origin [[Coordinates]]
   * @param destination [[Coordinates]]
   * @param connectTimeout connection timeout
   * @param readTimeout read timeout
   * @return tuple containing distance (metres) and time (seconds)
   */
  def route(
    origin: Coordinates, 
    destination: Coordinates,
    connectTimeout: Int,
    readTimeout: Int
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
   * Useful if routing service is unavailable / overwhelmed.
   * 
   * @param coordinates Set of coordinates, with origin at start
   * @return List of tuples containing each leg of overall trip--coordinate pairs, distance, and minutes.
   */
  def trip0(
    coordinates: Seq[Coordinates], aveSpeed: Double, rateup: Double
  ): List[(Coordinates, Coordinates, Double, Double)] = {
    def loop(
      coords: Seq[Coordinates], last: Coordinates, accum: List[(Coordinates, Coordinates, Double, Double)]
    ): List[(Coordinates, Coordinates, Double, Double)] = {
      if (coords.isEmpty) accum
      else {
        val d = coords.map(x => {
          val r = route0(last, x, aveSpeed, rateup)
          (last, x, r._1, r._2)
        })
        val res = d.minBy(_._3)
        loop(coords.filter(_ != res._2), res._2, accum :+ res)
      }
    }

    val res = loop(coordinates.drop(1), coordinates.head, List.empty)
    val d = route0(res.last._2, coordinates.head, aveSpeed, rateup)
    res :+ (res.last._2, coordinates.head, d._1, d._2)
  }

  /**
   * Trip solution
   * 
   * Call open source routing machine and extract times and distances of each leg.
   * 
   * @param coordinates Set of coordinates, with origin at start
   * @param connectTimeout connection timeout
   * @param readTimeout read timeout
   * @return List of tuples containing each leg of overall trip--coordinate pairs, distance, and minutes.
   */
  def trip(
    coordinates: Seq[Coordinates],
    connectTimeout: Int,
    readTimeout: Int
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

  /**
   * Trip solution
   * 
   * Call open source routing machine and extract times and distances of each leg.
   * 
   * @param origin starting (and ending) location
   * @param coordinates Set of coordinates, with origin at start
   * @param connectTimeout connection timeout
   * @param readTimeout read timeout
   * @return List of tuples containing each leg of overall trip--coordinate pairs, distance, and minutes.
   */
  def trip(
    origin: Coordinates,
    destinations: Seq[Coordinates],
    connectTimeout: Int,
    readTimeout: Int
  ): Try[List[(Coordinates, Coordinates, Double, Double)]] = 
    trip(origin +: destinations, connectTimeout, readTimeout)
}