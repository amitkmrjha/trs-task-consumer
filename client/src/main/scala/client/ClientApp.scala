package client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import com.lb.d11.trs.task.{TrsTaskServiceClient, TrsTaskStatsRequest}

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration
import scala.io.StdIn

object ClientApp extends App {
  implicit val system: ActorSystem = ActorSystem("UserClient")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8081).withTls(false)
  val client = TrsTaskServiceClient(clientSettings)

  var userId = ""
  while (userId != ":q") {
    println("Enter user id or :q to quit")
    userId = StdIn.readLine()
    if (userId != ":q") {
      val runningTotal = Await.result(client.userStats(TrsTaskStatsRequest(userId)), Duration.Inf)
      println(
        s"User ${userId} has total ${runningTotal}")
    }

  }
  println("Exiting")
  system.terminate()
}
