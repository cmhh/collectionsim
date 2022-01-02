package org.cmhh

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
import java.io.File
import java.time.LocalDate
/**
 * Dwelling actor.
 * 
 * Responsible for spawing individuals.
 */
object DwellingActor {
  import messages._
  import implicits.{random, conf}

  /**
   * Actor behavior
   * 
   * @param id unique dwelling ID
   * @param address address string
   * @param location Coordinates
   * @param eventRecorder reference for system event recorder, used for persistent logging.
   */
  def apply(
    id: Int, address: String, location: Coordinates, eventRecorder: ActorRef[EventRecorderCommand]
  ): Behavior[DwellingCommand] = Behaviors.setup{context =>
    new DwellingActor(id, address, location, eventRecorder, context).behavior(false)
  }
}

private class DwellingActor(
  id: Int, address: String, location: Coordinates,
  eventRecorder: ActorRef[messages.EventRecorderCommand],
  context: ActorContext[messages.DwellingCommand]
)(implicit val random: Rand, val conf: Config) {
  import messages._
  import categories._

  /**
   * Placeholder for household-level questionnaire.
   */
  override def toString = random.string(50)

  private val residents: List[ActorRef[IndividualCommand]] = 
      if (scala.util.Random.nextDouble() < conf.getDouble("collection-settings.household.proportion-empty")) 
        List.empty 
      else spawnResidents(context)

  /**
   * Actor behavior
   * 
   * @param respondent whether the actor has successfully responded or not.
   */
  private def behavior(responded: Boolean): Behavior[DwellingCommand] = Behaviors.receiveMessage { message =>
    message match {
      case m: AttemptInterview =>
        val r = scala.util.Random.nextDouble()
        val p = Vector(
          conf.getDouble("collection-settings.household.probs.refusal"),
          conf.getDouble("collection-settings.household.probs.noncontact"),
          conf.getDouble("collection-settings.household.probs.response")
        ).scanLeft(0.0)(_ + _).drop(1)
        if (residents.size == 0) {
          // assume we can identify empty dwellings with certainty.
          // not reasonable in practice, but...
          m.replyTo ! DwellingEmpty(context.self)
          Behaviors.same
        }
        else if (r < p(0)) {
          m.replyTo ! DwellingRefusal(context.self)
          Behaviors.same
        }
        else if (r < p(1)) {
          m.replyTo ! DwellingNoncontact(context.self)
          Behaviors.same
        } 
        else {
          m.replyTo ! DwellingResponse(context.self, toString, residents)
          behavior(true)
        }
    }
  }

  private def spawn(
    first: String, last: String, dob: LocalDate, sex: Sex, 
    context: ActorContext[DwellingCommand]
  ): ActorRef[IndividualCommand] = 
    context.spawn(
      IndividualActor(first, last, dob, sex, context.self), 
      s"""$last@$first@${random.string(5)}"""
    )

  private def spawnFamily(context: ActorContext[DwellingCommand]): List[ActorRef[IndividualCommand]] = {
    random.famtype match {
      case CoupleOnly =>
        val (last, first1, first2, dob1, dob2, sex1, sex2) = random.couple
        List(
          spawn(first1, last, dob1, sex1, context), 
          spawn(first2, last, dob2, sex2, context)
        )
      case CoupleOnlyAndOthers =>
        val (last, first1, first2, dob1, dob2, sex1, sex2) = random.couple
        val n = random.int(2)
        List(
          spawn(first1, last, dob1, sex1, context), 
          spawn(first2, last, dob2, sex2, context)
        ) ++ 
        (1 to n).map(i => random.adult(context)).toList
      case CoupleWithChildren =>
        val (last, first1, first2, dob1, dob2, sex1, sex2) = random.couple
        val n = random.int(4)
        List(
          spawn(first1, last, dob1, sex1, context), 
          spawn(first2, last, dob2, sex2, context)
        ) ++ 
        (1 to n).map(i => random.child(context, last = Some(last))).toList
      case CoupleWithChildrenAndOthers =>
        val (last, first1, first2, dob1, dob2, sex1, sex2) = random.couple
        val nchild = random.int(4)
        val nadult = random.int(2)
        List(
          spawn(first1, last, dob1, sex1, context), 
          spawn(first2, last, dob2, sex2, context)
        ) ++ 
        (1 to nchild).map(i => random.child(context, last = Some(last))).toList ++
        (1 to nadult).map(i => random.adult(context)).toList
      case OneParentWithChildren =>
        val last = random.nameLast
        val n = random.int(2)
        random.adult(context, last = Some(last)) :: 
          (1 to n).map(i => random.child(context, last = Some(last))).toList
      case OneParentWithChildrenAndOthers =>        
        val last = random.nameLast
        val nchild = random.int(4)
        val nadult = random.int(2)
        random.adult(context, last = Some(last)) :: 
          (1 to nchild).map(i => random.child(context, last = Some(last))).toList ++
          (1 to nadult).map(i => random.adult(context)).toList 
    }
  }

  private def spawnResidents(context: ActorContext[DwellingCommand]): List[ActorRef[IndividualCommand]] = {
    random.hhtype match {
      case OnePersonHH =>
        List(random.adult(context))
      case OneFamilyHH =>
        spawnFamily(context)
      case TwoFamilyHH =>
        spawnFamily(context) ++ spawnFamily(context)
      case OtherMultHH =>
        val n = random.int(4)
        (1 to n).map(i => random.adult(context)).toList
    }
  }
}