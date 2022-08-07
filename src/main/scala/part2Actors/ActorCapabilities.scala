package part2Actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2Actors.ActorCapabilities.Counter.Print
import part2Actors.ActorCapabilities.Person.LiveTheLife

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => {
        println(s"[$self] I have received Hi!")
        context.sender() ! "Hello, there!" // replying to a message
      }
      case message: String => println(s"[$self] I have received $message")
      case number: Int => println(s"[simple actor] I have received a NUMBER: $number")
      case SpecialMessage(content) => println(s"[simple actor] I have received something SPECIAL: $content")
      case SendMessageToYourself(content) => self ! content
      case SayHiTo(ref) => ref ! "Hi!"
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // i keep the original sender of the WPM
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE
  // in practice use case classes and case objects

  simpleActor ! 42

  case class SpecialMessage(content: String)
  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about thier context and about themselves
  // context.self === 'this' in OOP

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am an actor and I am proud of it")

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)

  alice ! SayHiTo(bob)

  // 4 - dead letters
  alice ! "Hi!" // reply to "me"

  // 5 - forwarding messages
  // Daniel -> Alice -> Bob
  // forwarding = sending a message with the ORIGINAL sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob)

  /**
   * Exercise 1
   *
   *  a Counter actor
   *    - Increment
   *    - Decrement
   *    - Print
   */

  val counterSystem = ActorSystem("counterSystem")
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }
  case class CounterActor() extends Actor {
    import Counter._
    var count: Int = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[counter actor] - current count is $count")
      case _ => "I don't understand"
    }
  }
  val aCounterActorRef = counterSystem.actorOf(Props[CounterActor], "aCounterActorRef")
  (1 to 5).foreach(_ => aCounterActorRef ! Counter.Increment)
  (1 to 3).foreach(_ => aCounterActorRef ! Counter.Decrement)
  aCounterActorRef ! Print

  /**
   * Exercise 2
   *
   * a Bank account as an actor
   *  receives
   *    - Deposit an amount
   *    - Withdraw an amount
   *    - Statement
   *  replies with
   *    - Success
   *    - Failure
   *
   * interact with some other kind of actor
   *
   */

  val bankAccountSystem = ActorSystem("bankAccountSystem")
  case class Deposit(amt: Double)
  case class Withdraw(amt: Double)
  case object Statement
  case class TransactionSuccess(message: String)
  case class TransactionFailure(reason: String)
  case class StatementResponse(bal: Double)

  object BankAccountActor {
    def props(bal: Double) = Props(BankAccountActor(bal))
  }
  case class BankAccountActor(private var bal: Double) extends Actor {
    override def receive: Receive = {
      case Deposit(amt) =>
        if (amt < 0)
          sender()! TransactionFailure(s"invalid deposit amount - $amt")
        else {
          bal += amt
          sender() ! TransactionSuccess(s"successfully deposited $amt")
        }
      case Withdraw(amt) =>
        if (amt < 0)
          sender() ! TransactionFailure(s"invalid withdrawal amount - $amt")
        else if (amt > bal)
          sender() ! TransactionFailure(s"insufficient funds for withdrawal - $amt")
        else {
          bal -= amt
          sender() ! TransactionSuccess(s"successfully withdrew $amt")
        }
      case Statement => sender() ! StatementResponse(bal)
    }
  }
  val aBankActorRef = bankAccountSystem.actorOf(BankAccountActor.props(10000), "aBankActorRef")
//  aBankActorRef ! Deposit(1000)
//  aBankActorRef ! Statement

  val anotherBankActorRef = bankAccountSystem.actorOf(BankAccountActor.props(20000), "anotherBankActorRef")
//  anotherBankActorRef ! Withdraw(1500)
//  anotherBankActorRef ! Statement

//  val bankAccountClientSystem = ActorSystem("bankAccountClientSystem")

  object BankAccountClientActor {
    def props(ref: ActorRef) = Props(BankAccountClientActor(ref))
  }

  case class BankAccountClientActor(ref: ActorRef) extends Actor {
    override def receive: Receive = {
      case TransactionSuccess(msg) => println(s"$msg from $sender() ")
      case TransactionFailure(rsn) => println(s"$rsn from $sender() ")
      case StatementResponse(bal: Double) => println(s"[bank account client actor] - balance - $bal")
      case Deposit(amt) => ref ! Deposit(amt)
      case Withdraw(amt) => ref ! Withdraw(amt)
      case Statement => ref ! Statement
      case msg => s"[bank account client actor] - don't understand the message - ${msg.toString}"
    }
  }

  val aBankClient = bankAccountSystem.actorOf(BankAccountClientActor.props(aBankActorRef), "aBankClient")
  aBankClient ! Statement
  aBankClient ! Deposit(-100d)
  aBankClient ! Deposit(1000d)
  aBankClient ! Deposit(1000d)
  aBankClient ! Deposit(1000d)
  aBankClient ! Withdraw(500d)
  aBankClient ! Withdraw(-500d)
  aBankClient ! Withdraw(50000d)
  aBankClient ! Statement

  /**
   * FROM SOLUTION
   */

  object Person {
    case class LiveTheLife(account: ActorRef)
  }

  class Person extends Actor {
    import Person._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(900000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message.toString)
    }
  }

  val account = bankAccountSystem.actorOf(BankAccountActor.props(10000), "lastBankAccountActor")
  val person = bankAccountSystem.actorOf(Props[Person], "billionaire")
  person ! LiveTheLife(account)
}
