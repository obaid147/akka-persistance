package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {
  /*store for acoustic guitars*/
  val ACOUSTIC = "acoustic"
//  val ELECTRIC = "electric"
  val PRICE = 200
  // datastructures
  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC, price: Int = PRICE)

  // COMMAND
  case class AddGuitar(guitar: Guitar, quantity: Int)

  //EVENT
  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)
  case class GuitarAddedV3(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String, price: Double)

  class InventoryManager extends PersistentActor with ActorLogging {
    override def persistenceId: String = "guitar-inventory-manager"

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType, price), quantity) =>
        persist(GuitarAddedV3(id, model, make, quantity, guitarType, price)) { _ =>
          addGuitarInventory(guitar, quantity)
          log.info(s"Added $quantity x $guitar to Inventory")
        }
      case "print" =>
        log.info(s"Current Inventory is:- $inventory")
    }

    override def receiveRecover: Receive = {
      case event @ GuitarAddedV3(id, model, make, quantity, guitarType, price) =>
        log.info(s"Recovered $event")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitarInventory(guitar, quantity)
    }

    def addGuitarInventory(guitar: Guitar, quantity: Int): Option[Int] = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    /*
    * When journal sends message to actor (when actor is trying to recover), Journal sends string of bytes that
    * goes through serializer and the serializer Deserializes events received from journal. After that
    * events go through eventAdapter(ReadEventAdapter), that transforms Deserialized events into some other type
    * that Actor can recognize.
    * PIPELINE
    * journal -> serializer -> event adapter -> actor
    * (bytes)    (GuitarAdded) (GuitarAddedV2)  (receiveRecover)
    * application.conf
    * uncomment- !, we will have 10 old guitar events and 10 new guitarV2 added events
    * re-run we see Recovered GuitarAddedV2 and  Added 5 x Guitar
    * comment the !
    * re-run
    * Recovered GuitarAddedV2 1 to 10 and again
    * Recovered GuitarAddedV2 1 to 10, the printing the Current Inventory.......
    */
    /** We modify GuitarAddedV2 to GuitarAddedV3....
      *
      * There is also WriteEventAdapter that goes like:-
      * actor -> write event adapter -> serializer -> journal
      * toJournal() method is used...
      * WriteEventAdapter is used for backward compatibility
      * If we want to use both with different class which will duplicate the code,
      * We can simply use / extend EventAdapter trait which extends both Write and Read Event adapters.
      * */

    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
        EventSeq.single(GuitarAddedV3(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC, PRICE))
      case other => EventSeq.single(other)
    }

  }
  val system = ActorSystem("EventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

  val guitars = for(i <- 1 to 10) yield Guitar(s"$i", s"Fender_$i", "Pack")

  /*guitars.foreach { guitar =>
    inventoryManager ! AddGuitar(guitar, 5)
  }*/

  /*If we try to change the schema ie: add or modify a field in persisted event, we would actually need to create a new event
    and handle it in receiveRecover.

  *changes like from acoustic to electric/any of these 2, the old acoustic store will
  * not be compatible with the new one. So, we create a new event and persist it only instead of old event
  * and handle both events in recover.*/
  inventoryManager ! "print"
  /*for the above problem where schema changes, Scala gives us Event adapters*/
}
