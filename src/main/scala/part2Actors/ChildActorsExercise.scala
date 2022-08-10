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
    case class WordCountTask(taskId: Int, text: String)
    case class WordCountReply(taskId: Int, count: Int)
  }

  class WordCounterMaster extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case Initialize(n) =>
        println(s"${self.path} - got n - $n")
        val workers: Array[ActorRef] = (1 to n).
          map(i => context.actorOf(Props[WordCounterWorker], s"wordCounterWorker_$i")).toArray
        context.become(withChildren(Map(), workers, 0))
    }

    def withChildren(clientTaskMap: Map[Int, ActorRef], workers: Array[ActorRef], taskNum: Int): Receive = {
      case str: String =>
        val workerNum = taskNum % workers.length
//        println(s"${self.path} - taskNum - $taskNum - workerNum - $workerNum - str - $str")
        workers(workerNum) ! WordCountTask(taskNum, str)
        val newClientTaskMap = clientTaskMap + (taskNum -> sender())
        context.become(withChildren(newClientTaskMap, workers, taskNum + 1))
      case WordCountReply(taskId, count) =>
//        println(s"${self.path} - WordCountReply - cnt - $count")
        client ! WordCountReply(taskId, count)
        context.become(withChildren(clientTaskMap - taskNum, workers, taskNum))
      case msg => s"${self.path} - ${msg.toString}"

    }
  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._

    override def receive: Receive = {
      case WordCountTask(id, text) =>
//        println(s"${self.path} - taskNum - text - $text")
        sender() ! WordCountReply(id, text.split(" ").length)
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
        master ! Initialize(3)
        master ! "Akka is awesome!"
        master ! "Learning it!"
        master ! "Learning it 2!"
        master ! "Learning it 3 3!"
        master ! "Learning it 4 4 4!"
        master ! "Learning it 5 5 5 5!"
        master ! "Learning it 6 6 6 6 6!"
      case WordCountReply(taskId, num) =>
        println(s"${self.path} - taskId - $taskId - len - $num")
    }
  }

  import WordCounterClient._

  val client = system.actorOf(Props[WordCounterClient], "aWordCounterClient")
  client ! StartProcess
}
