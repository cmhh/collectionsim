package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import java.io.File
import java.time.LocalDateTime
import scala.util.{Try, Success, Failure}

/**
 * Field collector
 * 
 * Responsible for contacting and interviewing dwellings and individuals.
 */
object FieldCollectorActor {
  import messages._

  /**
   * Actor behavior
   * 
   * @param id unique ID
   * @param address address string
   * @param location location of address
   * @param eventRecorder reference for system event recorder, used for persistent logging.
   */
  def apply(
    id: Int, address: String, location: Coordinates, eventRecorder: ActorRef[EventRecorderCommand]
  ): Behavior[FieldCollectorCommand] = Behaviors.setup { context => 
    new FieldCollectorActor(id, address, location, eventRecorder, context)
      .behavior(FieldCollectorConfig())
  }
}

private class FieldCollectorActor (
  id: Int, address: String, location: Coordinates, 
  eventRecorder: ActorRef[messages.EventRecorderCommand], context: ActorContext[messages.FieldCollectorCommand]
) {    
  import messages._

  /**
   * Actor behavior
   * 
   * @param config [[FieldCollectorConfig]]
   */
  def behavior(
    config: FieldCollectorConfig 
  ): Behavior[FieldCollectorCommand] = Behaviors.receiveMessage { message => 
    message match {
      // spawn a new dwelling
      case m: Dwelling =>
        val dwelling = context.spawn(DwellingActor(m.id, m.address, m.location, eventRecorder), s"""dwelling@${m.id}""")
        eventRecorder ! DwellingRecord(dwelling.toString, m.id, m.address, m.location, m.area)
        eventRecorder ! DwellingAssignment(dwelling.toString, context.self.toString)
        behavior(config.addDwelling(dwelling, m))
      // simulate a day of work
      // get outstanding cases, and place them in appropriate driving order.
      // contact sorted cases one-by-one, send a NextCase message to self after each is completed.
      case RunDay(dt) =>
        val activeCases: List[ActorRef[DwellingCommand]] = config.getActiveDwellings
        if (activeCases.size == 0) Behaviors.same else {
          val locations = location :: activeCases.map(k => config.cases(k).location).toList

          val path: List[(Coordinates, Coordinates, Double, Double)] = Router.trip(locations) match {
            case Success(p) => p.dropRight(1)
            case Failure(e) => Router.trip0(locations).dropRight(1)
          } 

          val work: List[(Double, Double, ActorRef[DwellingCommand])] = path.map(x => {
            val coords = x._2
            val ref = activeCases.filter(k => config.cases(k).location == coords).head
            (x._3, x._4, ref)
          })
          
          context.self ! NextItem(dt)
          behavior(config.setWorkload(work).setTime(dt))
        }
      case DwellingRefusal(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "REFUSAL")
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(60 * 1)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingRefusal(ref))
      case DwellingNoncontact(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "NONCONTACT")
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(60 * 1)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingNoncontact(ref))
      case DwellingEmpty(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "EMPTY")
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(60 * 1)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingVacant(ref))
      // a dwelling will be vacant or not.  if vacant, move to next case.
      // if a dwelling is not vacant, the dwelling will reply with the list of individuals
      // the individuals will then be contacted one-by-one before moving to the next case.
      case DwellingResponse(ref, response, cases) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "RESPONSE")
        eventRecorder ! DwellingData(context.self.toString, response)
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(random.dwellingDuration)).get
        cases.head ! AttemptInterview(newtime, context.self)

        behavior(
          config
            .setDwellingResponse(ref)
            .addIndividuals(ref, cases)
            .setCurrentDwelling(ref)
            .setCurrentIndividuals(cases) 
            .setTime(newtime)
        )
      case IndividualRefusal(ref, href) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "REFUSAL")
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(60 * 1)).get

        config.nextIndividual match {
          case None =>
            context.self ! NextItem(newtime)
          case Some(nxt) =>
            nxt ! AttemptInterview(newtime, context.self)
        }

        behavior(
          config
            .setIndividualRefusal(ref, href)
            .popIndividual(ref)
            .setTime(newtime)
        )
      case IndividualNoncontact(ref, href) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "NONCONTACT")
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(60 * 1)).get
        
        config.nextIndividual match {
          case None =>
            context.self ! NextItem(newtime)
          case Some(nxt) =>
            nxt ! AttemptInterview(newtime, context.self)
        }

        behavior(
          config
            .setIndividualNoncontact(ref, href)
            .popIndividual(ref)
            .setTime(newtime)
        )
      case IndividualResponse(ref, href, response) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "RESPONSE")
        eventRecorder ! IndividualData(ref.toString, href.toString, response)
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(random.individualDuration)).get
        
        config.nextIndividual match {
          case None =>
            context.self ! NextItem(newtime)
          case Some(nxt) =>
            nxt ! AttemptInterview(newtime, context.self)
        }

        behavior(
          config
            .setIndividualResponse(ref, href)
            .popIndividual(ref)
            .setTime(newtime)
        )
      // send a request to the next case in the list of work items.
      // included in the request is an estimated arrivale time--
      // we calculate the drive time from current location to next, and add to current time.
      case NextItem(datetime) =>
        if (config.workload.size == 0 | datetime.getHour >= 18) {
          config.getLocation match {
            case Some(location) => 
              context.self ! GoHome(datetime, location)
              Behaviors.same
            case None =>
              context.log.error("Missing location.  Cannot plan route home.")
              behavior(
                config.clearWork
              )
          }
        } else {
          val workloadItem = config.nextWorkloadItem
          val nxt = workloadItem._3
          val eta: LocalDateTime = datetime.plusSeconds(workloadItem._2.toLong)

          val origin = config.currentDwelling match {
            case Some(ref) => config.getLocation(ref)
            case _ => location
          }
          
          val destination = config.getLocation(nxt)
          eventRecorder ! Trip(context.self.toString, datetime, eta, workloadItem._1, origin, destination)
          
          if (!config.summaries(nxt).dwelling.complete) {
            nxt ! AttemptInterview(eta, context.self)
            behavior(
              config
                .popWorkloadItem
                .setCurrentDwelling(nxt)
                .setCurrentIndividuals(List.empty)
                .setTime(eta)
            )
          } else {
            val resp = config.getActiveIndividuals(nxt)
            resp.head ! AttemptInterview(eta, context.self)
            behavior(
              config
                .popWorkloadItem
                .setCurrentDwelling(nxt)
                .setCurrentIndividuals(resp)
                .setTime(eta)
            )
          }
        }
      // clear workload, and calculate the route home.
      case GoHome(datetime, currentLocation) =>
        val route = Router.route(currentLocation, location) match {
          case Success(r) => r
          case Failure(e) => Router.route0(currentLocation, location)
        }

        val eta = datetime.plusSeconds(route._2.toLong)
        eventRecorder ! Trip(context.self.toString, datetime, eta, route._1, currentLocation, location)
        behavior(config.clearWork)
    }
  }
}