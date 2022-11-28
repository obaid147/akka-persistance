package part3_stores_serialization

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  *  Distributed database with two benefits
  * 1. Highly available:-
  *   If one of the nodes on which cassandra is deployed goes down, the DB can still works fine.
  * 2. High throughput:-
  *   Great performance on rights and high throughput. Beneficial for us because we write events to cassandra all time.
  *
  * CONS:-
  * 1. eventual consistency:-
  *   if we write a value to Cassandre, then one of the nodes will be the first to write it and the other nodes
  *   will have to wait a certain time before they're are also updated.
  *   During this time, The subsequent reads to Cassandre may be inconsistent, but that's not
  *   such a big problem in the context of akka-persistence, because we do rights to the database most of the time.
  * 2. soft delete and "tombstone":-
  *   if we want to delete data from Cassandra, Cassandra will actually create more
  *   data in the form of these tombstone's data structures to mark which pieces of data are actually deleted.
*/

object Cassandra {
  val cassandraActorSystem: ActorSystem =
    ActorSystem("cassandraActorSystem", ConfigFactory.load().getConfig("cassandraDemo"))
  val persistentActor: ActorRef =
    cassandraActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [$i]"
  }
  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [$i]"
  }
}

/* docker-compose.yml cassandra # service
* build.sbt
* akka-persistence-cassandra holds snapshot and journal plugins. and other library.
* application.conf
*
* make sure cassandra container is running, "docker-compose up"
*
* Run this application.
* We can see in terminal, cassandra create key space and tables for us automatically eg: akka.messages,
* akka_snapshot.snapshot...etc.
*
* open other terminal tab.
* ./cqlsh.sh
*  We are connected to cassandra cluster.
*  select * from akka.messages // we see the data in a colorful table with persistence id.
*  select * from akka_snapshot.snapshot // we see the information written to table.
*
*  Run the app again,
* select * from akka.messages // we have lot more data now.
*  select * from akka_snapshot.snapshot // Now, we have 2 snapshots instead on one.
* */
