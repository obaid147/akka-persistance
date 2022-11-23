package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActors extends App {

  /* Scenario:-
  * Let's say we have an accountant that keeps track of our invoices for our business
  * and sums up the total income*/

  // Commands
  case class Invoice(recipient: String, date: Date, amount: Int) // command for accountant

  // Events:- persistent actor will send to journal (persistent store)
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int) // EVENT

  class Accountant extends PersistentActor with ActorLogging {
    var latestInvoiceId = 0
    var totalAmount = 0
    /**persistenceId:-
    * Is how the event persisted by this Actor will be identified.
    * In persistence store, we have lots of events. So, we have to know which actor wrote which event*/
    override def persistenceId: String = "simple-accountant" // make it unique.

    /**receiveCommand is the normal Receive handler, Which will be called when we send message(command) to this actor*/
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
          /** When you receive a command...
            * 1. Create an event to persist into the store/ send to journal.
            * 2. Persist the event, then pass in the callback which will get triggered once event is writtern
            * 3. We update actor state when event has persisted.
            * */
        log.info(s"Received invoice for an amount:- $amount")
        //val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount) // #1
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount)/*combined 1&2*/)
        /*timeGap-> all other messages are stashed*/{ e => // #2
          // #3 update state/log messages/ send message to other actors
          latestInvoiceId += 1
          totalAmount += amount

          // correctly identify the sender of the command
          sender() ! "PersistenceACK"

          log.info(s"Persisted $e as invoice number #${e.id}, for total amount $totalAmount")
        }

        // act like a normal actor.
      case "print" =>
        log.info(s"latest invoice id: $latestInvoiceId, total amount: $totalAmount")

    }

    /**
      * Handle that will be called on recovery
      * */
    override def receiveRecover: Receive = {
      /** receiveRecover will be called when the actor starts or
        * is restarted as a result of supervisor strategy
        *
        * best practice: follow the logic in the persist steps of receiveCommand
        * */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId += id
        totalAmount += amount
        log.info(s"Recovered invoice #$id, for amount $amount, total amount $totalAmount")
    }
    /*before running this application, we need to have a persistent store or a journal configured for storing events.
    * application.conf*/

  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  //for (i <- 1 to 10) accountant ! Invoice("The Sofa company", new Date, i * 1000)
  // we get all messages and now, we will comment the above line of code and log in receiveRecover InvoiceRecorded

  /** HOW DOES IT ALL WORK....
    * From the first run where for (i <- 1 to 10) accountant ! Invoice("The Sofa company", new Date, i * 1000)
    * was not commented, where we send command to our actor,
    * Accountant actor wrote bunch of objects in journal(persist() calls). But these persist calls and
    * persisted events [persist(InvoiceRecorded(] were tagged with this actors "simple-accountant" persistentID.
    *
    * OnStart ie:- On the next run, akka queried the journal for all events tagged with "simple-accountant" persistentID
    * on behalf of the actor that is just starting. Because journal had 10 events for this persistenceID.
    * The Accountant actor calls the receiveRecover handler for every single-one of those events, replaying all
    * the events and recreate its state. */

  /** More things to discuss....
    * Persisting is asynchronous. Non blocking call that will be executed at some point in future
    * and the Handler will also be executed at some point in future after the event has been successfully
    * persisted into the journal.
    *
    * In AKKA we have learnt that we should never access mutable states or call methods in asynchronous callBack.
    * because this can break the actor encapsulation(Race Condition) BUT BUT BUT BUT.....
    *
    * In Akka-Persistence, It is ok as we have no "Race Condition" here.
    * SAFE TO ACCESS MUTABLE STATES
    *
    * sender() ! "..."  will identify the original sender precisely.
    *
    *
    * TIME GAP Between Persisting and Handling the event...
    * TimeGap between original call to persist persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount) and
    * the callback { e =>
    *  latestInvoiceId += 1
    *  totalAmount += amount
    *  sender() ! "PersistenceACK"
    *  log.info(s"Persisted $e as invoice number #${e.id}, for total amount $totalAmount")
    *  }
    * ********* all other messages are stashed *****************
    *
    * What if other messages/commands are handled in the mean time between Persisting and the callBack????
    *
    * With Persist, AKKA gurantees that all the messages between Persisting
    * and the Handling of persisted events are stashed.
    *
    *
    * */

  // ACT Like a normal actor... no need to persist events here / we are not forced to persist events...
  accountant ! "print"

}
