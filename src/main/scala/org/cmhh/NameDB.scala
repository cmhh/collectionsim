package org.cmhh

import java.io.{InputStream, FileInputStream}
import java.util.zip.GZIPInputStream
import scala.io.Source

class NameDB(gzinput: GZIPInputStream) {
  private val r = new scala.util.Random
  private val data: List[(String, Double)] = fromStream(gzinput)

  def this(inputstream: InputStream) = 
    this(new GZIPInputStream(inputstream))

  def this(inputstream: FileInputStream) = 
    this(new GZIPInputStream(inputstream))

  def this(dbname: String) = 
    this(new FileInputStream(dbname))

  private def fromStream(gzinput: GZIPInputStream): List[(String, Double)] = {
    val src = Source.fromInputStream(gzinput)
    
    def loop(lines: Iterator[String], accum: List[(String, Double)]): List[(String, Double)] = {
      if (!lines.hasNext) accum
      else {
        val line = lines.next().split("\\s+")
        loop(lines, (line(0).toUpperCase, line(1).toDouble) :: accum)
      }
    }

    val db = loop(src.getLines(), List.empty)
    val s = db.foldLeft(0.0)(_ + _._2)
    db.map(x => (x._1, x._2 / s))
  }

  def apply(name: String) = 
    data.filter(_._1 == name.toUpperCase)

  def length = data.size
  def size = data.size

  def bottom(n: Int) = 
    data.sortBy(_._2).take(n).map(_._1).toList

  def top(n: Int) = 
    data.sortBy(_._2).takeRight(n).reverse.map(_._1).toList

  def getRandom = 
    data(r.nextInt(data.length - 1))._1
}

class MaleNames(gzinput: GZIPInputStream) extends NameDB(gzinput) { def nameType = "male"}
class FemaleNames(gzinput: GZIPInputStream) extends NameDB(gzinput) { def nameType = "female"}
class LastNames(gzinput: GZIPInputStream) extends NameDB(gzinput) {def nameType = "surname"}

object NameDB {
  implicit val maleNames: MaleNames = new MaleNames(new GZIPInputStream(getClass.getResourceAsStream("/dist.male.first.gz")))
  implicit val femaleNames: FemaleNames = new FemaleNames(new GZIPInputStream(getClass.getResourceAsStream("/dist.female.first.gz")))
  implicit val lastNames: LastNames = new LastNames(new GZIPInputStream(getClass.getResourceAsStream("/dist.all.last.gz")))
}