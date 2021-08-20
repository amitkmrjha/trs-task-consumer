package com.lb.d11.trs.task

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector, Scheduler}
import akka.util.Timeout
import com.lb.d11.trs.task.TrsTask.{Command, GetTrsTask, TrsTaskSummary}
import com.lb.d11.trs.task.repository.{ScalikeJdbcSession, WalletRepository}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TrsTaskGrpcService(system: ActorSystem[_], shardRegion: ActorRef[Command],walletRepository: WalletRepository) extends TrsTaskService {

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def userStats(in: TrsTaskStatsRequest): Future[TrsTaskStatsResponse] = {
    shardRegion
      .ask[TrsTaskSummary](replyTo => GetTrsTask(in.userId, replyTo))
      .map{summary =>
        val records = summary.topTask.map( e=> KafkaConsumerRecord(e.key,e.offset,e.partition,e.topic))
        TrsTaskStatsResponse(in.userId, summary.totalTask,records)
      }
  }

  override def trsTaskByUser(in: TrsTaskStatsRequest): Future[TrsTaskRecordResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        walletRepository.getTrsTaskByUserId(session, in.userId)
      }
    }(blockingJdbcExecutor).map { response =>
      val taskInfo: Seq[TrsTaskRecord] = response.map { info =>
        TrsTaskRecord(info.userId,info.roundId,info.transType,info.transType,
          info.amount,info.status,info.transactionId,info.lastAccountBalance)
      }
      TrsTaskRecordResponse(taskInfo)
    }
  }
}
