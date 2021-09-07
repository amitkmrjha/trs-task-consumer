package com.lb.d11.trs.task

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector, Scheduler}
import akka.util.Timeout
import com.lb.d11.trs.task.repository.{ScalikeJdbcSession, WalletRepository}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TrsTaskGrpcService(system: ActorSystem[_],walletRepository: WalletRepository) extends TrsTaskService {

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

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
