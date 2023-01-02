package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

/**PersistAsync is a special version of persist that relaxes some delivery and ordering gurantees.
  * Particularly designed for  high throughput use cases where we need to process commands at a
  * high rate in your persistent actors.*/

// COMMAND
case class Command(contents: String)

//EVENT
case class Event(contents: String)

object PersistAsyncDemo extends App {

    object CriticalStreamProcessor {
        def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor(eventAggregator))
    }
    
    class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
        /*Will persist Events and forward events to another actor(EventAggregator).*/
        override def persistenceId: String = "critical-stream-processor"

        override def receiveCommand: Receive = {
            case Command(contents) =>
                eventAggregator ! s"Processing $contents"
                persistAsync(Event(contents)) /*              TIME GAP                       */ { event =>
                  eventAggregator ! event
                }
            //Some computation!!!
            val processedContents = contents + "_processed"
            persistAsync(Event(processedContents)) /*              TIME GAP                       */  { event =>
              eventAggregator ! event
            }
        }

        override def receiveRecover: Receive = {
            case message =>
                log.info(s"Recovered $message")
        }
    }

    class EventAggregator extends Actor with ActorLogging {
        override def receive: Receive = {
            case message => log.info(s"$message")
        }
    }

    import CriticalStreamProcessor._
    val system = ActorSystem("criticalStreamProcessor")
    val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
    val criticalStreamProcessor = system.actorOf(props(eventAggregator), "streamProcessor")

    criticalStreamProcessor ! Command("command1")
    /*Every single thing will be processed for command1 then for command2
    * Processing command1
    * Event(command1)
    * Event(command1_processed)
    *
    * Event(command2)
    * Processing command2
    * Event(command1_processed)*/
    criticalStreamProcessor ! Command("command2")

    /*Now, if we make a small change in the code and we will see different output
    *
    * - change persist calls to persistAsync calls and delete the journal to re-run
    *
    * Processing command1
    * Processing command2
    *
    * Event(command1)
    * Event(command1_processed)

    * Event(command2)
    * Event(command2_processed)
    *
    * Persisting is an asynchronous operation and sends event to journal, When journal persist the event, call back will fire up.
    * Difference between persist and persistAsync is TIME GAP,
    * In case of persist, Every single message is stashed.
    * In case of persistAsync, messages are not stashed.
    * TIME GAP is comparatively large*/

    /** The time actor spends on executing code is small compare to
      * the persistAsync Calls and call back handlers
      * both persist and persistAsync are asynchronous methods.
      * Async in this case means that you can also receive normal commands. In the meantime, that is in between
      *     these time gaps, so that the persisting combo call plus callback handler is not atomic with respect to
      *     incoming messages anymore.*/


    // -------------------------QUESTION?
    //    when would it be best to use, persist or persistAsync?
    /** persistAsync has the upper hand on persist on performance because in persist, you will have to wait until
      * the entire command has been processed before processing any other command.
      * During these time gaps, any incoming commands will be stashed, which means that every single bit of
      * this code, including the time gaps, have to pass before a new command can arise and be processed by
      * the persistent doctor.
      *
      * - This is particularly useful in high throughput environments.
      * - Bad when we need ORDERING OF EVENTS.
      *
      * PERSIST vs PERSIST-ASYNC
      * - Ordering with persist.
      * - useful in high throughput env with persistAsync.
      * */
}
