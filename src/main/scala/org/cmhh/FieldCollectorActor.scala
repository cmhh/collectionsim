package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.ask
import java.io.File
import java.time.LocalDateTime
import scala.util.{Try, Success, Failure}

object FieldCollectorActor {
  import messages._

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

  def behavior(
    config: FieldCollectorConfig 
  ): Behavior[FieldCollectorCommand] = Behaviors.receiveMessage { message => 
    message match {
      case CountCases => 
        if (config.cases.size > 0) println(s"""[${context.self}] - I have ${config.cases.size} cases.""")
        Behaviors.same
      case CountResidents =>
        config.cases.keys.foreach(x => x ! CountResidents)
        Behaviors.same
      case m: Dwelling =>
        val dwelling = context.spawn(DwellingActor(m.id, m.address, m.location, eventRecorder), s"""dwelling@${m.id}""")
        eventRecorder ! DwellingRecord(dwelling.toString, m.id, m.address, m.location, m.area)
        eventRecorder ! DwellingAssignment(dwelling.toString, context.self.toString)
        behavior(config.addDwelling(dwelling, m))
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

          //log the trip
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