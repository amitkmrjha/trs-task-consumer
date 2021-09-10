package com.lb.d11.trs.task

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.pattern.{ask, pipe}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

object TrsTask {

  def init(system: ActorSystem[_], settings: ProcessorSettings,slickMySql:SlickPostgres): Future[ActorRef[Command]] = {
    import system.executionContext
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Command) => msg.userId,
      settings = settings.kafkaConsumerSettings()
    ).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey)(createBehavior = _ => TrsTask(slickMySql))
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, settings.entityTypeKey.name))
          .withMessageExtractor(messageExtractor))
    })
  }

  sealed trait Command extends CborSerializable {
    def userId: String
  }

  final case class UserTrsTask(userId: String,roundId: String,leagueId: String,transType: String,
                               amount: Int, status: String, transactionId: String,lastAccountBalance: Int,
                               replyTo: ActorRef[Done]) extends Command
  final case class QueueSubmitSuccess(userId: String,amount: Int,replyTo: ActorRef[Done])  extends Command
  final case class QueueSubmitFailure(userId: String,failure: String,replyTo: ActorRef[Done])  extends Command

  final case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends Command

  // state
  final case class RunningTotal(total: Int) extends CborSerializable

  def apply(slickMySql:SlickPostgres): Behavior[Command] = running(RunningTotal(0),slickMySql)

  private def running(runningTotal: RunningTotal,slickMySql:SlickPostgres): Behavior[Command] = {
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext
      Behaviors.receiveMessage[Command] {
        case x:UserTrsTask =>
          ctx.pipeToSelf(slickMySql.jdbcQueue.offer(x)){
            case Success(s) => s match {
              case QueueOfferResult.Enqueued    => QueueSubmitSuccess(x.userId,x.amount, x.replyTo) //enqueued
              case QueueOfferResult.Dropped     => QueueSubmitFailure(x.userId,"Dropped",x.replyTo )
              case QueueOfferResult.Failure(ex) => QueueSubmitFailure(x.userId,s"Offer failed ${ex.getMessage}",x.replyTo )
              case QueueOfferResult.QueueClosed => QueueSubmitFailure(x.userId,s"Source Queue closed",x.replyTo )
            }
            case Failure(e) => QueueSubmitFailure(x.userId,s"pipeToSelf Failure ${e.getMessage}",x.replyTo )
          }
          Behaviors.same
        case GetRunningTotal(id, replyTo) =>
          replyTo ! runningTotal
          Behaviors.same
        case QueueSubmitSuccess(id,amount,replyTo) =>
          val total = runningTotal.total+amount
          running(runningTotal.copy(total),slickMySql)
        case QueueSubmitFailure(id,status,replyTo) =>
          ctx.log.error(s"QueueSubmitFailure for user ${id} : message ${status}")
            Behaviors.same
      }
    }
  }
}
