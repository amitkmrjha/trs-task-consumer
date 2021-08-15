package util
import akka.Done
import akka.actor.ActorSystem
import slick.backend.DatabaseConfig
import slick.jdbc.JdbcProfile
//#important-imports
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._
import slick.jdbc.GetResult
//#important-imports

import scala.concurrent.Future
object PostgresTest extends App {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  implicit val session = SlickSession.forConfig("slick-postgres")

  system.registerOnTermination(session.close())
  println()
  println(s"I am here ")
  println()
  println(s"${session.db.createSession().metaData}")
  println()

  // The example domain
  case class User(id: Int, name: String)

  // We need this to automatically transform result rows
  // into instances of the User class.
  // Please import slick.jdbc.GetResult
  // See also: "http://slick.lightbend.com/doc/3.2.1/sql.html#result-sets"
  implicit val getUserResult = GetResult(r => User(r.nextInt, r.nextString))

  // This import enables the use of the Slick sql"...",
  // sqlu"...", and sqlt"..." String interpolators.
  // See also: "http://slick.lightbend.com/doc/3.2.1/sql.html#string-interpolation"
  import session.profile.api._

  // Stream the results of a query
  val done: Future[Done] =
    Slick
      .source(sql"SELECT ID, NAME FROM ALPAKKA_SLICK_SCALADSL_TEST_USERS".as[User])
      .log("user")
      .runWith(Sink.ignore)
  //#source-example

  done.onComplete {
    case _ =>
      system.terminate()
  }
}
