package org.cmhh

import org.locationtech.proj4j._

case class Coordinates(longitude: Double, latitude: Double) {
  override val toString = s"$longitude,$latitude"

  def xy: (Double, Double) = {
    val res: ProjCoordinate = new ProjCoordinate()
    Coordinates.wgsToNztm.transform(new ProjCoordinate(longitude, latitude), res)
    (res.x, res.y)
  }

  def distance(that: Coordinates): Double = {
    val (x1, y1) = this.xy
    val (x2, y2) = that.xy
    math.sqrt(math.pow(x1 - x2, 2) + math.pow(y1 - y2, 2))
  }
}

case object Coordinates {
  private val WGS84: CoordinateReferenceSystem = (new CRSFactory()).createFromName("epsg:4326")
  private val NZTM: CoordinateReferenceSystem = (new CRSFactory()).createFromName("epsg:2193")
  private val wgsToNztm: CoordinateTransform = (new CoordinateTransformFactory()).createTransform(WGS84, NZTM)
}