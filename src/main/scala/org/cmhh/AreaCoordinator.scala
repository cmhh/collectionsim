package org.cmhh

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import akka.pattern.ask
import java.io.File
import scala.util.{Try, Success, Failure}

object AreaCoordinator {
  import messages._
  implicit val timeout: Timeout = 3.seconds

  def apply(eventRecorder: ActorRef[EventRecorderCommand]): Behavior[AreaCoordinatorCommand] = Behaviors.setup{context => 

    def behavior(
      collectors: Map[ActorRef[FieldCollectorCommand], FieldCollector], 
      workloads: Map[ActorRef[FieldCollectorCommand], Int]
    ): Behavior[AreaCoordinatorCommand] = Behaviors.receive{(context, message) => {
      message match {
        case ListFieldCollectors => 
          collectors.foreach(collector => println(collector))
          Behaviors.same
        case CountFieldCollectors => 
          println(s"""[${context.self}] - I have ${collectors.size} field collectors.""")
          Behaviors.same
        case CountCases =>
          collectors.keys.foreach(collector => collector ! CountCases)
          Behaviors.same
        case m: FieldCollector => 
          val collector = context.spawn(FieldCollectorActor(m.id, m.address, m.location, eventRecorder), s"collector@${m.id}")
          eventRecorder ! CollectorRecord(collector.toString, m.id, m.address, m.location, m.area)
          behavior(collectors + (collector -> m), workloads + (collector -> 0))
        case m: Dwelling => 
          val available1 = collectors.filter(collector => workloads(collector._1) < 50)
          val available2 = if (available1.size > 0) available1 else collectors
          val distances = available2.map(collector => {
            val d = Router.route(m.location, collector._2.location, 20, 20) match {
              case Success(x) => x._1
              case Failure(e) => Router.route0(m.location, collector._2.location)._1
            }
            (collector._1, d)
          }).toMap
          val min = distances.map(x => x._2).min
          val closest = distances.keys.filter(collector => distances(collector) == min).head
          closest ! m
          behavior(collectors, workloads + (closest -> (workloads(closest) + 1)))
        case CountResidents =>
          collectors.keys.foreach(collector => collector ! CountResidents)
          Behaviors.same
        case m: Distance =>
          Behaviors.unhandled
        case m: Workload =>
          Behaviors.unhandled
        case m: RunDay =>
          collectors.keys.foreach(collector => collector ! m)
          Behaviors.same
      }
    }}

    behavior(Map.empty, Map.empty)
  }
}