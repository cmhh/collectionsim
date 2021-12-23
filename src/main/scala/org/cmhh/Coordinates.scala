package org.cmhh

import org.locationtech.proj4j._

/**
 * Helper class for working with lat/lng coordinates
 */
case class Coordinates(longitude: Double, latitude: Double) {
  override val toString = s"$longitude,$latitude"

  /**
   * Return X and Y coordinates, in metres, using standard NZ geodetic datum (NZTM2000 / EPSG: 2193)
   * 
   * @return X and Y coordinates in a tuple.
   */
  def xy: (Double, Double) = {
    val res: ProjCoordinate = new ProjCoordinate()
    Coordinates.wgsToNztm.transform(new ProjCoordinate(longitude, latitude), res)
    (res.x, res.y)
  }

  /**
   * Calculate distance to another coordinate pair in metres.
   * 
   * @param that [[Coordinates]]
   * @return Distance in metres
   */
  def distance(that: Coordinates): Double = {
    val (x1, y1) = this.xy
    val (x2, y2) = that.xy
    math.sqrt(math.pow(x1 - x2, 2) + math.pow(y1 - y2, 2))
  }
}

/**
 * Useful constants
 */
case object Coordinates {
  /**
   * Coordinate reference system for lat / lng coordinates
   */
  private val WGS84: CoordinateReferenceSystem = 
    (new CRSFactory()).createFromName("epsg:4326")

  /**
   * Coordinate reference system for X/Y coodinates
   */
  private val NZTM: CoordinateReferenceSystem = 
    (new CRSFactory()).createFromName("epsg:2193")

  /**
   * Transformation from lat/lng to X/Y
   */
  private val wgsToNztm: CoordinateTransform = 
    (new CoordinateTransformFactory()).createTransform(WGS84, NZTM)

  /**
   * Transformation from lat/lng to X/Y
   */
  private val nztmToWgs: CoordinateTransform = 
    (new CoordinateTransformFactory()).createTransform(NZTM, WGS84)
}