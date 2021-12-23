package org.cmhh

import akka.actor.typed.scaladsl.{Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import java.io.File

/**
 * System coordinator.
 * 
 * Single point of contact for actor system.  
 */
object Coordinator {
  import messages._
  import area._

  /**
   * Actor behavior
   * 
   * @param dbpath path to database created by event recorder
   */
  def apply(dbpath: String): Behavior[CoordinatorCommand] = Behaviors.setup { context => 
    val eventRecorder: ActorRef[EventRecorderCommand] = context.spawn(EventRecorder(dbpath, 100), "eventrecorder")

    val areaCoordinators: Map[Area, ActorRef[AreaCoordinatorCommand]] = 
      area.areas.map(x => {
        (x, context.spawn(AreaCoordinator(eventRecorder), s"areacoordinator@${x.toString}"))
      }).toMap

    Behaviors.receiveMessage { message => {
      message match {
        case m: FieldCollector =>
          areaCoordinators(area.get(m.area)) ! m
        case m: Dwelling =>
          areaCoordinators(area.get(m.area)) ! m
        case m: RunDay =>
          areaCoordinators.foreach(x => x._2 ! m)
        case Flush =>
          eventRecorder ! Flush
      }
      Behaviors.same
    }}
  }
}