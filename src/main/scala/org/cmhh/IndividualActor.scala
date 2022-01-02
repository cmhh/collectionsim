package org.cmhh

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
import java.time.LocalDate
import categories._

/**
 * Individual actor
 * 
 * Represents an individual.  Can respond to requests from a field collector.
 */
object IndividualActor {
  import implicits.conf

  /**
   * Actor behavior
   * 
   * @param first first name
   * @param last last name
   * @param dob date of birth
   * @param sex sex
   * @param parent reference of parent dwelling
   */
  def apply(
    first: String, last: String, dob: LocalDate, sex: Sex, 
    parent: ActorRef[messages.DwellingCommand]
  ): Behavior[messages.IndividualCommand] = Behaviors.setup { context => 
    new IndividualActor(first, last, dob, sex, parent, context).behavior(false)
  }
}

private class IndividualActor(
  first: String, last: String, dob: LocalDate, sex: Sex, 
  parent: ActorRef[messages.DwellingCommand], context: ActorContext[messages.IndividualCommand]
)(implicit val conf: Config) {
  import messages._

  override def toString = 
    s"""{"first":"$first", "last":"$last", "dob":"$dob", "sex":"$sex"}"""

  /**
   * Actor behavior
   * 
   * @param responded whether or not actor has responded 
   */
  private def behavior(responded: Boolean): Behavior[IndividualCommand] = Behaviors.receiveMessage { message => {
    message match {
      case m: AttemptInterview =>
        val r = scala.util.Random.nextDouble()
        val p = Vector(
          conf.getDouble("collection-settings.individual.probs.refusal"),
          conf.getDouble("collection-settings.individual.probs.noncontact"),
          conf.getDouble("collection-settings.individual.probs.response")
        ).scanLeft(0.0)(_ + _).drop(1)
        if (r < p(0)) {
          m.replyTo ! IndividualRefusal(context.self, parent)
          behavior(true)
        } 
        else if (r < p(1)) {
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