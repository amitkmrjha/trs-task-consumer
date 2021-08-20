package com.lb.d11.trs.task

import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import com.lb.d11.trs.task.TrsTask.TrsTaskAdded
import com.lb.d11.trs.task.repository.{ScalikeJdbcSession, WalletRepository}
import org.slf4j.LoggerFactory

class TrsTaskProjectionHandler( tag: String,
                                system: ActorSystem[_],
                                repository: WalletRepository)
  extends JdbcHandler[EventEnvelope[TrsTask.Event],ScalikeJdbcSession]() {
  private val log = LoggerFactory.getLogger(getClass)
  override def process(
                        session: ScalikeJdbcSession,
                        envelope: EventEnvelope[TrsTask.Event]): Unit = {
    envelope.event match {
      case x:TrsTaskAdded =>
        repository.update(session, x.userId, x.taskInfo,x.consumerRecordInfo)
        logTrsTaskAdded(x)
      case _ =>
    }
  }

  private def logTrsTaskAdded( trsTask:TrsTaskAdded ): Unit = {
    val cri = trsTask.consumerRecordInfo
    log.info(s"TrsTaskProjectionHandler is adding event to repository  for user id ${trsTask.userId}" +
      s" Kafka key ${cri.key} offset ${cri.offset} partition ${cri.partition} topic ${cri.topic}")
  }

}