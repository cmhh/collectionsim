package org.cmhh

import java.time.LocalDate
import com.typesafe.config.Config
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef

/**
 * Functions for generating random objects.
 */
case class Rand()(implicit conf: Config) {
  import NameDB._
  import messages._
  import categories._
  import scala.util.Random

  val PROP_MALE = conf.getDouble("demographic-settings.proportion-male")
  val MAX_AGE = conf.getInt("demographic-settings.max-age")
  val MIN_AGE_COUPLE = conf.getInt("demographic-settings.min-age-couple")
  val HHLD_DURATION_MEAN = conf.getInt("collection-settings.household.duration-mean")
  val HHLD_DURATION_STDEV = conf.getInt("collection-settings.household.duration-stdev")
  val IND_DURATION_MEAN = conf.getInt("collection-settings.individual.duration-mean")
  val IND_DURATION_STDEV = conf.getInt("collection-settings.individual.duration-stdev")

  /**
   * Random normal.
   * 
   * @param mean mean
   * @param std standard deviation
   */
  def normal(mean: Double, std: Double) = Random.nextGaussian() * std + mean

  /**
   * Random number representing duration of dwelling questionnaire in seconds
   */
  def dwellingDuration: Int = math.round(
    normal(HHLD_DURATION_MEAN, HHLD_DURATION_STDEV) * 60
  ).toInt

  /**
   * Random number representing duration of individual questionnaire in seconds
   */
  def individualDuration: Int = math.round(
    normal(IND_DURATION_MEAN, IND_DURATION_STDEV) * 60
  ).toInt

  /**
   * Random male name
   */
  def nameMale = maleNames.getRandom

  /**
   * Random female name
   */
  def nameFemale = femaleNames.getRandom

  /**
   * Random family name
   */
  def nameLast = lastNames.getRandom

  /**
   * Random sex
   */
  def sex: Sex = 
    if (Random.nextDouble() < PROP_MALE) 
      MALE 
    else 
      FEMALE

  /**
   * Random age in range [lo, hi]
   * 
   * @param lo lower limit for age
   * @param hi upper limit for age
   */
  def age(lo: Int, hi: Int): Int = Random.nextInt(hi - lo) + lo  

  /**
   * Random date of birth.
   * 
   * Generated birth date will be between 0 and 120 years prior to current date.
   */
  def dob: LocalDate = dob(0, MAX_AGE)

  /**
   * Random date of birth.
   * 
   * Generated birth date will be between 0 and 14 years prior to current date.
   */
  def dobChild: LocalDate = dob(0, 14)

  /**
   * Birthdates for a couple
   * 
   * Generated birth date will be between 18 and 120 years prior to current date, 
   * and will be within +/- 5 years of each other.
   */
  def dobCouple: (LocalDate, LocalDate) = dobCouple(MIN_AGE_COUPLE, MAX_AGE)

  /**
   * Birthdates for a couple
   * 
   * @param min min age for either person in couple
   * @param max maximum age for either person in couple
   */
  def dobCouple(min: Int, max: Int): (LocalDate, LocalDate) = {
    val a = age(min, max)
    (dob(math.max(a - 5, min), math.min(a + 5, max)), dob(math.max(a - 5, min), math.min(a + 5, max)))
  }

  /**
   * Random date of birth.
   * 
   * @param lo lower age limit
   * @param hi upper age limit
   */
  def dob(lo: Int, hi: Int): LocalDate = { 
    assert(lo >= 0 & lo < hi)
    assert(hi <= MAX_AGE)
    LocalDate.now.minusDays(Random.nextInt((hi -lo) * 365) + lo * 365)
  }

  /**
   * A random 10-character string.
   */
  def string: String = string(10)

  /**
   * A random string.
   * 
   * @param n length of string
   */
  def string(n: Int): String = {
    val chars = "0123456789abcdef"
    (1 to n).map(i => chars(Random.nextInt(chars.size))).mkString
  }

  /**
   * Random integer in range [0, hi]
   * 
   * @param hi upper bound for result
   */
  def int(hi: Int): Int = int(0, hi) + 1

  /**
   * Random integer in range [lo, hi]
   * 
   * @param lo lower bound for result
   * @param hi upper bound for result
   */
  def int(lo: Int, hi: Int): Int = Random.nextInt(hi - lo) + lo + 1

  /**
   * Tuple representing demographics of a copule
   */
  def couple: (String, String, String, LocalDate, LocalDate, Sex, Sex) = couple(MIN_AGE_COUPLE, MAX_AGE)

  /**
   * Tuple representing demographics of a couple
   */
  def couple(min: Int, max: Int): (String, String, String, LocalDate, LocalDate, Sex, Sex) = {
    val (dob1, dob2) = dobCouple(min, max)
    (nameLast, nameFemale, nameMale, dob1, dob2, FEMALE, MALE)
  }

  /**
   * Random household type
   */
  def hhtype: HhType = {
    val c = conf.getConfig("demographic-settings.household-type")

    val prob = Vector("one-person", "one-family", "two-family", "other-mult")
      .map(x => c.getDouble(x))
      .scanLeft(0.0)(_ + _)

    val rng = prob.dropRight(1).zip(prob.drop(1))
    val cats = Vector(OnePersonHH, OneFamilyHH, TwoFamilyHH, OtherMultHH)    
    val r = Random.nextDouble()
    rng.zip(cats).filter(x => r >= x._1._1 & r < x._1._2).head._2
  }

  /**
   * Random family type
   */
  def famtype: FamType = {
    val c = conf.getConfig("demographic-settings.family-type")

    val prob = Vector(
      "couple-only", "couple-only-and-others", "couple-with-children", 
      "couple-with-children-and-others", "one-parent-with-children", 
      "one-parent-with-children-and-others"
    )
      .map(x => c.getDouble(x))
      .scanLeft(0.0)(_ + _)

    val rng = prob.dropRight(1).zip(prob.drop(1))

    val cats = Vector(
      CoupleOnly, CoupleOnlyAndOthers, CoupleWithChildren, CoupleWithChildrenAndOthers, 
      OneParentWithChildren, OneParentWithChildrenAndOthers
    )

    val r = Random.nextDouble()
    rng.zip(cats).filter(x => r >= x._1._1 & r < x._1._2).head._2
  } 

  /**
   * Spawn an individual actor representing an adult
   */
  def adult(
    context: ActorContext[DwellingCommand], 
    first: Option[String] = None, last: Option[String] = None, 
    dob$: Option[LocalDate] = None, sex$: Option[Sex] = None
  ): ActorRef[IndividualCommand] = {
    val s = sex$ match {
      case Some(x) => x
      case _ => sex
    }

    val f = first match {
      case Some(x) => x
      case _ => s match {
        case FEMALE => nameFemale
        case MALE => nameMale
      }
    }

    val l = last match {
      case Some(x) => x
      case _ => nameLast
    }

    val d = dob$ match {
      case Some(x) => x
      case _ => dob(MIN_AGE_COUPLE, MAX_AGE)
    }

    context.spawn(IndividualActor(f, l, d, s, context.self), s"""$l@$f@${string(5)}""")
  }

  /**
   * Spawn an individual actor representing a child
   */
  def child(context: ActorContext[DwellingCommand], last: Option[String] = None): ActorRef[IndividualCommand] = {
    val s = sex

    val f = s match {
      case MALE => nameMale
      case FEMALE => nameFemale
    }

    val l = last match {
      case Some(n) => n
      case _ => nameLast
    }

    val d = dob(MIN_AGE_COUPLE, MAX_AGE)
    context.spawn(IndividualActor(f, l, d, s, context.self), s"""$l@$f@${string(5)}""")
  }
}