package org.cmhh

import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream
import java.nio.charset.StandardCharsets
import scala.io.Source

/**
 * utitilies for importing external data.
 */
object load {
  import messages._

  /**
   * CSV configuration.
   * 
   * Specify which columns contain required fields.  CSV files must have a header row.
   * 
   * @param idCol column holding unique identifier
   * @param addressCol column holding address string
   * @param longitudeCol column holding longitude, i.e. EPSG: 4326
   * @param latitudeCol column holding latitude, i.e. EPSG: 4326
   * @param regionCOl column holding 'region'--used to assign potential actors to the correct AreaCoordinator
   */
  case class CsvConfig(idCol: String, addressCol: String, longitudeCol: String, latitudeCol: String, regionCol: String)

  /**
   * Default [[CsvConfig]].
   */
  implicit val conf = CsvConfig("address_id", "full_address", "gd2000_xcoord", "gd2000_ycoord", "region")

  /**
   * Produce [[FieldCollector]] iterator from CSV file.
   * 
   * @param file file name
   * @param conf [[CsvConfig]]
   */
  case class FieldCollectorIterator(file: String)(implicit val conf: CsvConfig) extends Iterator[FieldCollector] {
    private val it = 
      if (file.matches("^.+.csv.gz$")) {
        val fs = new FileInputStream(file)
        val gz = new GZIPInputStream(fs)
        Source.fromInputStream(gz).getLines()
      } else {
        Source.fromFile(new File(file)).getLines()
      }

    private val hdr = it.next().split(",").toList.map(x => x.replaceAll("\"", ""))

    /**
     * Return `true` if more records exists, `false` otherwise.
     */
    def hasNext: Boolean = it.hasNext

    /**
     * Return next record
     * 
     * @return [[FieldCollector]]
     */
    def next(): FieldCollector = {
      val row = it.next()
      val els = parseLine(row)
      FieldCollector(
        els(hdr.indexOf(conf.idCol)).toInt, els(hdr.indexOf(conf.addressCol)),
        Coordinates(els(hdr.indexOf(conf.longitudeCol)).toDouble, els(hdr.indexOf(conf.latitudeCol)).toDouble),
        els(hdr.indexOf(conf.regionCol))
      )
    }
  }

  /**
   * Produce [[Dwelling]] iterator from CSV file.
   * 
   * @param file file name
   * @param conf [[CsvConfig]]
   */
  case class DwellingIterator(file: String)(implicit val conf: CsvConfig) extends Iterator[Dwelling] {
    private val it = 
      if (file.matches("^.+.csv.gz$")) {
        val fs = new FileInputStream(file)
        val gz = new GZIPInputStream(fs)
        Source.fromInputStream(gz).getLines()
      } else {
        Source.fromFile(new File(file)).getLines()
      }

    private val hdr = it.next().split(",").toList.map(x => x.replaceAll("\"", ""))

    /**
     * Return `true` if more records exists, `false` otherwise.
     */
    def hasNext: Boolean = it.hasNext

    /**
     * Return next record
     * 
     * @return [[Dwelling]]
     */
    def next(): Dwelling = {
      val row = it.next()
      val els = parseLine(row)
      Dwelling(
        els(hdr.indexOf(conf.idCol)).toInt, els(hdr.indexOf(conf.addressCol)),
        Coordinates(els(hdr.indexOf(conf.longitudeCol)).toDouble, els(hdr.indexOf(conf.latitudeCol)).toDouble),
        els(hdr.indexOf(conf.regionCol))
      )
    }
  }

  private def parseLine(line: String): List[String] = {
    def parse(str: String, inside: Boolean, buffer: String, accum: List[String]): List[String] = str match {
      case "" =>
        if (buffer != "") accum :+ buffer else accum
      case s => 
        val h = s.head
        val t = s.tail
        if (h == '"' & !inside)
          parse(t, true, "", accum)
        else if (h == '"' & inside)
          parse(t, false, buffer, accum)
        else if (h == ',' & !inside) 
          parse(t, false, "", accum :+ buffer)
        else 
          parse(t, inside, buffer + h, accum)
    }

    parse(line, false, "", List.empty)
  }
}