package org.cmhh

import scala.util.Random

object demographics {
  sealed trait HhType{val prob: Double}
  case object OnePersonHH extends HhType{val prob: Double = 0.227441234181339}
  case object OneFamilyHH extends HhType{val prob: Double = 0.686168716018560}
  case object TwoFamilyHH extends HhType{val prob: Double = 0.035188671940655}
  case object OtherMultHH extends HhType{val prob: Double = 0.051201377859446}

  private val hhtypes = List(OnePersonHH, OneFamilyHH, TwoFamilyHH, OtherMultHH)

  def randomHhType: HhType = {
    val r = Random.nextDouble()
    def loop(hhtypes: Seq[HhType] = hhtypes, accum: Double = 0): HhType = hhtypes match {
      case h::Nil => h
      case h::t => 
        if (r < accum + h.prob) h
        else loop(t, accum + h.prob)
    }
    loop()
  }

  sealed trait FamType{val prob: Double}
  case object CoupleOnly extends FamType{val prob: Double = 0.372531912434152}
  case object CoupleOnlyAndOthers extends FamType{val prob: Double = 0.037557315777596}
  case object CoupleWithChildren extends FamType{val prob: Double = 0.398532495912896}
  case object CoupleWithChildrenAndOthers extends FamType{val prob: Double = 0.037309612537087}
  case object OneParentWithChildren extends FamType{val prob: Double = 0.124743351920251}
  case object OneParentWithChildrenAndOthers extends FamType{val prob: Double = 0.029325311418019}

  private val famtypes = List(
    CoupleOnly, CoupleOnlyAndOthers, CoupleWithChildren, CoupleWithChildrenAndOthers, 
    OneParentWithChildren, OneParentWithChildrenAndOthers
  )

  def randomFamType: FamType = {
    val r = Random.nextDouble()
    def loop(famtypes: Seq[FamType] = famtypes, accum: Double = 0): FamType = famtypes match {
      case h::Nil => h
      case h::t => 
        if (r < accum + h.prob) h
        else loop(t, accum + h.prob)
    }
    loop()
  }
}