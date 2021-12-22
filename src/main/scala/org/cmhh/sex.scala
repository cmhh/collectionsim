package org.cmhh

object sex {
  sealed trait Sex
  case object MALE extends Sex
  case object FEMALE extends Sex
}