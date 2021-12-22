package org.cmhh

import sex._
import demographics._
import java.time.LocalDate

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef

object random {
  import NameDB._
  import messages._
  import scala.util.Random

  def normal(mean: Double, std: Double) = Random.nextGaussian() * std + mean
  def dwellingDuration: Int = math.round(normal(6, 1) * 60).toInt
  def individualDuration: Int = math.round(normal(6, 1) * 60).toInt

  def nameMale = maleNames.getRandom
  def nameFemale = femaleNames.getRandom
  def nameLast = lastNames.getRandom
  def sex: Sex = if (Random.nextDouble() < 0.5) MALE else FEMALE
  def age(lo: Int, hi: Int): Int = Random.nextInt(hi - lo) + lo  

  def dob: LocalDate = dob(0, 120)
  def dobChild: LocalDate = dob(0, 14)

  def dobCouple: (LocalDate, LocalDate) = dobCouple(18, 120)

  def dobCouple(min: Int, max: Int): (LocalDate, LocalDate) = {
    val a = age(min, max)
    (dob(math.max(a - 5, min), math.min(a + 5, max)), dob(math.max(a - 5, min), math.min(a + 5, max)))
  }

  def dob(lo: Int, hi: Int): LocalDate = { 
    assert(lo >= 0 & lo < hi)
    assert(hi <= 120)
    LocalDate.now.minusDays(Random.nextInt((hi -lo) * 365) + lo * 365)
  }

  def string: String = string(10)
  def string(n: Int): String = {
    val chars = "0123456789abcdef"
    (1 to n).map(i => chars(Random.nextInt(chars.size))).mkString
  }

  def int(hi: Int): Int = int(0, hi) + 1
  def int(lo: Int, hi: Int): Int = Random.nextInt(hi - lo) + lo + 1

  def couple: (String, String, String, LocalDate, LocalDate, Sex, Sex) = couple(18, 120)
  def couple(min: Int, max: Int): (String, String, String, LocalDate, LocalDate, Sex, Sex) = {
    val (dob1, dob2) = dobCouple(min, max)
    (nameLast, nameFemale, nameMale, dob1, dob2, FEMALE, MALE)
  }

  def hhtype: HhType = randomHhType
  def famtype: FamType = randomFamType

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