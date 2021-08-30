package com.lb.d11.trs.task

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.external.ExternalShardAllocationStrategy
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import akka.serialization.SerializationExtension
import com.google.common.collect.EvictingQueue

import java.util
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object TrsTask {

  val entityIdentifier = "trs-task"

  final case class State(totalTask: Int,
                         topTask:util.Queue[ConsumerRecordInfo] = EvictingQueue.create(10))extends CborSerializable{
    def update(taskInfo: TaskInfo,consumerRecordInfo: ConsumerRecordInfo):State = {
     topTask.add(consumerRecordInfo)
      State(totalTask+1,topTask)
    }
  }
  object State {
    def emptyInit =
      State(0)
  }

  final case class EntityTag(name: String, shardDBId: Int,projectionId: Int)
  object EntityTag{
    private val fieldSeprator = '|'
    val tags = Vector.tabulate(4)(shardDBId => s"$entityIdentifier$fieldSeprator$shardDBId")flatMap { e =>
      Vector.tabulate(2) ( projectionId => s"$e$fieldSeprator$projectionId")
    }

    def toEntityTag(tagString: String) : EntityTag = {
      val fields = tagString.split(fieldSeprator)
      EntityTag(fields(0),fields(1).toInt,fields(2).toInt)
    }

  }


  def init(system: ActorSystem[_], settings: ProcessorSettings): Future[ActorRef[Command]] = {
    import system.executionContext
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Command) => msg.userId,
      settings = settings.kafkaConsumerSettings()
    ).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey) { entityContext =>
          val i = math.abs(entityContext.entityId.hashCode % EntityTag.tags.size)
          val selectedTag = EntityTag.tags(i)
          TrsTask(entityContext.entityId,selectedTag,settings)
        }
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, settings.entityTypeKey.name))
          .withMessageExtractor(messageExtractor))
    })
  }



  sealed trait Command extends CborSerializable {
    def userId: String
  }

  final case class AddTrsTask(userId:String,taskInfo: TaskInfo,
                              consumerRecordInfo: ConsumerRecordInfo,replyTo: ActorRef[Done]) extends Command

  final case class TaskInfo(userId: String, roundId: String, leagueId: String, transType: String,
                            amount: Int, status: String, transactionId: String, lastAccountBalance: Int)
    extends CborSerializable

  final case class ConsumerRecordInfo(key:String, offset: Long,partition: Int,topic: String )
    extends CborSerializable

  final case class GetTrsTask(userId: String, replyTo: ActorRef[TrsTaskSummary]) extends Command

  final case class TrsTaskSummary(totalTask: Int,
                                  topTask:List[ConsumerRecordInfo]) extends CborSerializable


  sealed trait Event extends CborSerializable {
    def userId: String
  }

  final case class TrsTaskAdded(userId: String, taskInfo: TaskInfo,
                                consumerRecordInfo: ConsumerRecordInfo) extends Event

  def apply(userId: String,projectionTag: String,settings: ProcessorSettings): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(settings.entityTypeKey.name, userId),
        emptyState = State.emptyInit,
        commandHandler =
          (state, command) => handleCommand(userId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1)
      )
  }

  private def handleCommand( userId: String,
                             state: State,
                             command: Command): ReplyEffect[Event, State] = {
    processTrsTask(state, command)
  }

  private def processTrsTask(state: State,command: Command): ReplyEffect[Event, State] = {
    command match {
      case x:AddTrsTask=>
          Effect
            .persist(
              TrsTaskAdded(x.userId,x.taskInfo,x.consumerRecordInfo)
            ).thenReply(x.replyTo) { task => Done}
      case GetTrsTask(userId,replyTo) =>
        val trsTaskSummary = TrsTaskSummary(state.totalTask,
          new util.ArrayList[ConsumerRecordInfo](state.totalTask).asScala.toList
        )
        Effect.reply(replyTo)(trsTaskSummary)
    }
  }

  private def handleEvent(state: State, event: Event): State = {
    event match {
      case x:TrsTaskAdded=>
        state.update(x.taskInfo,x.consumerRecordInfo)
    }
  }


}
