package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Postgres extends App {
  val postgresActorSystem = ActorSystem("postgresActorSystem", ConfigFactory.load().getConfig("postgresDemo"))
  val persistentActor = postgresActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [$i]"
  }
  persistentActor ! "print"
  persistentActor ! "snap"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [$i]"
  }

  /** For postgres-------------
  * open terminal in parent dir:- docker-compose up
  * open new tab in same location
  * ./psql.sh
  * select * from public.journal; // empty table
  * run this application
  * select * from public.journal;
    * We have some all the fields...
    * we can see the format of message is not readable, caused by java's serialization of messages.

  * select * from public.snapshot // we have table fields and data of a single snapshot...
  *
  *
  * Run the application again
  * select * from public.snapshot; // we can see another snapshot added.
  * select * from public.journal; // lot more messages
  * */
}
