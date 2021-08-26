package com.lb.d11.trs.task.repository

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.{ConnectionPool, DataSourceCloser, DataSourceConnectionPool}
import scalikejdbc.config.DBs
import scalikejdbc.config.NoEnvPrefix
import scalikejdbc.config.TypesafeConfig
import scalikejdbc.config.TypesafeConfigReader

object ScalikeShardJdbcSetup {

  /**
   * Initiate the ScalikeJDBC connection pool configuration and shutdown.
   * The DataSource is setup with ActorSystem's config.
   *
   * The connection pool will be closed when the actor system terminates.
   */
  def init(system: ActorSystem[_]): Unit = {
    initFromConfig(system.settings.config)
    system.whenTerminated.map { _ =>
      ConnectionPool.closeAll()
    }(scala.concurrent.ExecutionContext.Implicits.global)

  }

  /**
   * Builds a Hikari DataSource with values from jdbc-connection-settings.
   * The DataSource is then configured as the 'default' connection pool for ScalikeJDBC.
   */
  private def initFromConfig(config: Config): Unit = {

    val dbs = new DBsFromConfig(config)
    dbs.loadGlobalSettings()

    val dataSourceSeq = buildShardedDataSource(
      ShardedDataBaseConfig.toShardedDataBase(config))
    dataSourceSeq.foreach{dataSourceKV =>
      val name = dataSourceKV._1
      val dataSource = dataSourceKV._2
      val dataSourceConnectionPool = new DataSourceConnectionPool(
        dataSource = dataSource,
        closer = HikariCloser(dataSource))
      ConnectionPool.add(name,dataSourceConnectionPool)
    }
  }
  private def buildShardedDataSource(shardedDataBases: Seq[ShardedDataBaseConfig]): Map[ShardedDataBase,HikariDataSource] = {
    shardedDataBases.map{sharded =>
      val dataSource = new HikariDataSource()

      dataSource.setPoolName("read-side-connection-pool")
      dataSource.setMaximumPoolSize(
        sharded.config.getInt("connection-pool.max-pool-size"))

      val timeout = sharded.config.getDuration("connection-pool.timeout").toMillis
      dataSource.setConnectionTimeout(timeout)

      dataSource.setDriverClassName(sharded.config.getString("driver"))
      dataSource.setJdbcUrl(sharded.config.getString("url"))
      dataSource.setUsername(sharded.config.getString("user"))
      dataSource.setPassword(sharded.config.getString("password"))

      sharded.dataBase -> dataSource
    }.toMap
  }

  /**
   * This is only needed to allow ScalikeJdbc to load its logging configurations from the passed Config
   */
  private class DBsFromConfig(val config: Config)
      extends DBs
      with TypesafeConfigReader
      with TypesafeConfig
      with NoEnvPrefix

  /**
   * ScalikeJdbc needs a closer for the DataSource to delegate the closing call.
   */
  private case class HikariCloser(dataSource: HikariDataSource)
      extends DataSourceCloser {
    override def close(): Unit = dataSource.close()
  }

}
