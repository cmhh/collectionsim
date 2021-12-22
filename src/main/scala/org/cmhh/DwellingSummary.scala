package org.cmhh

import akka.actor.typed.ActorRef
import messages._

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

case class DwellingSummary(
  dwelling: Monitor[DwellingCommand], individuals: Vector[Monitor[IndividualCommand]], 
  vacant$: Option[Boolean]
) {
  def isComplete: Boolean = dwelling.complete & individuals.filter(!_.complete).size == 0
  def getIncompleteIndividuals: Vector[ActorRef[IndividualCommand]] = individuals.filter(!_.complete).map(_.ref)

  def noncontact = DwellingSummary(dwelling.noncontact, individuals, vacant$)
  def refuse = DwellingSummary(dwelling.refuse, individuals, vacant$)
  def respond = DwellingSummary(dwelling.respond, individuals, vacant$)
  def addPhone(ph: String) = DwellingSummary(dwelling.addPhone(ph), individuals, vacant$)
  def vacant = DwellingSummary(dwelling.close, Vector.empty, Some(true))
  
  def noncontact(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).noncontact,
      vacant$
    )
  }
  
  def refuse(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).refuse,
      vacant$
    )
  }
  
  def respond(ref: ActorRef[IndividualCommand]): DwellingSummary = {
    val i = individuals.indexWhere(_.ref == ref)
    DwellingSummary(
      dwelling,
      individuals.filter(_.ref != ref) :+ individuals(i).respond,
      vacant$
    )
  }
  
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