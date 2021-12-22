package org.cmhh

import akka.actor.typed.ActorRef
import java.time.LocalDateTime
import messages._

case class FieldCollectorConfig(
  cases: Map[ActorRef[DwellingCommand], Dwelling], 
  summaries: Map[ActorRef[DwellingCommand], DwellingSummary],
  workload: List[(Double, Double, ActorRef[DwellingCommand])],
  currentDwelling: Option[ActorRef[DwellingCommand]],
  currentIndividuals: List[ActorRef[IndividualCommand]],
  time: Option[LocalDateTime],
  dayKms: Double,
  dayMins: Double
) {
  def getLocation: Option[Coordinates] = currentDwelling.map(x => cases(x).location)
  def getLocation(ref: ActorRef[DwellingCommand]): Coordinates = cases(ref).location

  def reset = 
    this.copy(
      workload = List.empty, currentDwelling = None, currentIndividuals = List.empty, time = None
    )

  def nextWorkloadItem: (Double, Double, ActorRef[DwellingCommand]) = 
    workload.head

  def nextIndividual: Option[ActorRef[IndividualCommand]] = 
    if (currentIndividuals.size == 0) None else Some(currentIndividuals.head)

  def getActiveDwellings: List[ActorRef[DwellingCommand]] = summaries.filter(x => !x._2.isComplete).keys.toList

  def getActiveIndividuals(ref: ActorRef[DwellingCommand]): List[ActorRef[IndividualCommand]] = 
    summaries(ref).getIncompleteIndividuals.toList

  def popWorkloadItem = 
    this.copy(
      workload = this.workload.tail
    )

  def popIndividual(ref: ActorRef[IndividualCommand]) = 
    this.copy(
      currentIndividuals = this.currentIndividuals.filter(_ != ref)
    )

  def addDwelling(ref: ActorRef[DwellingCommand], dwelling: Dwelling) = 
    this.copy(
      cases = this.cases + (ref -> dwelling), 
      summaries = this.summaries + (ref -> DwellingSummary(ref))
    )

  def addIndividuals(ref: ActorRef[DwellingCommand], individuals$: List[ActorRef[IndividualCommand]]) = 
    this.copy(
      summaries = summaries + (ref -> DwellingSummary(
        summaries(ref).dwelling, individuals$.map(x => Monitor(x)).toVector, Some(false)
      ))
    )

  def setWorkload(workload$: List[(Double, Double, ActorRef[DwellingCommand])]) = 
    this.copy(workload = workload$)

  def setCurrentDwelling(ref: ActorRef[DwellingCommand]) = 
    this.copy(currentDwelling = Some(ref))

  def setCurrentIndividuals(refs: List[ActorRef[IndividualCommand]]) = 
    this.copy(currentIndividuals = refs)

  def setTime(time$: LocalDateTime) = this.copy(time = Some(time$))
  def setTime(time$: Option[LocalDateTime]) = this.copy(time = time$)

  def setDwellingRefusal(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).refuse)
    )

  def setDwellingNoncontact(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).noncontact)
    )

  def setDwellingVacant(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).vacant)
    )

  def setDwellingResponse(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).respond)
    )

  def setIndividualRefusal(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).refuse(ref))
    )

  def setIndividualNoncontact(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).noncontact(ref))
    )

  def setIndividualResponse(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).respond(ref))
    )

  def clearWork = 
    this.copy(
      workload = List.empty, currentDwelling = None, currentIndividuals = List.empty
    )
}

case object FieldCollectorConfig {
  def apply(): FieldCollectorConfig = FieldCollectorConfig(
    Map.empty, Map.empty, List.empty, None, List.empty, None, 0, 0
  )
}