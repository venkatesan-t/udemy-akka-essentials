package part6patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import part6patterns.AskSpec.AuthManager.{AUTH_FAILURE_NOT_FOUND, AUTH_FAILURE_PASSWORD_INCORRECT, AuthFailure, AuthSuccess}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

// step 1 - import the ask (and pipe) pattern
import akka.pattern.ask
import akka.pattern.pipe

class AskSpec extends TestKit(ActorSystem("AskSpec"))
  with ImplicitSender with AnyWordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AskSpec._

  "An authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "An piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props) = {
    import AuthManager._

    "fail to authenticate a non-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("daniel", "rtjvm")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "rtjvm")
      authManager ! Authenticate("daniel", "iloveakka")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "successfully authenticate a resgistered user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "rtjvm")
      authManager ! Authenticate("daniel", "rtjvm")
      expectMsg(AuthSuccess)
    }
  }
}

object AskSpec {

  // this code is somewhere else in your app
  case class Read(key: String)
  case class Write(key: String, value: String)
  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at key $key")
        sender() ! kv.get(key) //Option[String]
      case Write(key, value) =>
        log.info(s"writing the value $value for the key $key")
        context.become(online(kv + (key -> value)))
    }
  }

  object AuthManager {
    final val AUTH_FAILURE_NOT_FOUND = "username not found"
    final val AUTH_FAILURE_PASSWORD_INCORRECT = "incorrect password"
    final val AUTH_FAILURE_SYSTEM = "system error"

    // user authenticator actor
    case class RegisterUser(username: String, password: String)
    case class Authenticate(username: String, password: String)
    case class AuthFailure(message: String)
    case object AuthSuccess
  }

  class AuthManager extends Actor with ActorLogging {

    import AuthManager._

    // step 2 - logistics
    implicit val timeout: Timeout = FiniteDuration(1, TimeUnit.SECONDS)
    implicit val executionContext: ExecutionContext = context.dispatcher

    protected val authDb = context.actorOf(Props[KVActor])

    override def receive: Receive = {
      case RegisterUser(username, password) => authDb ! Write(username, password)
      //        case Authenticate(username, password) =>
      //          authDb ! Read(username)
      //          context.become(waitingForPassword(username, sender()))
      case Authenticate(username, password) => handleAuthentication(username, password)

    }

    def handleAuthentication(username: String, password: String) = {
      val originalSender = sender()
      // step 3 - ask the actor
      val future = authDb ? Read(username) // runs on a thread
      // step 4 - handle the future for e.g. with onComplete
      future.onComplete { // issue 1 - this can run on another thread - akka encapsulation is broken
        // step 5 - NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE.
        // avoid closing over the actor instance (by using sender()) or mutable state
        //            case Success(None) => sender() ! AuthFailure("username not found") // issue 2 - sender() is authDb not one trying to get authenticated
        case Success(None) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND) // issue 2 - sender() is authDb not one trying to get authenticated
        case Success(Some(dbPassword)) =>
          if (dbPassword == password) originalSender ! AuthSuccess
          //              if (dbPassword == password) sender() ! AuthSuccess
          else originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        //              else sender() ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }

    //      def waitingForPassword(str: String, ref: ActorRef): Receive = {
    //        case password: Option[String] => // do password checks here
    //      }
  }

  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(username: String, password: String): Unit = {
      // step 3 - ask the actor
      val future = authDb ? Read(username) // Future[Any]
      // step 4 - process the future until you get the responses you will send back
      val passwordFuture = future.mapTo[Option[String]]  // Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbPassword) =>
          if (dbPassword == password) AuthSuccess
          else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      } // Future[Any] - will be completed with the response I will send back

      // step 5 - pipe the resulting future to the actor you want to send the result to
      /**
       * When the future completes, send the response to the actor ref in the arg list.
       */
      responseFuture.pipeTo(sender())
    }
  }
}
