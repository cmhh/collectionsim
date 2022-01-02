package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
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
  import implicits.{random, router, conf}

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
)(implicit val random: Rand, val router: Router, conf: Config) {    
  import messages._

  private val CONNECT_TIMEOUT = conf.getInt("router-settings.connect-timeout")
  private val READ_TIMEOUT = conf.getInt("router-settings.read-timeout")
  private val AVE_SPEED = conf.getDouble("router-settings.average-speed")
  private val RATEUP = conf.getDouble("router-settings.rateup")
  private val MAX_MINS = conf.getDouble("collection-settings.collector.max-daily-work-minutes")

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
        if (activeCases.size == 0) {
          behavior(config.clearWork)
        } else {
          val locations = location :: activeCases.map(k => config.cases(k).location).toList

          val path: List[(Coordinates, Coordinates, Double, Double)] = 
            router.trip(locations, CONNECT_TIMEOUT, READ_TIMEOUT) match {
              case Success(p) => p.dropRight(1)
              case Failure(e) => router.trip0(locations, AVE_SPEED, RATEUP).dropRight(1)
            } 

          val work: List[(Double, Double, ActorRef[DwellingCommand])] = path.map(x => {
            val coords = x._2
            val ref = activeCases.filter(k => config.cases(k).location == coords).head
            (x._3, x._4, ref)
          })
          
          context.self ! NextItem(dt)
          behavior(config.clearWork.setWorkload(work).setTime(dt))
        }
      case DwellingRefusal(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "REFUSAL")
        val duration = random.dwellingRefusalDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingRefusal(ref).incrementMins(duration / 60.0))
      case DwellingNoncontact(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "NONCONTACT")
        val duration = random.dwellingNoncontactDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingNoncontact(ref).incrementMins(duration / 60.0))
      case DwellingEmpty(ref) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "EMPTY")
        val duration = random.dwellingEmptyDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        context.self ! NextItem(newtime)
        behavior(config.setDwellingVacant(ref).incrementMins(duration / 60.0))
      // a dwelling will be vacant or not.  if vacant, move to next case.
      // if a dwelling is not vacant, the dwelling will reply with the list of individuals
      // the individuals will then be contacted one-by-one before moving to the next case.
      case DwellingResponse(ref, response, cases) =>
        eventRecorder ! Interview(context.self.toString, ref.toString, ref.toString, config.time.get, "RESPONSE")
        eventRecorder ! DwellingData(context.self.toString, response)
        val duration = random.dwellingResponseDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        cases.head ! AttemptInterview(newtime, context.self)

        behavior(
          config
            .setDwellingResponse(ref)
            .addIndividuals(ref, cases)
            .setCurrentDwelling(ref)
            .setCurrentIndividuals(cases) 
            .setTime(newtime)
            .incrementMins(duration / 60.0)
        )
      case IndividualRefusal(ref, href) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "REFUSAL")
        val duration = random.individualRefusalDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get 
        
        config.nextIndividual(ref) match {
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
            .incrementMins(duration / 60.0)
        )
      case IndividualNoncontact(ref, href) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "NONCONTACT")
        val duration = random.individualNoncontactDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        
        config.nextIndividual(ref) match {
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
            .incrementMins(duration / 60.0)
        )
      case IndividualResponse(ref, href, response) =>
        eventRecorder ! Interview(context.self.toString, href.toString, ref.toString, config.time.get, "RESPONSE")
        eventRecorder ! IndividualData(ref.toString, href.toString, response)
        val duration = random.individualResponseDuration
        val newtime: LocalDateTime = config.time.map(_.plusSeconds(duration)).get
        
        config.nextIndividual(ref) match {
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
            .incrementMins(duration / 60.0)
        )
      // send a request to the next case in the list of work items.
      // included in the request is an estimated arrivale time--
      // we calculate the drive time from current location to next, and add to current time.
      case NextItem(datetime) =>
        // invariant:
        // workload contains previously worked on item at head
        // if currentDwelling is None, then no work has been done in the current day
        if (config.dayMins >= MAX_MINS) {
          config.getLocation match {
            case Some(location) => 
              context.self ! GoHome(datetime, location)
              Behaviors.same
            case None =>
              context.log.error("(1) Missing location.  Cannot plan route home.")
              behavior(
                config.clearWork
              )
          }
        } else {        
          val workloadItem = config.currentDwelling match {
            case None => config.nextWorkloadItem
            case Some(ref) => config.nextWorkloadItem(ref)
          }

          workloadItem match {
            case None => {
              config.getLocation match {
                case Some(location) => 
                  context.self ! GoHome(datetime, location)
                  Behaviors.same
                case None =>
                  context.log.error(s"""(2) Missing location @ ${datetime}.  Cannot plan route home.""")
                  behavior(config.clearWork)
              }
            }
            case Some((distance, duration, nxt)) => {
              val eta: LocalDateTime = datetime.plusSeconds(duration.toLong)

              val origin = config.currentDwelling match {
                case Some(ref) => config.getLocation(ref)
                case _ => location
              }
              
              val destination = config.getLocation(nxt)
              eventRecorder ! Trip(context.self.toString, datetime, eta, distance, origin, destination)
              
              if (!config.summaries(nxt).dwelling.complete) {
                nxt ! AttemptInterview(eta, context.self)
                behavior(
                  config
                    .popWorkloadItem
                    .setCurrentDwelling(nxt)
                    .setCurrentIndividuals(List.empty)
                    .setTime(eta)
                    .incrementKms(distance / 1000.0)
                    .incrementMins(duration / 60.0)
                )
              } else {
                val resp = config.getActiveIndividuals(nxt)
                if (resp.size == 0) {
                  context.log.error(s"Attempt completed case @ ${datetime} - (${nxt}).  Skipping...")
                  context.self ! NextItem(datetime)
                  behavior(
                    config
                      .popWorkloadItem
                      .incrementKms(distance / 1000.0)
                  )
                } else {
                  resp.head ! AttemptInterview(eta, context.self)
                  behavior(
                    config
                      .popWorkloadItem
                      .setCurrentDwelling(nxt)
                      .setCurrentIndividuals(resp)
                      .setTime(eta)
                      .incrementKms(distance / 1000.0)
                      .incrementMins(duration / 60.0)
                  )
                }
              }
            }
          }
        }
      // clear workload, and calculate the route home.
      case GoHome(datetime, currentLocation) =>
        val route = router.route(currentLocation, location, CONNECT_TIMEOUT, READ_TIMEOUT) match {
          case Success(r) => r
          case Failure(e) => router.route0(currentLocation, location, AVE_SPEED, RATEUP)
        }

        val eta = datetime.plusSeconds(route._2.toLong)
        eventRecorder ! Trip(context.self.toString, datetime, eta, route._1, currentLocation, location)
        behavior(config.clearWork)
    }
  }
}