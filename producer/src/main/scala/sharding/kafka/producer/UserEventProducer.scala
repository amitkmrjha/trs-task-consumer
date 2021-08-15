package sharding.kafka.producer

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import com.lb.d11.trs.task.serialization.trs_task_events.TrsTaskMessage
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object UserEventProducer extends App {

  implicit val system: ActorSystem = ActorSystem(
    "UserEventProducer",
    ConfigFactory.parseString("""
      akka.actor.provider = "local" 
     """.stripMargin).withFallback(ConfigFactory.load()).resolve())

  val log = Logging(system, "UserEventProducer")

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val producerConfig = ProducerConfig(system.settings.config.getConfig("kafka-to-sharding-producer"))

  val producerSettings: ProducerSettings[String, Array[Byte]] =
    ProducerSettings(config, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(producerConfig.bootstrapServers)

  val nrUsers = 200
  val maxRound = 50
  val maxLeague = 50
  val amountMax = 50000

  val done: Future[Done] =
    Source
      .tick(1.second, 1.second, "tick")
      .map(_ => {
        val randomEntityId = Random.nextInt(nrUsers).toString
        val round = Random.nextInt(maxRound).toString
        val league = Random.nextInt(maxLeague).toString
        val trsType = "Wallet"
        val amount = Random.nextInt(amountMax)
        val status = "Success"
        val tId = java.util.UUID.randomUUID.toString
        val balance = 0;
        val message = TrsTaskMessage(randomEntityId, round,league, trsType, amount,status,tId,balance).toByteArray
        log.info("Sending message to user {}", randomEntityId)
        // rely on the default kafka partitioner to hash the key and distribute among shards
        // the logic of the default partitioner must be replicated in MessageExtractor entityId -> shardId function
        new ProducerRecord[String, Array[Byte]](producerConfig.topic, randomEntityId, message)
      })
      .runWith(Producer.plainSink(producerSettings))
}
