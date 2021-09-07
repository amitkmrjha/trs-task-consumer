package com.lb.d11.trs.task

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.scaladsl.AkkaManagement
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.lb.d11.trs.task.repository.{ScalikeJdbcSetup, WalletRepository, WalletRepositoryImpl}

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait Command
case object NodeMemberUp extends Command
final case object ShardingStarted extends Command
final case class BindingFailed(reason: Throwable) extends Command

object Main {
  def main(args: Array[String]): Unit = {

    def isInt(s: String): Boolean = s.matches("""\d+""")

    args.toList match {
      case single :: Nil if isInt(single) =>
        val nr = single.toInt
        init(2550 + nr, 8550 + nr, 8080 + nr)
      case portString :: managementPort :: frontEndPort :: Nil
          if isInt(portString) && isInt(managementPort) && isInt(frontEndPort) =>
        init(portString.toInt, managementPort.toInt, frontEndPort.toInt)
      case _ =>
        throw new IllegalArgumentException("usage: <remotingPort> <managementPort> <frontEndPort>")
    }
  }

  def init(remotingPort: Int, akkaManagementPort: Int, frontEndPort: Int): Unit = {
    ActorSystem(Behaviors.setup[Command] {
      ctx =>
        AkkaManagement(ctx.system.toClassic).start()
        val cluster = Cluster(ctx.system)
        val upAdapter = ctx.messageAdapter[SelfUp](_ => NodeMemberUp)
        cluster.subscriptions ! Subscribe(upAdapter, classOf[SelfUp])
        ScalikeJdbcSetup.init(ctx.system)
        val settings = ProcessorSettings("kafka-source-provider", ctx.system.toClassic)
        val walletRepository = new WalletRepositoryImpl()


        starting(ctx,joinedCluster = false,settings,walletRepository)
    }, "KafkaToProjection", config(remotingPort, akkaManagementPort))

    def start(ctx: ActorContext[Command],
              joinedCluster: Boolean,
              settings: ProcessorSettings,
              walletRepository:WalletRepository): Behavior[Command] = {
      import ctx.executionContext
      ctx.log.info("Starting projection ")
      TrsTaskProjection.init(ctx.system,settings, walletRepository)

      val binding: Future[Http.ServerBinding] = startGrpc(ctx.system, frontEndPort,walletRepository)
      binding.onComplete {
        case Failure(t) =>
          ctx.self ! BindingFailed(t)
        case _ =>
      }
      running(ctx, binding)
    }
    def starting(ctx: ActorContext[Command],
                 joinedCluster: Boolean,
                 settings: ProcessorSettings,
                 walletRepository:WalletRepository): Behavior[Command] = Behaviors
      .receive[Command] {
        case (_, NodeMemberUp)  =>
          ctx.log.info("Member has joined the cluster")
          //starting(ctx,joinedCluster = true, settings,walletRepository)
          start(ctx, joinedCluster = true, settings,walletRepository)
      }

    def running(ctx: ActorContext[Command], binding: Future[Http.ServerBinding]): Behavior[Command] =
      Behaviors.receiveMessagePartial[Command] {
        case BindingFailed(t) =>
          ctx.log.error("Failed to bind front end", t)
          Behaviors.stopped
      }.receiveSignal {
        case (ctx, Terminated(processor)) =>
          ctx.log.warn(s"Actor Ref ${processor.path}. Shutting down")
          binding.map(_.unbind())(ctx.executionContext)
          Behaviors.stopped
      }


    def startGrpc(system: ActorSystem[_], frontEndPort: Int,
                  walletRepository:WalletRepository): Future[Http.ServerBinding] = {
      implicit val sys: ActorSystem[_] = system
      implicit val ec: ExecutionContext = system.executionContext

      val grpcService: TrsTaskService = new TrsTaskGrpcService(system,walletRepository)
      val service: HttpRequest => Future[HttpResponse] =
        ServiceHandler.concatOrNotFound(
          TrsTaskServiceHandler.partial(grpcService),
          ServerReflection.partial(List(TrsTaskService))
        )
        Http()
          .newServerAt("127.0.0.1", frontEndPort)
          .bind(service)
          .map(_.addToCoordinatedShutdown(3.seconds))

    }

    def config(port: Int, managementPort: Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = $managementPort
       """).withFallback(ConfigFactory.load())
  }
}
