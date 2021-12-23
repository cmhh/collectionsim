package org.cmhh

import akka.actor.typed.ActorRef
import messages._

/**
 * Object for monitoring single actor.
 * 
 * Object is immutable, so all methods return new objects.
 */
case class Monitor[T](
  ref: ActorRef[T], noncontacts: Int, refused: Boolean, complete: Boolean, phone: Option[String]
) {
  def noncontact: Monitor[T] = Monitor(ref, noncontacts + 1, refused, complete, phone)
  def refuse: Monitor[T] = Monitor(ref, noncontacts, true, true, phone) 
  def respond: Monitor[T] = Monitor(ref, noncontacts, false, true, phone)
  def close: Monitor[T] = Monitor(ref, noncontacts, false, true, phone)
  def addPhone(ph: String): Monitor[T] = Monitor(ref, noncontacts, refused, complete, Some(ph))
}

case object Monitor {
  def apply[T](ref: ActorRef[T]): Monitor[T] = Monitor(ref, 0, false, false, None)
}

/**
 * Object for monitoring a whole household: dwelling and individuals.
 * 
 * @param dwelling [[Monitor]] for dwelling actor
 * @param individuals vector of [[Monitor]]s for each individual
 * @param vacant$ whether vacant or not--can be unknown.
 */
case class DwellingSummary(
  dwelling: Monitor[DwellingCommand], individuals: Vector[Monitor[IndividualCommand]], 
  vacant$: Option[Boolean]
) {
  /**
   * Whether or not the overall case is complete.
   */
  def isComplete: Boolean = dwelling.complete & individuals.filter(!_.complete).size == 0

  /**
   * Get vector of incomplete individual actors
   */
  def getIncompleteIndividuals: Vector[ActorRef[IndividualCommand]] = individuals.filter(!_.complete).map(_.ref)

  /**
   * Increment dwelling non-contact attempts
   */
  def noncontact = DwellingSummary(dwelling.noncontact, individuals, vacant$)

  /**
   * Mark dwelling as refusal
   */
  def refuse = DwellingSummary(dwelling.refuse, individuals, vacant$)

  /**
   * Mark dwelling as a response
   */
  def respond = DwellingSummary(dwelling.respond, individuals, vacant$)

  /**
   * Add a dwelling phone number.  Not used.
   */
  def addPhone(ph: String) = DwellingSummary(dwelling.addPhone(ph), individuals, vacant$)

  /**
   * Mark dwelling as vacant
   */
  def vacant = DwellingSummary(dwelling.close, Vector.empty, Some(true))
  
  /**
   * Increment individual non-contact attempts.
   * 
   * @param ref actor ref of individual
   */
  def noncontact(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).noncontact,
      vacant$
    )
  }
  
  /**
   * Mark individual as refusal.
   * 
   * @param ref actor ref of individual
   */
  def refuse(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).refuse,
      vacant$
    )
  }
  
  /**
   * Mark individual as a response.
   * 
   * @param ref actor ref of individual
   */
  def respond(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).respond,
      vacant$
    )
  }
  
  /**
   * Add a phone 'number' for individual.  Not used.
   * 
   * @param ref actor ref of individual
   * @param ph string containing phone 'number'
   */
  def addPhone(ref: ActorRef[IndividualCommand], ph: String): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).addPhone(ph),
      vacant$
    )
  }
}

case object DwellingSummary {
  def apply(ref: ActorRef[DwellingCommand]): DwellingSummary = 
    DwellingSummary(Monitor(ref), Vector.empty, None)
}