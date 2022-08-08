package part2Actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2Actors.ChangingActorBehavior.Mom.MomStart
import part2Actors.ChangingActorBehavior.VoteStatusRequest

object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    final val HAPPY = "happy"
    final val SAD = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    // internal state of the kid
    var state: String = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceiveNew

//    def happyReceive: Receive = {
//      case Food(VEGETABLE) => context.become(sadReceive) // change my receive handler to sadReceive
//      case Food(CHOCOLATE) =>
//      case Ask(_) => sender() ! KidAccept
//    }
//    def sadReceive: Receive = {
//      case Food(VEGETABLE) => // stay sad
//      case Food(CHOCOLATE) => context.become(happyReceive) // change my receive handler to happyReceive
//      case Ask(_) => sender() ! KidReject
//    }

    def happyReceiveNew: Receive = {
      case Food(VEGETABLE) => context.become(sadReceiveNew, discardOld = false) // change my receive handler to sadReceive
      case Food(CHOCOLATE) =>
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceiveNew: Receive = {
      case Food(VEGETABLE) => context.become(sadReceiveNew, discardOld = false) // stay sad
      case Food(CHOCOLATE) => context.unbecome() // change my receive handler to happyReceive
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do you want to play?
    final val VEGETABLE = "veggies"
    final val CHOCOLATE = "chocolate"
  }
  class Mom extends Actor {
    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println("Yay, my kid is happy!")
      case KidReject => println("My kid is sad, but atleast he's healthy!")
    }
  }

  val system = ActorSystem("changingActorBehaviorDemo")
  val fussyKid = system.actorOf(Props[FussyKid], "fussyKid")
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid], "statelessFussyKid")
  val mom = system.actorOf(Props[Mom])

//  mom ! MomStart(fussyKid)
  mom ! MomStart(statelessFussyKid)

  /*
  mom receives MomStart
    kid receives Food(VEGETABLE) -> kid will change the handler to sadReceive
    kid receives Ask(play?) -> kid replies with the sadReceive handler =>
  mom receives KidReject
   */

  /*
  Food(VEGETABLE) -> message handler turns to sadReceive
  Food(CHOCOLATE) -> become happyReceive
   */

  /*
    Food(VEGETABLE) -> stack.push(sadReceiveNew)
    Food(CHOCOLATE) -> stack.push(happyReceiveNew)

    Stack:
    1. happyReceiveNew
    2. sadReceiveNew
    3. happyReceiveNew
     */

  /*
    new behavior
    Food(VEGETABLE) -> push sadReceiveNew - using context.become(sadReceiveNew, false)
    Food(VEGETABLE) -> push sadReceiveNew - using context.become(sadReceiveNew, false)
    Food(CHOCOLATE) -> pop sadReceiveNew - using context.unbecome()
    Food(CHOCOLATE) -> pop sadReceiveNew - using context.unbecome()

    Stack:
    1. sadReceiveNew
    2. sadReceiveNew
    3. happyReceiveNew

   */

  /*
  Exercise 1 - recreate the Counter Actor with context.become and NO MUTABLE STATE
   */

  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  case class CounterActor() extends Actor {

    import Counter._

    override def receive: Receive = countReceive(0)

    def countReceive(acc: Int): Receive = {
      case Increment =>
        println(s"[$acc] incrementing")
        context.become(countReceive(acc + 1))
      case Decrement =>
        println(s"[$acc] decrementing")
        context.become(countReceive(acc - 1))
      case Print => println(s"[counter] my current count is $acc")
    }
  }

  import Counter._
  val aCounterActorRef = system.actorOf(Props[CounterActor], "aCounterActorRef")
  (1 to 5).foreach(_ => aCounterActorRef ! Increment)
  (1 to 3).foreach(_ => aCounterActorRef ! Decrement)
  aCounterActorRef ! Print

  /*
  Exercise 2 - simplified voting system
   */
  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = voteReceive(None)
    def voteReceive(cand: Option[String]): Receive = {
      case Vote(c) => context.become(voteReceive(Some(c)))
      case VoteStatusRequest => sender() ! VoteStatusReply(cand)
      case msg => "I don't understand"
    }
  }
  case class AggregateVotes(citizens: Set[ActorRef])
  class VoteAggregator extends Actor {
    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(czs) =>
        czs.foreach(c => c ! VoteStatusRequest)
        context.become(awaitingStatuses(czs, Map()))
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], acc: Map[String, Int]): Receive = {
      case VoteStatusReply(None) => sender() ! VoteStatusRequest
      case VoteStatusReply(Some(cand)) =>
        val newStillWaiting = stillWaiting - sender()
        val newAcc = acc + (cand -> (acc.getOrElse(cand, 0) + 1))
        if (newStillWaiting.isEmpty)
          println(newAcc)
        else context.become(awaitingStatuses(newStillWaiting, newAcc))
      case msg => "I don't understand"
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator], "voteAggregator")
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))
}
