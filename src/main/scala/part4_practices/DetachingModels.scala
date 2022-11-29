package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DetachingModels extends App {
  /**
    * domain model = events our actor thinks it persists.
    * data model = objects which actually gets persisted.
    *
    * good practice:- make these two models independent.
    * This eases our work with schema evolution that we saw in EventAdapters.scala
    * */

  /*Imagine we want to add coupons to our guitar store*/
  class CouponManager extends PersistentActor with ActorLogging {
    import DomainModel._

    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User]()

    override def persistenceId: String = "coupon-manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(s"Persisted $e")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"RECOVERED $event")
        coupons.put(code, user)
    }
  }

  val system = ActorSystem("detachingModels", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")

  import DomainModel.{ApplyCoupon, Coupon, User}

  /*for(i <- 10 to 15) {
    val coupon =  Coupon(s"MEGA_COUPON_$i", i * 10)
    val user = User(s"$i", s"User_$i@mail.com", s"userName_$i")
    couponManager ! ApplyCoupon(coupon, user) // application.conf
  }*/

  /**
    * Run the app and make sure cassandra is up and running.
    * In cygwin -> bash cqlsh.sh
    * select * from akka.messages;
    * We see coupon-manager.....
    * our actor thinks it Persisted CouponApplied (coupon-manager), rather it persisted WrittenCouponApplied
    * Stop the app and comment the ! loop.
    * and run the app again
    * We get WrittenCouponApplied to DOMAIN model and RECOVERED CouponApplied as our actor thinks.
    * So, our actor works both ways. On Writing and Reading.
    * */
}

// outside app
object DomainModel { // Actor thinks this will be persisted to journal
  case class User(id: String, email: String, name: String)

  case class Coupon(code: String, promotionAmount: Int)

  //command
  case class ApplyCoupon(coupon: Coupon, user: User)

  //event
  case class CouponApplied(code: String, user: User)
}

object DataModel {
  /*Write EventAdapter that turns DomainModel.CouponApplied event to DataMode.WrittenCouponApplied
  that will end-up being in journal.*/
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, username: String)
}

class ModelAdapter extends EventAdapter {
  import DataModel._
  import DomainModel._
  // both read and write

  override def manifest(event: Any): String = "CMA" //CMA(coupon model adapter)// includes type hint.

  // turns event that was deserialized from journal into an object that will later be passed to actor.
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {

    // DataFlow:- journal -> serializer -> fromJournal -> actor
      case event @ WrittenCouponApplied(code, userId, userEmail) => // old persisted event in journal
        print(s"Converting $event to DOMAIN model")
        EventSeq.single(CouponApplied(code, User(userId, userEmail, "")))
      case event @ WrittenCouponAppliedV2(code, userId, userEmail, username) => // new // application.conf V2
        print(s"Converting $event to DOMAIN model")
        EventSeq.single(CouponApplied(code, User(userId, userEmail, username)))
      case other => EventSeq.single(other)
  }

  override def toJournal(event: Any): Any = event match {
    // actor -> toJournal -> serializer -> journal
      case event @ CouponApplied(code, user) =>
        print(s"Converting $event to DATA model")
        WrittenCouponAppliedV2(code, user.id, user.email, user.name)
  }
}