package com.lb.d11.trs.task

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.kafka.cluster.sharding.KafkaClusterSharding

import scala.concurrent.Future
import scala.concurrent.duration._

object TrsTask {
  def init(system: ActorSystem[_], settings: ProcessorSettings): Future[ActorRef[Command]] = {
    import system.executionContext
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Command) => msg.userId,
      settings = settings.kafkaConsumerSettings()
    ).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey)(createBehavior = _ => TrsTask())
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

  final case class GetRunningTotal(userId: String, replyTo: ActorRef[RunningTotal]) extends Command

  // state
  final case class RunningTotal(total: Int) extends CborSerializable

  def apply(): Behavior[Command] = running(RunningTotal(0))

  private def running(runningTotal: RunningTotal): Behavior[Command] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[Command] {
        case x:UserTrsTask =>
          ctx.log.info("user {}, runningTotal {}", x.userId, runningTotal)
          x.replyTo ! Done
          running(
            runningTotal.copy(total = runningTotal.total+x.amount))
        case GetRunningTotal(id, replyTo) =>
          ctx.log.info("user {} running total queried", id)
          replyTo ! runningTotal
          Behaviors.same
      }
    }
  }
}
