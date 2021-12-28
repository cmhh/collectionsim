package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps, ActorContext}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
import java.io.File
import scala.util.{Try, Success, Failure}

/**
 * Area coordinator.
 * 
 * Responsible for spawning and communicating with a subset of collectors in a
 * specific area.  
 */
object AreaCoordinator {
  import messages._
  import implicits.{router, conf}
  
  /**
   * Actor behavior
   * 
   * @param eventRecorder reference for system event recorder, used for persistent logging.
   */
  def apply(eventRecorder: ActorRef[EventRecorderCommand]): Behavior[AreaCoordinatorCommand] = Behaviors.setup{context => 
    new AreaCoordinator(eventRecorder, context).behavior(Map.empty, Map.empty)
  }
}

private class AreaCoordinator(
  eventRecorder: ActorRef[messages.EventRecorderCommand],
  context: ActorContext[messages.AreaCoordinatorCommand]
)(implicit val router: Router, conf: Config) {
  import messages._

  private val CONNECT_TIMEOUT = conf.getInt("router-settings.connect-timeout")
  private val READ_TIMEOUT = conf.getInt("router-settings.read-timeout")
  private val AVE_SPEED = conf.getDouble("router-settings.average-speed")
  private val RATEUP = conf.getDouble("router-settings.rateup")

  /**
   * Actor behavior
   * 
   * @param collectors retain a record of all spawned collectors--key is the reference, value is the original message
   * @param workloads retain a record of how many dwellings have been assigned to each collector
   */
  def behavior(
    collectors: Map[ActorRef[FieldCollectorCommand], FieldCollector], 
    workloads: Map[ActorRef[FieldCollectorCommand], Int]
  ): Behavior[AreaCoordinatorCommand] = Behaviors.receiveMessage{ message => 
    message match {
      // spawn collector and log.
      case m: FieldCollector => 
        val collector = context.spawn(FieldCollectorActor(m.id, m.address, m.location, eventRecorder), s"collector@${m.id}")
        eventRecorder ! CollectorRecord(collector.toString, m.id, m.address, m.location, m.area)
        behavior(collectors + (collector -> m), workloads + (collector -> 0))
      // decide which collector to send Dwelling to
      // choose closest collector who is under the work threshold
      case m: Dwelling => 
        val available1 = collectors.filter(collector => workloads(collector._1) < 50)
        val available2 = if (available1.size > 0) available1 else collectors
        val distances = available2.map(collector => {
          val d = router.route(m.location, collector._2.location, CONNECT_TIMEOUT, READ_TIMEOUT) match {
            case Success(x) => x._1
            case Failure(e) => router.route0(m.location, collector._2.location, AVE_SPEED, RATEUP)._1
          }
          (collector._1, d)
        }).toMap
        val min = distances.map(x => x._2).min
        val closest = distances.keys.filter(collector => distances(collector) == min).head
        closest ! m
        behavior(collectors, workloads + (closest -> (workloads(closest) + 1)))
      case m: RunDay =>
        collectors.keys.foreach(collector => collector ! m)
        Behaviors.same
    }
  }
}