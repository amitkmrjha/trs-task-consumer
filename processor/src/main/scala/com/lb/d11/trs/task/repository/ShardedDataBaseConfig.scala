package com.lb.d11.trs.task.repository
import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigObject, ConfigValue}

import scala.jdk.CollectionConverters._
final case class ShardedDataBase(name:String)
final case class ShardedDataBaseConfig(dataBase: ShardedDataBase, config: Config)
object ShardedDataBaseConfig {
  def toShardedDataBase(config:Config): Seq[ShardedDataBaseConfig] = {
    config.getConfig("sharddb.jdbc-connection-settings").root().asScala.map {
      case (k: String, v: ConfigValue) =>
        val config = v.asInstanceOf[ConfigObject].toConfig
        ShardedDataBaseConfig(ShardedDataBase(k), config)
    }.toSeq
  }
}
