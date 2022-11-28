package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

/** from DB demos using postgres and cassandra, We saw that the messages we persisted were in binary form because
  * akka serializes the events using default JVM serialization.
  * When running any akka-persistence application, we saw warnings, "java serialization not ideal"
  * SERIALIZATION = turning in-memory objects into a recognizable format.
  *
  * "java serialization not ideal":-
  * 1. Not optimal, memory consumption.
  * 2. speed/performance
  */

//COMMAND
case class RegisterUser(email: String, name: String)

// EVENT
case class UserRegistered(id: Int, email: String, name: String)



//SERIALIZER  --- config in application.conf
class UserRegistrationSerializer extends Serializer {
  // implement 4 methods
  val SEPARATOR = "//"

  override def identifier: Int = 53278 // any number we can choose

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case event@UserRegistered(id, email, name) =>
        println(s"SERIALIZING $event")
        s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes() // String serialized as bytes
      case _ => throw new IllegalArgumentException("only user registration events supported in this serializer")
    }
  } /*turns an object[AnyRef] to an array of bytes Array[Byte] which will then be written
    either a file/journal is implemented with (db cassandra,..etc)*/

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(SEPARATOR)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"Deserialized $string to $result")

    result
  }
  /* takes an array of bytes Array[byte] and returns an object we want and 2nd parameter in the form of Option of
     Class 2nd param is used, This is used to instantiate the proper class that you want via reflection.
     This manifest will be passed with some class if the includeManifest method returns true.
     If the include manifest returns true, the actual class here will be passed so that you can use this
     class's constructor to instantiate the proper object from these bytes that you pass.*/

  override def includeManifest: Boolean = false
  // fromBinary() manifest will have None as includeManifest returns false.

}

//ACTOR
class UserRegistrationActor extends PersistentActor with ActorLogging {
  var currentId = 0

  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(s"Persisted $e")
      }
  }

  override def receiveRecover: Receive = {
    case event@UserRegistered(id, _, _) =>
      log.info(s"RECOVERED $event")
      currentId = id
  }
}

object CustomSerialization extends App {

  /* Run the app
  * open cygwin cd /cygdrive/d/thisDrive
  * docker-compose up
  * open another cygwin terminal in same dir
  * bash cqlsh.sh
  * select * from akka.messages
  * we see 30 rows
  * copy the purple hex code and convert it online to text.
  *
  * comment the ! command
  * re-run app
  * we see 40 rows and deserialized.
  * */
  val system = ActorSystem("CustomSerialization", ConfigFactory.load().getConfig("customSerializerDemo"))
  val userRegistrationActor = system.actorOf(Props[UserRegistrationActor], "userRegistration")

  for (i <- 1 to 10) {
    userRegistrationActor ! RegisterUser(s"user_$i@email.com", s"user_$i")
  }

  /** When we send command to UserRegistrationActor, It will call persist method which will attempt to send
    * UserRegistered event to the journal. BUT Before UserRegistered gets to the journal, It passes
    * through SERIALIZER that converts this into the bytes that journal would expect and write the bytes.
    *
    * After we implement our custom Serializer, We will configure it so that it will sit between our UserRegistrationActor
    * and the journal.
    *
    *
    * When actor does recovery, the opposite happens.
    * journal will send the event back to the actor and serializer will call the fromBinary() to turn
    * the array of byes into the actual object that the Actor will expect in receiveRecover(). */
}
