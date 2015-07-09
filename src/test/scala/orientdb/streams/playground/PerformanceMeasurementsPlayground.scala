package orientdb.streams.playground

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import org.scalatest.{Matchers, WordSpecLike}
import orientdb.streams.{NonBlockingQueryBackpressuring, NonBlockingQueryBuffering, NonBlockingQuery}

import scala.reflect.ClassTag



// TODO no real tests, just something
abstract class PerformanceMeasurements(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers {
  val uuid = java.util.UUID.randomUUID.toString
  //implicit val db = new ODatabaseDocumentTx(s"memory:testdb$uuid");db.create()
  implicit val db = new ODatabaseDocumentTx(s"remote:localhost/test"); db.open("root", "test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val amountOfUsers = 10000

  val runtime  = Runtime.getRuntime()
  def name: String
/*

  val users = (for (i ← 0 to amountOfUsers) yield {
    val doc = new ODocument("Person")
    doc.field("name", s"Luke$i")
    doc.field("surname", s"Skywalker$i")
    doc.save()
  }).toVector
*/

  // just toy tools

  def memory[R](block: => R): R = {
    val before = runtime.totalMemory - runtime.freeMemory
    val r = block
    val after = runtime.totalMemory - runtime.freeMemory
    println(s"$name| Used memory: ${after - before}")
    r
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    println(s"$name| Elapsed time: (${t1 - t0}) ns")
    result
  }

  def NonBlockingQuery[A: ClassTag](query: String): NonBlockingQuery[A]

  "Performance" should {
    "something" in {
      time {
        val query = {
          NonBlockingQuery[ODocument]("SELECT * FROM Person ORDER BY name")
        }
        val src =  {
          Source(query.execute()).runWith(TestSink.probe[ODocument])
        }
        {
          src.request(1)
        }
      }
    }
  }
}

class PerformanceMeasurementsBuffering(_system: ActorSystem) extends PerformanceMeasurements(_system) {
  def this() = this(ActorSystem("performance-non-blocking-query-buffering"))
  def name="buffering"
  override def NonBlockingQuery[A: ClassTag](query: String): NonBlockingQuery[A] = NonBlockingQueryBuffering[A](query)
}


class PerformanceMeasurementsBackpressuring(_system: ActorSystem) extends PerformanceMeasurements(_system) {
  def this() = this(ActorSystem("performance-non-blocking-query-locking"))
  def name="locking"
  override def NonBlockingQuery[A: ClassTag](query: String): NonBlockingQuery[A] = NonBlockingQueryBackpressuring[A](query)
}
