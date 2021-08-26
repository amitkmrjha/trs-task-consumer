package com.lb.d11.trs.task.repository

import akka.japi.function.Function
import akka.projection.jdbc.JdbcSession
import scalikejdbc._

import java.sql.Connection

object ScalikeJdbcSession {
  def withSession[R](f: ScalikeJdbcSession => R)(implicit dataBase: ShardedDataBase): R = {
    val session = new ScalikeJdbcSession()
    try {
      f(session)
    } finally {
      session.close()
    }
  }
}

/**
 * Provide database connections within a transaction to Akka Projections.
 */
final class ScalikeJdbcSession()(implicit dataBase: ShardedDataBase) extends JdbcSession {
  val db: DB = DB.connect(conn = ConnectionPool.borrow(dataBase))
  db.autoClose(false)

  override def withConnection[Result](
      func: Function[Connection, Result]): Result = {
    db.begin()
    db.withinTxWithConnection(func(_))
  }

  override def commit(): Unit = db.commit()

  override def rollback(): Unit = db.rollback()

  override def close(): Unit = db.close()
}
