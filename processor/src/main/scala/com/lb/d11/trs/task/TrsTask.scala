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
  final case class QueueSubmitStatus(userId: String,amount: Int,status: String,replyTo: ActorRef[Done])  extends Command

  final case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends Command

  // state
  final case class RunningTotal(total: Int) extends CborSerializable

  def apply(slickMySql:SlickPostgres): Behavior[Command] = running(RunningTotal(0),slickMySql)

  private def running(runningTotal: RunningTotal,slickMySql:SlickPostgres): Behavior[Command] = {
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext
      Behaviors.receiveMessage[Command] {
        case x:UserTrsTask =>
          val futureResult = queueOffer(slickMySql,x)
         ctx.pipeToSelf(futureResult){
           case Success(s) => QueueSubmitStatus(x.userId,x.amount,s, x.replyTo)
           case Failure(e) => QueueSubmitStatus(x.userId,x.amount,e.getMessage, x.replyTo)
         }
          Behaviors.same
        case GetRunningTotal(id, replyTo) =>
          ctx.log.info("user {} running total queried", id)
          replyTo ! runningTotal
          Behaviors.same
        case QueueSubmitStatus(id,amount,status,replyTo) =>
          val total = runningTotal.total+amount
          replyTo ! Done
          ctx.log.info("user {}, runningTotal {}, queue submit {}", id,total,status)
          running(runningTotal.copy(total),slickMySql)
      }
    }
  }

  def queueOffer(slickMySql:SlickPostgres,task:UserTrsTask)(implicit  ec:ExecutionContext ) = {
    slickMySql.jdbcQueue.offer(task) map{
      case QueueOfferResult.Enqueued    => s"enqueued ${task.userId} ${task.amount}"
      case QueueOfferResult.Dropped     => s"dropped ${task.userId} ${task.amount}"
      case QueueOfferResult.Failure(ex) => s"Offer failed ${ex.getMessage}"
      case QueueOfferResult.QueueClosed => "Source Queue closed"
    }
  }
}
