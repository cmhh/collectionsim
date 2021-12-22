package org.cmhh

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import java.time.LocalDate
import sex._

object IndividualActor {
  def apply(
    first: String, last: String, dob: LocalDate, sex: Sex, 
    parent: ActorRef[messages.DwellingCommand]
  ): Behavior[messages.IndividualCommand] = Behaviors.setup { context => 
    new IndividualActor(first, last, dob, sex, parent, context).behavior(false)
  }
}

class IndividualActor(
  first: String, last: String, dob: LocalDate, sex: Sex, 
  parent: ActorRef[messages.DwellingCommand], context: ActorContext[messages.IndividualCommand]
) {
  import messages._

  override def toString = 
    s"""{"first":"$first", "last":"$last", "dob":"$dob", "sex":"$sex"}"""

  private def behavior(responded: Boolean): Behavior[IndividualCommand] = Behaviors.receiveMessage { message => {
    message match {
      case m: AttemptInterview =>
        val r = scala.util.Random.nextDouble()
        if (r < 0.1) {
          m.replyTo ! IndividualRefusal(context.self, parent)
          behavior(true)
        } 
        else if (r < 0.3) {
          m.replyTo ! IndividualNoncontact(context.self, parent)
          behavior(false)
        } else {
          m.replyTo ! IndividualResponse(context.self, parent, toString)
          behavior(true)
        }
      case _ => ;
    }
    Behaviors.same
  }}
}