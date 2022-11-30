package part4_practices

import akka.NotUsed
import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory

import scala.util.Random

/**
  * persistent stores are also used for reading data
  * Event sourcing is powerful because it allows us not only to query the current state of things but
  * to find out what the state of system was at any given time.
  *
  * Queries supported by persistence query API:-
  * 1. select persistence IDs.
  * 2. select event by persistence IDs.
  * 3. select events across persistence IDs, by tags
  *
  * Use Cases:-
  * 1. Which persistence actors are active.
  * 2. recreate older state.
  * 3. track how we arrived in the current state of the actor.
  * 4. data processing / aggregation on events in the entire store. */
object PersistenceQueryDemo extends App {

  // configure persistence store to be cassandra-journal and cassandra-snapshot-store.
  val system: ActorSystem = ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQuery"))

  /*Accessing persistence query API:-
    - readJournal
  */

  val readJournal: CassandraReadJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

    //# 1
  /*val persistenceIds: Source[String, NotUsed] =
    readJournal.persistenceIds() // get all persistence id's that are available in journal(system), infiniteStream*/

  val persistenceIds = readJournal.currentPersistenceIds()
  // finite stream, returns available persistenceIds at that time and stream will close.

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

  /*persistenceIds.to(Sink.foreach{ persistenceId =>
    println(s"Found persistence id:- $persistenceId")
  }).run()*/

  /*persistenceIds.runWith(Sink.foreach { persistenceId =>
    println(s"Found persistence id:- $persistenceId")
  })*/

  /*persistenceIds.runForeach { persistenceId =>
    println(s"Found persistence id:- $persistenceId")
  }*/
  /** run the app and we will see all the persistence IDs, make sure cassandra container is running*/


  class SimplePersistenceQueryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query"

    override def receiveCommand: Receive = {
      case msg => persist(msg){ _ => log.info(s"persisted $msg") }
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered $e")
    }
  }

  val simplePersistenceQueryActor = system.actorOf(Props[SimplePersistenceQueryActor], "simplePersistenceQueryActor")

  import system.dispatcher
  import scala.concurrent.duration.DurationInt
  system.scheduler.scheduleOnce(5.seconds) {
//    simplePersistenceQueryActor ! "hello persistent actor"
    simplePersistenceQueryActor ! "hello for 2nd time."
  }
  /*First we see existing persistence id's, then after 5 seconds once the message is sent, we will see new
  * persistence id "persistence-query".*/



  // #2 events by persistenceId
  val event: Source[EventEnvelope, NotUsed] =
    readJournal.eventsByPersistenceId("persistence-query", 0, Long.MaxValue) // infinite
    //readJournal.currentEventsByPersistenceId("persistence-query", 0, Long.MaxValue) // finite stream

  event.runForeach{ event =>
    println(s"Read event $event")
  } // after run, uncomment the scheduler and re-run "hello for 2nd time."



  // #3 events by tag:- query events across multiple persistence ids.
  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")
  case class Song(artist: String, title: String, genre: String)
  case class Playlist(songs: List[Song]) // command
  case class PlaylistPurchased(id: Int, songs: List[Song]) // event

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "music-store-checkout"

    var latestPlaylistId = 0

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) { _ =>
          log.info(s"User purchased:- $songs")
          latestPlaylistId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        log.info(s"Recovered:- $event")
        latestPlaylistId += id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case myEvent @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(myEvent, genres) // tags column in cassandra; select * from akka.messages;
      case event => event
    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")
  val r = new Random
  for(_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for(i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist_$i", s"MySong_$i", randomGenre)
    }
    checkoutActor ! Playlist(songs.toList)
  } // app.conf

  val rockPlaylist: Source[EventEnvelope, NotUsed] =
    readJournal.eventsByTag("rock", Offset.noOffset) // Offset = sinceWhenToStartSearchingForEvent

  rockPlaylist.runForeach{ event =>
    println(s"found a playlist with a rock song $event")
  }

  /** eventsByTag will not guaranteed order of events we receive as they were persisted.
    * eventsByPersistenceId, we will receive events as persisted in order.*/
}
