package part2Actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActorsExercise extends App {
  // Distributed word counting
  /*
  create WordCounterMaster
  send Initialize() to workCounterMaster
  send "Akka is awesome" to workCounterMaster
    wcm will send a WordCountTask("...") to one of its children
      child replies with a WordCountReply(3) to the master
    master replies with 3 to the sender

    request -> wcm -> wcm
          r <- wcm <-

    round robin logic
    1,2,3,4,5 workers and 7 tasks
    1,2,3,4,5,1,2
   */

  val system = ActorSystem("wordCountSystem")

  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(text: String)
    case class WordCountReply(text: String, count: Int)
  }

  class WordCounterMaster extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case Initialize(n) =>
        println(s"${self.path} - got n - $n")
        val workers: Array[ActorRef] = (1 to n).
          map(i => system.actorOf(Props[WordCounterWorker], s"${i}WordCounterWorker")).toArray
        context.become(initializeReceive(sender(), workers, 0))
    }

    def initializeReceive(client: ActorRef, workers: Array[ActorRef], taskNum: Int): Receive = {
      case WordCountTask(str) =>
        val workerNum = taskNum % workers.length
//        println(s"${self.path} - taskNum - $taskNum - workerNum - $workerNum - str - $str")
        workers(workerNum) ! WordCountTask(str)
        context.become(initializeReceive(client, workers, taskNum + 1))
      case WordCountReply(text, count) =>
//        println(s"${self.path} - WordCountReply - cnt - $count")
        client ! WordCountReply(text, count)
      case msg => s"${self.path} - ${msg.toString}"

    }
  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case WordCountTask(text) =>
//        println(s"${self.path} - taskNum - text - $text")
        sender() ! WordCountReply(text, text.split(" ").length)
      case msg => s"${self.path} - ${msg.toString}"
    }
  }

  object WordCounterClient {
    case object StartProcess
  }
  class WordCounterClient extends Actor {
    import WordCounterClient._
    import WordCounterMaster._

    override def receive: Receive = {
      case StartProcess =>
        val master = system.actorOf(Props[WordCounterMaster], "wordCounterMaster")
        master ! Initialize(5)
        master ! WordCountTask("Akka is awesome!")
        master ! WordCountTask("Learning it!")
        master ! WordCountTask("Learning it 2!")
        master ! WordCountTask("Learning it 3 3!")
        master ! WordCountTask("Learning it 4 4 4!")
        master ! WordCountTask("Learning it 5 5 5 5!")
        master ! WordCountTask("Learning it 6 6 6 6 6!")
      case WordCountReply(text, num) =>
        println(s"${self.path} - text - $text - len - $num")
    }
  }

  import WordCounterClient._

  val client = system.actorOf(Props[WordCounterClient], "aWordCounterClient")
  client ! StartProcess
}
