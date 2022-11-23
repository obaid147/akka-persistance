package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActors_Extras extends App {

  /** Persisting Extras
    * Delete the journal under target/rtjvm in between the re-runs of application. Don't want accumulating journal.
    *
    * ------------------------------ Persistence Failures:- -----------------------------
    *
    * #1 Failures to persist in that call to persist() throws an error.
    * #2 Journal implementation actually fails to persist a message.
    *
    * */

  // Commands
  case class Invoice(recipient: String, date: Date, amount: Int) // command for accountant
  case class InvoiceBulk(Invoices: List[Invoice])

  // special message
  object Shutdown

  // Events:- persistent actor will send to journal (persistent store)
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int) // EVENT

  class Accountant extends PersistentActor with ActorLogging {
    var latestInvoiceId = 0
    var totalAmount = 0
    override def persistenceId: String = "simple-accountant" // make it unique.

    override def receiveCommand: Receive = {
      case Shutdown =>
        log.info(s"Shutting down ${self.path.name}......")
        context.stop(self) // put into normal mailbox not separate one like PoisonPill/kill

      case Invoice(recipient, date, amount) =>

        log.info(s"Received invoice for an amount:- $amount")
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount)) { e =>
          latestInvoiceId += 1
          totalAmount += amount
          //sender() ! "PersistenceACK"
          log.info(s"Persisted $e as invoice number #${e.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        /* step 1 create event
        * step 2 persist all events
        * step 3 update actor state when each event is persisted.*/
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val invoice = pair._1
          val id = pair._2

          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }

        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"Persisted SINGLE $e as invoice number #${e.id}, for total amount $totalAmount")
        }
    }

    override def receiveRecover: Receive = {

      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId += id
        totalAmount += amount
        log.info(s"Recovered invoice #$id, for amount $amount, total amount $totalAmount")
        //throw new RuntimeException("---RuntimeException---")
    }

    /*Persistent actor abstract class also has call-back methods in case persisting fails*/

    /* # 1
    * This method is called if persisting failed.
    * The actor will be STOPPED.
    * Best practice, start the actor again after a while using Backoff supervisor Strategy
    * */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      // seqNr is sequenceNumber of events from journal's point of view
      log.error(s"Failed to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /* # 2
    * This method is called if the journal throws an exception while persisting the event.
    * the actor is RESUMED, not stopped, because you know for sure that the event was
    not persistent and so the actor state was not corrupted in the process.
    */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event,seqNr)
    }



  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for (i <- 1 to 10) accountant ! Invoice("The Sofa company", new Date, i * 1000)
  // we get all messages and now, we will comment the above line of code and log in receiveRecover InvoiceRecorded

  /** persist multiple events (persistAll)*/
  //val newInvoices = for (i <- 1 to 5) yield Invoice("The Awesome Chair", new Date, i * 1000)
  //accountant ! InvoiceBulk(newInvoices.toList)

  /** NEVER EVER CALL persist or persistAll from futures.*/

  /** ShutDown of persistent actors
    *
    * BEST PRACTICE:- Define your owm Shutdown*/
//  accountant ! PoisonPill // Invoice are being sent to dead letters
  accountant ! Shutdown

}
