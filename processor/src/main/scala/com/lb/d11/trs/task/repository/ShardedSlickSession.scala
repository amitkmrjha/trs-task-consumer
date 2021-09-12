package com.lb.d11.trs.task.repository

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.typesafe.config.{Config, ConfigObject, ConfigValue}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.jdk.CollectionConverters._


class ShardedSlickSession(system: ActorSystem[_]) {

  import com.lb.d11.trs.task.repository.ShardedSlickSession._

    private val ShardedSession = initFromConfig(system.settings.config)
    system.whenTerminated.map { _ =>
      ShardedSession.values.map(session => session.close())
    }(scala.concurrent.ExecutionContext.Implicits.global)

  private def initFromConfig(config: Config): Map[ShardedDataBase,SlickSession] = {
    val shardedDataBaseConfigs = ShardedDBConfig.toShardedDataBase(config)
    shardedDataBaseConfigs.map{sdb =>
      val slickSession = SlickSession.forConfig(s"${shardbConfigPath}.${sdb.dataBase.name}")
      sdb.dataBase -> slickSession
    }.toMap
  }
  def getSessions():Seq[(ShardedDataBase,SlickSession)] = {
    ShardedSession.toSeq
  }

  def getDatabase = ShardedSession.keys.toSeq


}

object ShardedSlickSession {

  val shardbConfigPath = "sharddb.slick-mysql"

  final case class ShardedDataBase(name:String)

  final case class ShardedDBConfig(dataBase: ShardedDataBase, config: Config)
  object ShardedDBConfig {
    def toShardedDataBase(config:Config): Seq[ShardedDBConfig] = {
      config.getConfig(shardbConfigPath).root().asScala.map {
        case (k: String, v: ConfigValue) =>
          val config = v.asInstanceOf[ConfigObject].toConfig
          ShardedDBConfig(ShardedDataBase(k), config)
      }.toSeq
    }
  }
}
