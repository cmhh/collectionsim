package org.cmhh

/**
 * Collection areas.
 */
object area {
  sealed trait Area
  case object UPPERNORTH extends Area {val codes = List("01", "02")}
  case object LOWERNORTH extends Area {val codes = List("09")}
  case object CENTRALNORTH extends Area {val codes = List("03", "04", "05", "06", "07", "08")}
  case object CENTRALSOUTH extends Area {val codes = List("13")}
  case object UPPERSOUTH extends Area {val codes = List("12", "16", "17", "18")}
  case object LOWERSOUTH extends Area {val codes = List("14", "15")}

  val areas = List(
    UPPERNORTH, CENTRALNORTH, LOWERNORTH, UPPERSOUTH, CENTRALSOUTH, LOWERSOUTH
  )

  /**
   * Return collection area given regional council code.
   * 
   * @param region regional council code
   * @return [[Area]]
   */
  def get(region: String): Area = region match {
    case "01" => UPPERNORTH
    case "02" => UPPERNORTH
    case "03" => CENTRALNORTH
    case "04" => CENTRALNORTH
    case "05" => CENTRALNORTH
    case "06" => CENTRALNORTH
    case "07" => CENTRALNORTH
    case "08" => CENTRALNORTH
    case "09" => LOWERNORTH
    case "12" => UPPERSOUTH
    case "13" => CENTRALSOUTH
    case "14" => LOWERSOUTH
    case "15" => LOWERSOUTH
    case "16" => UPPERSOUTH
    case "17" => UPPERSOUTH
    case "18" => UPPERSOUTH
  }
}