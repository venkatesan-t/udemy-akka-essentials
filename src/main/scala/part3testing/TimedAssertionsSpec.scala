package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Random
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TimedAssertionsSpec extends TestKit(
  ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssertionsConfig")))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TimedAssertionsSpec._

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply with the meaning of life in a timely manner" in {
      within(FiniteDuration(500, TimeUnit.MILLISECONDS), FiniteDuration(1, TimeUnit.SECONDS)) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at a reasonable cadence" in {
      within(FiniteDuration(1, TimeUnit.SECONDS)) {
        workerActor ! "workSequence"

        val results: Seq[Int] = receiveWhile[Int](max = FiniteDuration(2, TimeUnit.SECONDS),
          idle = FiniteDuration(500, TimeUnit.MILLISECONDS), messages = 10) {
          case WorkResult(result) => result
        }

        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {
      val probe = TestProbe()
      probe.send(workerActor, "work")
      probe.expectMsg(WorkResult(42))
      // TestProbes don't listen to within blocks
      // test probe has its own timeout, configured using akka.test.single-expect-default
    }
  }

}

object TimedAssertionsSpec {

  case class WorkResult(result: Int)

  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        // long computation
        Thread.sleep(500)
        sender() ! WorkResult(42)
      case "workSequence" =>
        val r = new Random()
        for (i <- 1 to 10) {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }
}
