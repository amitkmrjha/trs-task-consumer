package com.lb.d11.trs.task

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.{FlowShape, OverflowStrategy}
import com.lb.d11.trs.task.TrsTask.UserTrsTask
import slick.backend.DatabaseConfig
import slick.jdbc.JdbcProfile
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future

class  SlickPostgres(system: ActorSystem[_]) {
   implicit val ec = system.executionContext

  implicit val session = SlickSession.forConfig("slick-postgres")

  val jdbcQueue = {
    import session.profile.api._

    val bufferSize = 100

    implicit val sys = system

    Source
      .queue[UserTrsTask](bufferSize, OverflowStrategy.backpressure)
      .via(Slick.flowWithPassThrough { message =>
        toUserSql(message).map(_ => message)
      })
      .toMat(Sink.foreach(_.replyTo ! Done))(Keep.left)
      .named("jdbc-flow")
      .addAttributes(CinnamonAttributes.instrumented(reportByName = true))
      .run

  }

  def toUserSql(user:UserTrsTask) = {
    import session.profile.api._
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

}