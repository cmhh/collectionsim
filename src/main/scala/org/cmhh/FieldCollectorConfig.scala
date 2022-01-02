package org.cmhh

import akka.actor.typed.ActorRef
import java.time.LocalDateTime
import messages._

/**
 * Object to simplify state maintained by field collector.
 * 
 * The field collector actor maintains a number of data items, and passing
 * around a single configuration object, rather than a large number of 
 * separate items, simplifies the management of actor behavior.
 * 
 * @param cases a map containing all dwelling references, and the original dwelling message
 * @param summaries a map containing summary information for all cases (number of non-contacts, etc.)
 * @param workload the workload for the current day, consisting of (distance, time, dwelling ref) tuples
 * @param currentDwelling the reference for the current dwelling case
 * @param currentIndividuals a list of individuals in the current dwelling that have yet to be resolved
 * @param time the current date and time (in simulation time)
 * @param dayKms total kms travelled for the day (unused / not implemented--will remain 0)
 * @param dayMins total minutes spend working for the day (unused / not implemented--will remain 0)
 */
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
  /**
   * Current location
   */
  def getLocation: Option[Coordinates] = currentDwelling.map(x => cases(x).location)

  /**
   * Location of specific dwelling
   * 
   * @param ref reference of dwelling actor
   */
  def getLocation(ref: ActorRef[DwellingCommand]): Coordinates = cases(ref).location

  /**
   * Clear daily workload
   */
  def reset = 
    this.copy(
      workload = List.empty, currentDwelling = None, currentIndividuals = List.empty, time = None,
      dayKms = 0, dayMins = 0
    )

  /**
   * Get next work item as (distance, time, actor reference) tuple
   */
  def nextWorkloadItem: Option[(Double, Double, ActorRef[DwellingCommand])] = 
    if (workload.size == 0) None else Some(workload.head)

  def nextWorkloadItem(ref: ActorRef[DwellingCommand]): Option[(Double, Double, ActorRef[DwellingCommand])] = {
    val w = workload.filter(x => x._3 != ref)
    if (w.size == 0) None else Some(w.head)
  }

  /**
   * Get next individual from workload
   */
  def nextIndividual: Option[ActorRef[IndividualCommand]] = 
    if (currentIndividuals.size == 0) None else Some(currentIndividuals.head)

  /**
   * Get next individual from workload that isn't a specific individual
   */
  def nextIndividual(ref: ActorRef[IndividualCommand]): Option[ActorRef[IndividualCommand]] = {
    val ind = currentIndividuals.filter(_ != ref)
    if (ind.size == 0) None else Some(ind.head)
  }

  /**
   * Return a list of active dwellings, i.e. incomplete dwelling cases
   */
  def getActiveDwellings: List[ActorRef[DwellingCommand]] = summaries.filter(x => !x._2.isComplete).keys.toList

  /**
   * Return a list of active / incomplete individuals for a specific dwelling
   * 
   * @param ref actor reference of dwelling
   */
  def getActiveIndividuals(ref: ActorRef[DwellingCommand]): List[ActorRef[IndividualCommand]] = 
    summaries(ref).getIncompleteIndividuals.toList

  /**
   * Remove the current case from the workload
   */
  def popWorkloadItem = 
    this.copy(
      workload = this.workload.tail
    )

  /**
   * Remove the current individual from the set of current individuals
   */
  def popIndividual(ref: ActorRef[IndividualCommand]) = 
    this.copy(
      currentIndividuals = this.currentIndividuals.filter(_ != ref)
    )

  /**
   * Add a dwelling to the configuration.
   * 
   * A dwelling is added to both `cases` and `summaries`.
   * 
   * @param ref dwelling actor reference
   * @param dwelling a `Dwelling` message
   */
  def addDwelling(ref: ActorRef[DwellingCommand], dwelling: Dwelling) = 
    this.copy(
      cases = this.cases + (ref -> dwelling), 
      summaries = this.summaries + (ref -> DwellingSummary(ref))
    )

  /**
   * Add individuals to configuration.
   * 
   * Individuals are attached to the appropriate dwelling in `summaries`.
   * 
   * @param ref dwelling actor reference
   * @param individuals$ 
   */
  def addIndividuals(ref: ActorRef[DwellingCommand], individuals$: List[ActorRef[IndividualCommand]]) = 
    this.copy(
      summaries = summaries + (ref -> DwellingSummary(
        summaries(ref).dwelling, individuals$.map(x => Monitor(x)).toVector, Some(false)
      ))
    )

  /**
   * Set configuration workload.
   * 
   * Workload is an ordered set of incomplete cases, and is a list of 
   * (distance, drivetime, actor reference) tuples.
   * 
   * @param workload$ list of (distance, drivetime, actor reference) tuples
   */
  def setWorkload(workload$: List[(Double, Double, ActorRef[DwellingCommand])]) = 
    this.copy(workload = workload$)

  /**
   * Set current dwelling.
   * 
   * @param ref dwelling actor reference
   */
  def setCurrentDwelling(ref: ActorRef[DwellingCommand]) = 
    this.copy(currentDwelling = Some(ref))

  /**
   * Set current individuals.
   * 
   * @param refs list of individual actor references
   */
  def setCurrentIndividuals(refs: List[ActorRef[IndividualCommand]]) = 
    this.copy(currentIndividuals = refs)

  /**
   * Set current time.
   * 
   * @param time$ datetime
   */
  def setTime(time$: LocalDateTime) = this.copy(time = Some(time$))

  /**
   * Set current time.
   * 
   * @param time$ optional datetime
   */
  def setTime(time$: Option[LocalDateTime]) = this.copy(time = time$)

  /**
   * Increment kilimeters.
   * 
   * @param kms minutes to add
   */
  def incrementKms(kms: Double) = this.copy(dayKms = this.dayKms + kms)

  /**
   * Increment minutes.
   * 
   * @param mins minutes to add
   */
  def incrementMins(mins: Double) = this.copy(dayMins = this.dayMins + mins)

  /**
   * Mark dwelling as a refusal.
   * 
   * @param ref dwelling actor reference
   */
  def setDwellingRefusal(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).refuse)
    )

  /**
   * Increment dwelling non-contact.
   * 
   * @param ref dwelling actor reference
   */
  def setDwellingNoncontact(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).noncontact)
    )

  /**
   * Mark dwelling as vacant.
   * 
   * @param ref dwelling actor reference
   */
  def setDwellingVacant(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).vacant)
    )

  /**
   * Mark dwelling as a response.
   * 
   * @param ref dwelling actor reference
   */
  def setDwellingResponse(ref: ActorRef[DwellingCommand]) = 
    this.copy(
      summaries = this.summaries + (ref -> this.summaries(ref).respond)
    )

  /**
   * Mark individual as refusal.
   * 
   * @param ref individual actor reference
   * @param href dwelling actor reference
   */
  def setIndividualRefusal(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).refuse(ref))
    )

  /**
   * Increment individual non-contacts.
   * 
   * @param ref individual actor reference
   * @param href dwelling actor reference
   */
  def setIndividualNoncontact(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).noncontact(ref))
    )

  /**
   * Mark individual as response.
   * 
   * @param ref individual actor reference
   * @param href dwelling actor reference
   */
  def setIndividualResponse(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) =
    this.copy(
      summaries = this.summaries + (href -> summaries(href).respond(ref))
    )

  /**
   * Clear work.
   * 
   * Set workload, current dwelling, and current individuals to empty.
   */
  def clearWork = 
    this.copy(
      workload = List.empty, currentDwelling = None, currentIndividuals = List.empty,
      dayKms = 0, dayMins = 0
    )
}

/**
 * Empty [[FieldCollectorConfig]]
 */
case object FieldCollectorConfig {
  def apply(): FieldCollectorConfig = FieldCollectorConfig(
    Map.empty, Map.empty, List.empty, None, List.empty, None, 0, 0
  )
}