package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

object AkkaRecap extends App {

  class ChildActor extends Actor {
    def receive: Receive = {
      case "to child" =>
        println("From child")
        sender ! " - child responded"
    }
  }
  class SimpleActor extends Actor with Stash with ActorLogging {
    override def receive: Receive = {
      case "create child" =>
        val child = context.actorOf(Props[ChildActor], "ChildActor")
        child ! "to child"
      case "stash" => stash()
      case "change handler now" =>
        unstashAll()
        context.become(anotherHandler) // In hope messages we stashed are appropriate for this msg handler.

      case "change" => context.become(anotherHandler)
      case msg => println(s"Received $msg")
    }
    def anotherHandler: Receive = {
      case _ =>
        println(self.path.name)
        context.unbecome()
    }

    override def preStart(): Unit = {
      log.info("I am starting...")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart // child throws RuntimeException, we Restart the child.
      case _ => Stop // child throws an exception apart from RuntimeException, we Stop the child.
    }

  }

  // Actor encapsulation
  val system: ActorSystem = ActorSystem("AkkaRecap")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
  simpleActor ! "HEY"
  simpleActor ! "change"
  simpleActor ! "show Actor.path.name"
  simpleActor ! "x"

  // stashing

  //actor spawn another actor
  simpleActor ! "create child"

  // Guardians
  // /system, /user (parent of actor we create), / (root guardian, parent of every actor in ActorSystem)


  // Actor lifecycle
  /* Actors can be
  * Started, Resumed, Stopped, Resumed, Restarted
  *
  * PreStart -- starting actor
  *
  * stopping Actor:-
  *   1. context.stop
  *   2. special message (PoisonPill, Kill) are handled in separate mailbox
  */
  simpleActor ! PoisonPill

  // logging with ActorLogging

  // supervision
  /*How parentActor respond to childActor Failure (supervisorStrategy method is present in each actor)*/

  // configure akka infraStructure: dispatchers, routers, mailboxes....

  //schedulers
  import system.dispatcher
  system.scheduler.scheduleOnce(2.seconds){
    simpleActor ! "delayed message"
  }


  // akka patterns   FiniteStateMachine + ask pattern
  implicit val timeout: Timeout = akka.util.Timeout(3.seconds)
  val askPatternFuture = simpleActor ? "message"
  // this is ask pattern and will return a future, askPatternFuture will contain the response from simpleActor
  //  as a reaction to this "message"
  // this future will be completed with the first reply from this actor(simpleActor) other reply's are discarded
  // if the actor does not reply within implicit Timeout(3.seconds) 3 seconds, future will fail with timeout


  // Pipe patter
  // Common use is to use ask pattern in conjunction with the pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")

  // We can send/pipe the result of the Future (askPatternFuture) to this anotherActor.
  askPatternFuture.mapTo[String].pipeTo(anotherActor)

  /** this means when future completes, Its value is being sent to anotherActor as a message*/
}
