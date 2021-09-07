package com.lb.d11.trs.task

import akka.actor.typed.ActorSystem
import akka.projection.jdbc.scaladsl.JdbcHandler
import com.lb.d11.trs.task.repository.{ScalikeJdbcSession, WalletRepository}
import com.lb.d11.trs.task.serialization.TrsTaskMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

class TrsTaskProjectionHandler( tag: String,
                                system: ActorSystem[_],
                                repository: WalletRepository)
  extends JdbcHandler[ConsumerRecord[String, Array[Byte]],ScalikeJdbcSession]() {

  import com.lb.d11.trs.task.TrsTaskProjectionHandler._

  private val log = LoggerFactory.getLogger(getClass)
  override def process(
                        session: ScalikeJdbcSession,
                        envelope: ConsumerRecord[String, Array[Byte]]): Unit = {
    val messageProto = TrsTaskMessage.parseFrom(envelope.value())
    val taskInfo = TaskInfo(
      messageProto.userId,
      messageProto.roundId,
      messageProto.leagueId,
      messageProto.transType,
      messageProto.amount,
      messageProto.status,
      messageProto.transactionId,
      messageProto.lastAccountBalance)
    val recordInfo = ConsumerRecordInfo(envelope.key(),envelope.offset(),envelope.partition(),envelope.topic())
    repository.update(session, taskInfo.userId, taskInfo,recordInfo)
    logTrsTaskAdded(recordInfo)

  }

  private def logTrsTaskAdded( cri:ConsumerRecordInfo ): Unit = {
    log.info(s"t[${cri.topic}] k[${cri.key}] o[${cri.offset}] p[${cri.partition}]")
  }

}

object TrsTaskProjectionHandler {
  val tags = Vector.tabulate(10)(i => s"trs-task-$i")
  final case class TaskInfo(userId: String, roundId: String, leagueId: String, transType: String,
                            amount: Int, status: String, transactionId: String, lastAccountBalance: Int)
    extends CborSerializable

  final case class ConsumerRecordInfo(key:String, offset: Long,partition: Int,topic: String )
    extends CborSerializable
}

