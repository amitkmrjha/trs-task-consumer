package com.lb.d11.trs.task

import java.lang.{ Long => JLong }
import org.apache.kafka.clients.consumer.ConsumerRecord

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess

import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.kafka.scaladsl.KafkaSourceProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{MergeableOffset, ProjectionBehavior, ProjectionId}
import com.lb.d11.trs.task.repository.{ScalikeJdbcSession, WalletRepository}

object TrsTaskProjection {

  def init(
            system: ActorSystem[_],
            processorSettings: ProcessorSettings,
            repository: WalletRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "TrsTaskProjection",
      TrsTaskProjectionHandler.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system,processorSettings, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   processorSettings: ProcessorSettings,
                                   repository: WalletRepository,
                                   index: Int): ExactlyOnceProjection[MergeableOffset[JLong], ConsumerRecord[String, Array[Byte]]] = {
    val tag = TrsTaskProjectionHandler.tags(index)
    val sourceProvider: SourceProvider[MergeableOffset[JLong], ConsumerRecord[String, Array[Byte]]] =
      KafkaSourceProvider(system, processorSettings.kafkaConsumerSettings(), processorSettings.topics.toSet)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("TrsTaskProjection", tag),
      sourceProvider,
      handler = () =>
        new TrsTaskProjectionHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}
