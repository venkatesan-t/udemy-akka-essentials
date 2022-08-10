package part2Actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2Actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}

object ChildActors extends App {

  // Actors can create other actors

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }
  class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child with name $name")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChildReceive(childRef))
    }

    def withChildReceive(child: ActorRef): Receive = {
      case TellChild(message) =>
        if (child != null) child forward message
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  import Parent._

  val system = ActorSystem("parentChildSystem")
  val parent = system.actorOf(Props[Parent], "aParentActor")
  parent ! CreateChild("John")
  parent ! TellChild("Do you want to play?")

  // actor hierarchies
  // parent -> child  -> grandchild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian/supervisor
    - /user = user-level guardian/supervisor
    - / = the root guardian/supervisor
   */

  /**
   * Actor selection
   */
  val childSelection = system.actorSelection("/user/aParentActor/John") // works with context.actorSelection as well
  childSelection ! "I found you!"

  /**
   * Danger! - Actor encapsulation dangers...
   *
   * NEVER PASS MUTABLE ACTOR STATE, OR THE 'THIS' REFERENCE, TO CHILD ACTORS.
   *
   * NEVER IN YOUR LIFE.
   */

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0
    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "creditCard")
        creditCardRef ! AttachToAccount(this) // !!
      case Deposit(funds: Int) => deposit(funds)
      case Withdraw(funds: Int) => withdraw(funds)
    }

    def deposit(funds: Int): Unit = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds }
    def withdraw(funds: Int): Unit = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) //!!
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import NaiveBankAccount._

    override def receive: Receive = {
      case AttachToAccount(acc) => context.become(attachTo(acc))
    }

    def attachTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your message has been processed")
        // benign
        account.withdraw(1) // because I can
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "naiveBankAccount")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/naiveBankAccount/creditCard")
  ccSelection ! CheckStatus
}
