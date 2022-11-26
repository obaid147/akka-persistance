package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object RecoveryDemo extends App {
  /** ****ALL COMMANDS SENT DURING RECOVERY ARE STASHED..............*/

  //COMMAND
  case class Command(contents: String)

  //EVENT
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {


    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "Not"} finished.")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case Event(id, contents) =>
        /*if (contents.contains("400"))
          throw new RuntimeException("I can't take this.....")*/
        log.info(s"Recovered $contents, recovery is ${if(this.recoveryFinished)"" else "Not"} finished.")
        context.become(online(id+1))
        /**Here this will not change the handler
        * receiveRecover will always be used during recovery regardless of how many context becomes we out inside
        * After recovery, the "normal" handler will be the result of all the stacking of context.become*/
      case RecoveryCompleted =>
        log.info("I have finished recovery")
    }

    /*on Failure of recovery*/
    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
      /*ctrl click onRecoveryFailure & search for recoveryBehavior*/
    }

    /**Recover customizing => at most 100 messages*/
    //override def recovery: Recovery = Recovery(toSequenceNr = 100) //#1
    //override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.latest) // #2
    //override def recovery: Recovery = Recovery.none // #3 recover nothing.

  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /* #1
  * Stashing commands
  * */
  //for(i <- 1 to 1000) recoveryActor ! Command(s"Command $i")
  // if we re-run like this only, We will have first 1000 commands Recovered that were persisted in previous run.
  // and then 1000 persisted for this run.
  /** We sent commands during recovers.
    * *****ALL COMMANDS DURING RECOVERY ARE STASHED..............*/


  /* #2
  * Failure during recovery onRecoveryFailure()
  * and failing receiveRecover with RuntimeException
  * Keep actor in recovery mode, No command to persist ====> comment the ! and re-run.
  * After we reach recovering till 399, receiveRecover is called + actor is stopped.
  * */

  /* #3
  * There are 3 ways (toSequenceNr, SnapshotSelectionCriteria, none)
  * Customizing recovery to a certain SEQ Number. and re-run, SnapshotSelectionCriteria.latest, Recovery.none.
  * We will recover 100 messages successfully without exception, because exception is caught at 400.
  *
  *  SnapshotSelectionCriteria.latest {all events}, Recovery.none {no event recovered}.
  *
  * DO NOT persist more events after a customized recovery (we may corrupt messages in mean time).
  * */

  /* #4
  * Recovery Status / Knowing when we are done recovering.
  * Injecting in receiveCommand logs and receiveRecover logs and comment the RuntimeException and re-run.
  * Only useful when shared between receiveCommand & receiveRecover, otherwise not useful.
  *
  * --GETTING A SIGNAL when recovering is done is USEFUL..... with special command RecoveryCompleted
  * */

  /* #5 Stateless Actors.........
  *
  * adding id to Event
  * use var latestPersistedEventId = 0 and put in where required.
  * and after that get rid of the var, then compiler will tell us what we need to fix.
  *
  * DELETE the Journal and uncomment 1000 command. !
  *
  * During recovery, Handler is not changed.
  * After recovery the final handler that is used during context become will be then used.
  * */

  recoveryActor ! Command("special command1") //id 999
  recoveryActor ! Command("special command2") // id 1000
  /*The id of these 2 special messages are not 0 and 1 because the actor is picking up where it left off.
  * in context.become, we increment the id to avoid duplication of id's*/
}
