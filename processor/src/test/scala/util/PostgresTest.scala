package util
import akka.Done
import akka.actor.ActorSystem
import com.lb.d11.trs.task.TrsTask.AddTrsTask
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

  final case class UserTrsTaskLog(userId: String,roundId: String,leagueId: String,transType: String,
                               amount: Int, status: String, transactionId: String,lastAccountBalance: Int)

  // We need this to automatically transform result rows
  // into instances of the User class.
  // Please import slick.jdbc.GetResult
  // See also: "http://slick.lightbend.com/doc/3.2.1/sql.html#result-sets"
  implicit val getUserTaskResult = GetResult(r => UserTrsTaskLog(r.nextString, r.nextString,r.nextString(),r.nextString(),
    r.nextInt(),r.nextString(),r.nextString(),r.nextInt()))

  // This import enables the use of the Slick sql"...",
  // sqlu"...", and sqlt"..." String interpolators.
  // See also: "http://slick.lightbend.com/doc/3.2.1/sql.html#string-interpolation"
  import session.profile.api._

  // Stream the results of a query
  val done: Future[Done] =
    Slick
      .source(sql"SELECT * FROM wallet".as[UserTrsTaskLog])
      .log("UserTrsTaskLog")
      .runWith(Sink.ignore)
  //#source-example

  done.onComplete {
    case _ =>
      system.terminate()
  }
}
