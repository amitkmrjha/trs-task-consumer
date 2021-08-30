package com.lb.d11.trs.task

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import com.lb.d11.trs.task.repository.{ShardedDataBase, ScalikeJdbcSession, WalletRepository}

object TrsTaskProjection {

  def init(
            system: ActorSystem[_],
            repository: WalletRepository,
            shardedDataBases:Seq[ShardedDataBase]): Unit = {
    ShardedDaemonProcess(system).init(
      name = "TrsTaskProjection",
      TrsTask.EntityTag.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index,shardedDataBases)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   repository: WalletRepository,
                                   index: Int,
                                   shardedDataBases:Seq[ShardedDataBase]): ExactlyOnceProjection[Offset, EventEnvelope[TrsTask.Event]] = {
    val tag = TrsTask.EntityTag.tags(index)
    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[TrsTask.Event]] =
      EventSourcedProvider.eventsByTag[TrsTask.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    val shardDBId = TrsTask.EntityTag.toEntityTag(tag).shardDBId

    implicit  val dataBase =
      shardedDataBases.find(p => p.name.contains((shardDBId+1).toString)).getOrElse(shardedDataBases(shardDBId))

    system.log.info(s"tag ${tag} will get proceesed in shard data base ${dataBase.name}")

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("TrsTaskProjection", tag),
      sourceProvider,
      handler = () =>
        new TrsTaskProjectionHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}
