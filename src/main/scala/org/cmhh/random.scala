package org.cmhh

import sex._
import demographics._
import java.time.LocalDate

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef

/**
 * Functions for generating random objects.
 */
object random {
  import NameDB._
  import messages._
  import scala.util.Random

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
  def dwellingDuration: Int = math.round(normal(6, 1) * 60).toInt

  /**
   * Random number representing duration of individual questionnaire in seconds
   */
  def individualDuration: Int = math.round(normal(6, 1) * 60).toInt

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
  def sex: Sex = if (Random.nextDouble() < 0.5) MALE else FEMALE

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
  def dob: LocalDate = dob(0, 120)

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
  def dobCouple: (LocalDate, LocalDate) = dobCouple(18, 120)

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
    assert(hi <= 120)
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
  def couple: (String, String, String, LocalDate, LocalDate, Sex, Sex) = couple(18, 120)

  /**
   * Tuple representing demographics of a copule
   */
  def couple(min: Int, max: Int): (String, String, String, LocalDate, LocalDate, Sex, Sex) = {
    val (dob1, dob2) = dobCouple(min, max)
    (nameLast, nameFemale, nameMale, dob1, dob2, FEMALE, MALE)
  }

  /**
   * Random household type
   */
  def hhtype: HhType = randomHhType

  /**
   * Random family type
   */
  def famtype: FamType = randomFamType

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
      case _ => dob(18, 120)
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

    val d = dob(18, 120)
    context.spawn(IndividualActor(f, l, d, s, context.self), s"""$l@$f@${string(5)}""")
  }
}