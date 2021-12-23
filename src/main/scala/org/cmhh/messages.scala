package org.cmhh

import akka.actor.typed.ActorRef
import java.time.{LocalDate, LocalDateTime}
import area._

/**
 * Message interface
 */
object messages {
  // -> coordinator
  sealed trait CoordinatorCommand 

  // -> area coordinators
  sealed trait AreaCoordinatorCommand

  // -> field collector
  sealed trait FieldCollectorCommand
  case class DwellingRefusal(ref: ActorRef[DwellingCommand]) extends FieldCollectorCommand
  case class DwellingEmpty(ref: ActorRef[DwellingCommand]) extends FieldCollectorCommand
  case class DwellingNoncontact(ref: ActorRef[DwellingCommand]) extends FieldCollectorCommand
  case class DwellingResponse(ref: ActorRef[DwellingCommand], response: String, cases: List[ActorRef[IndividualCommand]]) extends FieldCollectorCommand
  case class IndividualRefusal(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) extends FieldCollectorCommand
  case class IndividualNoncontact(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand]) extends FieldCollectorCommand
  case class IndividualResponse(ref: ActorRef[IndividualCommand], href: ActorRef[DwellingCommand], response: String) extends FieldCollectorCommand
  case class NextItem(datetime: LocalDateTime) extends FieldCollectorCommand
  case class GoHome(datetime: LocalDateTime, location: Coordinates) extends FieldCollectorCommand

  // -> dwelling
  sealed trait DwellingCommand

  // -> individual
  sealed trait IndividualCommand

  // field collector -> dwelling, individual
  case class AttemptInterview(datetime: LocalDateTime, replyTo: ActorRef[FieldCollectorCommand]) extends DwellingCommand with IndividualCommand

  // -> coordinator -> area coordinator
  case class FieldCollector(id: Int, address: String, location: Coordinates, area: String) extends CoordinatorCommand with AreaCoordinatorCommand

  // -> coordinator -> area coordinator -> field collector
  case class Dwelling(id: Int, address: String, location: Coordinates, area: String) extends CoordinatorCommand with AreaCoordinatorCommand with FieldCollectorCommand
  case class RunDay(datetime: LocalDateTime) extends CoordinatorCommand with AreaCoordinatorCommand with FieldCollectorCommand 

  // -> event recorder
  sealed trait EventRecorderCommand
  case class CollectorRecord(ref: String, id: Int, address: String, location: Coordinates, area: String) extends EventRecorderCommand
  case class DwellingRecord(ref: String, id: Int, address: String, location: Coordinates, area: String) extends EventRecorderCommand
  case class DwellingAssignment(dwellingRef: String, collectorRef: String) extends EventRecorderCommand
  case class DwellingData(ref: String, response: String) extends EventRecorderCommand
  case class IndividualData(ref: String, dwellingRef: String, response: String) extends EventRecorderCommand
  case class Interview(collectorRef: String, dwellingRef: String, ref: String, datetime: LocalDateTime, status: String) extends EventRecorderCommand
  case class Trip(ref: String, startTime: LocalDateTime, endTime: LocalDateTime, distance: Double, origin: Coordinates, destination: Coordinates) extends EventRecorderCommand

  // -> coordinator -> event recorder
  case object Flush extends CoordinatorCommand with EventRecorderCommand
}