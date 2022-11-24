package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersists extends App {

  /*
  * Diligent accountant: with every invoice, will persist 2 events
  *   - a tax record for the fiscal authority
  *   - an invoice record for personal logs or some auditing authority.
  * */

  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS ordering is guaranteed
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)


  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef): Props = {
      Props(new DiligentAccountant(taxId, taxAuthority))
    }
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {
    var latestTaxRecordId = 0
    var  latestInvoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        // Think of it like --->  journal ! TaxRecord even thought its batched ....6
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount/3)) { record =>
          /*After we persist TaxRecord to journal, We will notify TaxAuthority about the event*/
          taxAuthority ! record
          latestTaxRecordId += 1
          // nested persisting
          persist("I hereby declare this tax record to be true and complete") { declaration =>
            taxAuthority ! declaration
          }
        }
        // Think of it like --->  journal ! InvoiceRecord
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoiceRecord =>
          taxAuthority ! invoiceRecord
          latestInvoiceRecordId += 1

          // nested persisting
          persist("I hereby declare this invoice record to be true and complete") { declaration =>
            taxAuthority ! declaration
          }
        }

        /*Order of persist received by journal and tax authority
        * 1. Tax Record.
        * 2. Invoice Record.
        * 3. Tax declaration.
        * 4. Invoice declaration.
        * */
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"RECOVERED $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received $message")
    }
  }

  import DiligentAccountant._
  val system = ActorSystem("MultiplePersistDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "RevenueAndCustoms")
  val accountant = system.actorOf(props("UK1234_98765", taxAuthority))

  accountant ! Invoice("The D Company", new Date, 3000)
  /** /* The ordering of persisting events is guaranteed (TaxRecord , InvoiceRecord)*/
    * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING.
    * Journal persists our events as messages
  */

  accountant ! Invoice("The Lang Company", new Date, 6000)
  /*First group of 4 messages will arrive first then these 4.*/

}
