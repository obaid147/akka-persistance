package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {
  /* persistent Actor for voting station
  *
  * commands // Vote(citizenID: String, candidate: String)
  *
  * keep track of citizens who voted no duplicates.
  * poll: mapping between candidate and number of received votes so far
  *
  The actor must be able to recover it's state if shutdown or restarted.
  */

  case class Vote(citizenId: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {
    var citizens: mutable.ListBuffer[String] = new mutable.ListBuffer[String]()
    var poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]()

    override def persistenceId: String = "Voting-Station"

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenId, candidate) =>
        if (!citizens.contains(vote.citizenId)) {
          persist(vote) { _ => // command sourcing
            log.info(s"Persisted: $vote")
            handleInternalStateChange(citizenId, candidate)
          }
        }else {
          log.warning(s"Citizen $citizenId is trying to vote multiple times!")
        }
      case "print" =>
        log.info(s"current state: \nCitizens:- $citizens \nPolls: $poll")
    }
    def handleInternalStateChange(citizenId: String, candidate: String): Unit = {
        citizens += citizenId
        val votes = poll.getOrElse(candidate, 0)
        poll.put(candidate, votes + 1)
    }

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenId, candidate) =>
        log.info(s"RECOVERED: $vote")
        handleInternalStateChange(citizenId, candidate)
    }
  }

  val system = ActorSystem("VotingSystem")
  val votingStation = system.actorOf(Props[VotingStation], "votingStation")

  val votesMap = Map[String, String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Obaid" -> "Martin",
  )
  /*votesMap.keys.foreach(citizen =>{
    votingStation ! Vote(citizen, votesMap(citizen))
  } )*/

  votingStation ! Vote("Obaid", "Roland") // warning as citizen is trying to vote again
  votingStation ! "print"

}
