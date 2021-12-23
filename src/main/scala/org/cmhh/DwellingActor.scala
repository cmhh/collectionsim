package org.cmhh

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import java.io.File
import sex._
import demographics._
import java.time.LocalDate
/**
 * Dwelling actor.
 * 
 * Responsible for spawing individuals.
 */
object DwellingActor {
  import messages._

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
) {
  import messages._

  /**
   * Placeholder for household-level questionnaire.
   */
  override def toString = random.string(50)

  private val residents: List[ActorRef[IndividualCommand]] = 
      if (scala.util.Random.nextDouble() < 0.1) List.empty else spawnResidents(context)

  /**
   * Actor behavior
   * 
   * @param respondent whether the actor has successfully responded or not.
   */
  private def behavior(responded: Boolean): Behavior[DwellingCommand] = Behaviors.receiveMessage { message =>
    message match {
      // respond to request for interview
      case m: AttemptInterview =>
        val r = scala.util.Random.nextDouble()
        if (residents.size == 0) {
          // assume we can identify empty dwellings with certainty.
          // not reasonable in practice, but...
          m.replyTo ! DwellingEmpty(context.self)
          Behaviors.same
        }
        else if (r < 0.1) {
          // refuse with prob 0.1
          m.replyTo ! DwellingRefusal(context.self)
          Behaviors.same
        }
        else if (r < 0.3) {
          // non-contact with prob 0.2
          m.replyTo ! DwellingNoncontact(context.self)
          Behaviors.same
        } 
        else {
          // respond with prob 0.7
          // send 'completed questionnaire', and pointers to each individual to area coordinator
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