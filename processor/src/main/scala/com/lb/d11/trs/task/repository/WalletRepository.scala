package com.lb.d11.trs.task.repository

import com.lb.d11.trs.task.TrsTaskProjectionHandler.{ConsumerRecordInfo, TaskInfo}
import scalikejdbc._

trait WalletRepository {
  def update(session: ScalikeJdbcSession, userId: String, taskInfo: TaskInfo,
             consumerRecordInfo: ConsumerRecordInfo): Unit
  def getTrsTaskByUserId(session: ScalikeJdbcSession, userId: String): Seq[TaskInfo]
}


class WalletRepositoryImpl() extends WalletRepository {

  override def update(session: ScalikeJdbcSession, userId: String, taskInfo: TaskInfo,
                      consumerRecordInfo: ConsumerRecordInfo): Unit = {
    session.db.withinTx { implicit dbSession =>
      // This uses the PostgreSQL `ON CONFLICT` feature
      // Alternatively, this can be implemented by first issuing the `UPDATE`
      // and checking for the updated rows count. If no rows got updated issue
      // the `INSERT` instead.
      sql"""INSERT INTO wallet VALUES(
        ${taskInfo.userId},
        ${taskInfo.roundId},
        ${taskInfo.leagueId},
        ${taskInfo.transType},
        ${taskInfo.amount},
        ${taskInfo.status},
        ${taskInfo.transactionId},
        ${taskInfo.lastAccountBalance}
        ) ON DUPLICATE KEY UPDATE lastAccountBalance = wallet.lastAccountBalance + 1""".executeUpdate().apply()
    }

  }

  override def getTrsTaskByUserId(session: ScalikeJdbcSession, userId: String): Seq[TaskInfo] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>
        selectByUser(userId)
      }
    } else {
      session.db.readOnly { implicit dbSession =>
        selectByUser(userId)
      }
    }
  }
  private def selectByUser(userId: String)(implicit dbSession: DBSession): Seq[TaskInfo] = {
    sql"SELECT * FROM wallet WHERE user_id = $userId"
      .map(result =>
        TaskInfo(
          result.string("user_id"),
          result.string("round_id"),
          result.string("league_id"),
          result.string("trs_type"),
          result.int("amount"),
          result.string("trs_status"),
          result.string("transaction_id"),
          result.int("lastAccountBalance"),
        )
      ).toList().apply()
  }
}
