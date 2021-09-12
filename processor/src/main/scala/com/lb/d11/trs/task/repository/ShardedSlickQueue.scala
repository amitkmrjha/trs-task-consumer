package com.lb.d11.trs.task.repository

import akka.Done
import akka.actor.typed.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import com.lb.d11.trs.task.TrsTask.UserTrsTask
import com.lb.d11.trs.task.repository.ShardedSlickSession.ShardedDataBase
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes


trait ShardedSlickQueue[T] {
  def getQueue(userId:String):Option[SourceQueueWithComplete[T]]
}

class UserTaskQueue(system: ActorSystem[_]) extends ShardedSlickQueue[UserTrsTask] {

  implicit val ec = system.executionContext

  private val shardedSession = new ShardedSlickSession(system)

  private val userTaskQueuesMap:Map[ShardedDataBase,SourceQueueWithComplete[UserTrsTask]] = shardedSession.getSessions().map { e =>
    implicit val slickSession = e._2
    implicit val sys = system
    val bufferSize = 100
    e._1 -> createSourceQueue(e._1.name,bufferSize)
  }.toMap

  private def createSourceQueue(dbName: String,bufferSize: Int)(implicit system: ActorSystem[_],slickSession:SlickSession): SourceQueueWithComplete[UserTrsTask] ={
    Source
      .queue[UserTrsTask](bufferSize, OverflowStrategy.backpressure)
      .map(_.copy(status = dbName))
      .via(Slick.flowWithPassThrough { message =>
        toUserSql(message).map(_ => message)
      })
      .toMat(Sink.foreach(_.replyTo ! Done))(Keep.left)
      .named(s"slick-flow")
      .addAttributes(CinnamonAttributes.instrumented(reportByName = true))
      .run
  }

  private def toUserSql(user:UserTrsTask) (implicit slickSession:SlickSession) = {
    import slickSession.profile.api._
    sqlu"""INSERT INTO wallet VALUES(
        ${user.userId},
        ${user.roundId},
        ${user.leagueId},
        ${user.transType},
        ${user.amount},
        ${user.status},
        ${user.transactionId},
        ${user.lastAccountBalance}
        )ON CONFLICT (transaction_id) DO UPDATE SET lastAccountBalance = wallet.lastAccountBalance + 1"""
  }

  override def getQueue(userId: String):Option[SourceQueueWithComplete[UserTrsTask]] = {
    selectQueue(userId,selectDataBase)
  }

  private def selectQueue(userId: String,f:String => Option[ShardedDataBase] ):Option[SourceQueueWithComplete[UserTrsTask]] = {
    f(userId) flatMap {db =>
      val p: Option[SourceQueueWithComplete[UserTrsTask]] = userTaskQueuesMap.get(db)
      p
    }
  }

  private def selectDataBase(userId: String): Option[ShardedDataBase]  = {
    val tags = Vector.tabulate(4)(i => s"mysqldb${i+1}")
    val i = math.abs(userId.hashCode % tags.size)
    shardedSession.getDatabase.find(e => e.name.contains(tags(i)))
  }
}


