package com.lb.d11.trs.task

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.OverflowStrategy
import com.lb.d11.trs.task.TrsTask.UserTrsTask
import slick.backend.DatabaseConfig
import slick.jdbc.JdbcProfile
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future

class  SlickPostgres(system: ActorSystem[_]) {
  //implicit val session = SlickSession.forConfig("slick-postgres")

  val jdbcQueue:SourceQueueWithComplete[UserTrsTask] = {
   // import session.profile.api._

    val bufferSize = 1000
    implicit val sys = system

    /*Source
      .queue[UserTrsTask](bufferSize, OverflowStrategy.fail)
      .toMat(
        Slick.sink(user => sqlu"""INSERT INTO wallet VALUES(
        ${user.userId},
        ${user.roundId},
        ${user.leagueId},
        ${user.transType},
        ${user.amount},
        ${user.status},
        ${user.transactionId},
        ${user.lastAccountBalance}
        )""")
      )(Keep.left)
      .run*/


    Source
      .queue[UserTrsTask](bufferSize, OverflowStrategy.fail)
      .toMat(
        Sink.ignore
      )(Keep.left)
      .run
  }

}
