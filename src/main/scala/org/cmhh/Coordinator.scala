package org.cmhh

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorRef, Behavior}
import java.io.File

object Coordinator {
  import messages._
  import area._

  def apply(dbpath: String): Behavior[CoordinatorCommand] = Behaviors.setup { context => 
    val eventRecorder: ActorRef[EventRecorderCommand] = context.spawn(EventRecorder(dbpath, 100), "eventrecorder")

    val areaCoordinators: Map[Area, ActorRef[AreaCoordinatorCommand]] = 
      area.areas.map(x => {
        (x, context.spawn(AreaCoordinator(eventRecorder), s"areacoordinator@${x.toString}"))
      }).toMap

    Behaviors.receiveMessage { message => {
      message match {
        case ListAreaCoordinators =>
          areaCoordinators.foreach(x => println(x._2))
        case CountAreaCoordinators =>
          println(s"""[${context.self} - there are ${areaCoordinators.size} area coordinators.]""")
        case ListFieldCollectors =>
          areaCoordinators.foreach(x => x._2 ! ListFieldCollectors)
        case CountFieldCollectors =>
          areaCoordinators.foreach(x => x._2 ! CountFieldCollectors)
        case CountCases =>
          areaCoordinators.foreach(x => x._2 ! CountCases)
        case m: FieldCollector =>
          areaCoordinators(area.get(m.area)) ! m
        case m: Dwelling =>
          areaCoordinators(area.get(m.area)) ! m
        case CountResidents =>
          areaCoordinators.foreach(x => x._2 ! CountResidents)
        case m: RunDay =>
          areaCoordinators.foreach(x => x._2 ! m)
        case Flush =>
          eventRecorder ! Flush
      }
      Behaviors.same
    }}
  }
}