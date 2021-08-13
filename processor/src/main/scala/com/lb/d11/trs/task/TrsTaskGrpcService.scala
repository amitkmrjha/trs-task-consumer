package com.lb.d11.trs.task

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.util.Timeout
import com.lb.d11.trs.task.TrsTask.{Command, GetRunningTotal, RunningTotal}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class TrsTaskGrpcService(system: ActorSystem[_], shardRegion: ActorRef[Command]) extends TrsTaskService {

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def userStats(in: TrsTaskStatsRequest): Future[TrsTaskStatsResponse] = {
    shardRegion
      .ask[RunningTotal](replyTo => GetRunningTotal(in.userId, replyTo))
      .map(runningTotal => TrsTaskStatsResponse(in.userId, runningTotal.total))
  }
}
