package com.lb.d11.trs.task

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.{ActorSystem => TypedActorSystem}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.kafka.scaladsl.Committer
import akka.kafka.scaladsl.Consumer
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.pattern.retry
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink}
import com.lb.d11.trs.task.serialization.TrsTaskMessage
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes.SourceWithInstrumented
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

object UserEventsKafkaProcessor {

  sealed trait Command

  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command

  private val logger = LoggerFactory.getLogger(this.getClass)

  def apply(shardRegion: ActorRef[TrsTask.Command], processorSettings: ProcessorSettings): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        implicit val sys: TypedActorSystem[_] = ctx.system
        val result =  startConsumingFromTopic(shardRegion, processorSettings)

        ctx.pipeToSelf(result) {
          result => KafkaConsumerStopped(result)
        }

        Behaviors.receiveMessage[Command] {
          case KafkaConsumerStopped(reason) =>
            ctx.log.info("Consumer stopped {}", reason)
            Behaviors.stopped
        }
      }
      .narrow
  }


  private def startConsumingFromTopic(shardRegion: ActorRef[TrsTask.Command], processorSettings: ProcessorSettings)
                                     (implicit actorSystem: TypedActorSystem[_]) = {

    implicit val ec: ExecutionContext = actorSystem.executionContext
    implicit val scheduler: Scheduler = actorSystem.toClassic.scheduler
    val classic = actorSystem.toClassic

    val rebalanceListener = KafkaClusterSharding(classic).rebalanceListener(processorSettings.entityTypeKey)

    val subscription = Subscriptions
      .topics(processorSettings.topics: _*)
      .withRebalanceListener(rebalanceListener.toClassic)

    Consumer.committablePartitionedSource(processorSettings.kafkaConsumerSettings(), subscription)
      .mapAsyncUnordered(128) { case (topicPartition, source) =>
        source
          .asSourceWithContext(_.committableOffset)
          .mapAsync(20) (committableMessage => askShard(topicPartition,committableMessage, shardRegion, processorSettings))
          .runWith(Committer.sinkWithOffsetContext(CommitterSettings(classic)))
      }
      .runWith(Sink.ignore)


  }

  private def askShard(topicPartition: TopicPartition,committableMessage: CommittableMessage[String, Array[Byte]],
                       shardRegion: ActorRef[TrsTask.Command],
                       processorSettings: ProcessorSettings)(implicit actorSystem: TypedActorSystem[_])= {
    //logger.info(s"Message from  Partion ${topicPartition.partition()}->${committableMessage.record.key()}->${committableMessage.record.offset()} ")
    implicit val ec: ExecutionContext = actorSystem.executionContext
    implicit val scheduler: Scheduler = actorSystem.toClassic.scheduler
    retry(() =>
      shardRegion.ask[Done](replyTo => toUserTrsTask(committableMessage,replyTo))(processorSettings.askTimeout, actorSystem.scheduler),
      attempts = 5,
      delay = 1.second
    )
  }

  private def toUserTrsTask(committableMessage: CommittableMessage[String, Array[Byte]],replyTo: ActorRef[Done]) = {
    val messageProto = TrsTaskMessage.parseFrom(committableMessage.record.value())
    TrsTask.UserTrsTask(
      messageProto.userId,
      messageProto.roundId,
      messageProto.leagueId,
      messageProto.transType,
      messageProto.amount,
      messageProto.status,
      messageProto.transactionId,
      messageProto.lastAccountBalance,
      replyTo)
  }
}
