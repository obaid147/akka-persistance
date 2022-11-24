package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

/** If we use snapshots, we basically tell akka not to recover entire event history.
  * Rather recover since the latest snapshots.
  *
  * Example:- let's say we have events and snapshots.
  * event1
  * event2
  * event3
  * SNAPSHOT-1
  * event4
  * event5
  * SNAPSHOT-2
  * event6
  * event7
  *
  *
  * The only thing here that are going to get replayed during recovery is SNAPSHOT-2 (latest snapshot) and
  * all events since that snapshot (event6 & event7).
  *
  *
  * If snapshot was successful, then the persistent actor will receive a SaveSnapshotSuccess(SnapshotMetadata)
  * and if failed, it will receive SaveSnapShotFailure(SnapshotMetadata, Throwable). Both are final case class
  * */

import scala.collection.mutable

object Snapshots extends App {
  /** When we have a chat system like whatsapp, We send lot of messages and
    * recovering them takes time to finish and Snapshot speeds this up. */

  // COMMANDS
  case class ReceivedMessage(contents: String) // message from contact

  case class SentMessage(contents: String) // message to contact

  // EVENTS
  case class ReceivedMessageRecord(id: Int, contents: String)

  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {

    val MAX_MESSAGES = 10 // hold last 10 messages in memory
    val lastMessages = new mutable.Queue[(String, String)]()
    var currentMessageId = 0
    var commandsWithoutCheckpoint = 0


    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { _ =>
          log.info(s"Received message: $contents")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1

        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { _ =>
          log.info(s"Sent message: $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" => log.info(s"Most recent Messages: $lastMessages")

      /*SnapShot related messages that we are going to receive after SavedSnapshot was Success/Failure*/
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"saving snapshot SUCCEEDED $metadata")
      case SaveSnapshotFailure(metadata, cause) =>
        log.info(s"saving snapshot $metadata FAILED $cause")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"RECOVERED receive message:#$id, $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"RECOVERED sent message:#$id, $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered Snapshot: $metadata")

        /** our current state which is lastMessages has to have exact contents of snapshot payload */
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint..........")
        saveSnapshot(lastMessages) // saving snapshot is an asynchronous operation.
        //Think of it like sending a message to a snapshot store, much like persisting a message to journal.
        // Allows persisting anything that is serializable to a dedicated persistent store.
        commandsWithoutCheckpoint = 0 // reset checkpoint

        /* This gives us a special case SnapshotOffer which takes metadata and contents*/
      }
    }
  }

  import Chat._

  val system = ActorSystem("SnapshotDemo")
  val chat = system.actorOf(props("obaid123", "wel123"))

  /*for(i <- 1 to 100000) {
    chat ! ReceivedMessage(s"Akka Rocks $i")
    chat ! SentMessage(s"Akka Rules $i")
  }*/
  // after running this, comment this and check how much time will it take chat system & for chat actor to recover
  // all the persisted events.

  /** * imagine in production where you have - times more events to persist, this will take longer */

  /** This is where SnapShot comes in..........
    * maybeCheckpoint()
    * commandsWithoutCheckpoint
    *
    * before running this after adding snapshot,
    * GOTO:- application.conf to config Snapshot store.
    *
    * uncomment the for loop sending messages, and comment again to check recovery speed...
    *
    * use print...
    * */

  //chat ! "print"
  // now comment for and re-run, In a snap, latest messages are recovered.



  /***********SNAPSHOT PATTERN***********************
    * After each persist, maybe save a snapshot
    * If we save a snapShot, Handle SnapshotOffer() message in receiveRecover.
    *
    * (Optional but Best Practice) Handle SaveSnapshotSuccess and Failure in receiveCommand.
    * profit from extra speed.......
  */
}
