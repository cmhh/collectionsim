package org.cmhh

/**
 * Sex
 */
object sex {
  sealed trait Sex
  case object MALE extends Sex
  case object FEMALE extends Sex
}