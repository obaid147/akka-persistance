package part1_recap

import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.{Failure, Success}


object ScalaRecap extends App {

  class Animal
  trait Carnivore {
    def eat(a: Animal): Unit
  }

  object Carnivore

  abstract class MyList[+A]

  1+2
  1.+(2)

  val anInc1 = new Function1[Int, Int]  {
      def apply(x: Int): Int = {
        x+1
      }
  }
  val anInc2: Int => Int = x => x+1

  List(1,2).map(anInc2)

  val unknown: Any = 2
  val order = unknown match {
    case 1 => "one"
    case 2 => "Two"
    case 3 => "unknown"
  }

  /*try{
    throw new RuntimeException("OOPS!!!")
  } catch {
    case e: RuntimeException => println(e)
  }*/


  //ADVANCED
  val future = Future{
    Thread.sleep(200)
    10
  }
  future.onComplete{
    case Success(value) => println(value)
    case Failure(ex) => println(ex)
  }// executed on some thread that maybe a thread that completed the future, maybe calling thread onComplete


  val partialFn: partialAlias = {
    case x: Int => x
    case _ => 10
  }
  println(partialFn(101))


  // type aliases
  type partialAlias = PartialFunction[Int, Int]

  type AkkaReceive = PartialFunction[Any, Unit]

  def receive: AkkaReceive = {
    case _ => 123
  }


  implicit val timeout1: Timeout = Timeout(10.seconds)
  implicit val timeout2: Int = 3000

  def setTimeout(f:() => Unit)(implicit timeout: Int): Int = timeout
  val x = setTimeout(() => println("() => Unit"))// other arg is injected by compiler...
  println(x)

  /**Creating Futures also needs a 2nd arg which is injected by the compiler using EC.Implicits.global import*/


  // conversion
  case class Person(name: String) {
    def greet: String = s"hey... $name"
  }

  // implicit methods
  implicit def fromStrToPerson(name: String): Person = {
    Person(name)
  }

  "obaid".greet // here
  /*compiler automatically calls the fromStrToPerson on "obaid" string
  * Transforms it to a person and
  * calls greet method on that*/
  // its as if below:-
  fromStrToPerson("obaid").greet

  //implicit classes
  implicit class Dog(name: String) {
    def bark = println("bark")
  }
  "lessi".bark
  /*compiler construct new Dog from "lessi" string
  * Then calls bark method on that*/
  // its as if below:-
  new Dog("lessi").bark

  // implicit organizations


  // implicits are fetched from local scope. First place compiler looks for implicits
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1, 2, 3).sorted // compiler injects numberOrdering from local scope.


  // imported scope. Second place compiler looks for implicits
  // Future ----- import scala.concurrent.ExecutionContext.Implicits.global


  // companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  println(List(Person("obaid"), Person("diabo")).sorted)
  /* Compiler looks for all the companion objects involved in the call which here is List & Person.
  *  compiler find implicit in companion object Person and injects
  * (Person.personOrdering) to the .sorted method as 2nd arg list
  *
  * result // Lit(Person(diabo), Person("obaid"))
  */

}
