package org.cmhh

/**
 * Categories for household / dwelling variables
 */
object categories {
  
  /**
   * Household type.
   */
  sealed trait HhType
  case object OnePersonHH extends HhType
  case object OneFamilyHH extends HhType
  case object TwoFamilyHH extends HhType
  case object OtherMultHH extends HhType

  /**
   * Family type.
   */
  sealed trait FamType
  case object CoupleOnly extends FamType
  case object CoupleOnlyAndOthers extends FamType
  case object CoupleWithChildren extends FamType
  case object CoupleWithChildrenAndOthers extends FamType
  case object OneParentWithChildren extends FamType
  case object OneParentWithChildrenAndOthers extends FamType

  sealed trait Sex
  case object MALE extends Sex
  case object FEMALE extends Sex
}