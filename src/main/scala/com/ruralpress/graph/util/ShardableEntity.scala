package com.ruralpress.graph.util

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.ruralpress.graph.entities.Common.{Message, Request}

class ShardableEntity(numberOfShards: Int, shardType: String, actorProps: Props, implicit val actorSystem: ActorSystem) {

  val idExtractor: ShardRegion.ExtractEntityId = {

    case requestMessage: Message => {
      val id = requestMessage.actorId
      val uuid = id.entityId
      val extractedId = (uuid).toString
      (extractedId, requestMessage)
    }
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case requestMessage: Message => {
      val id = math.abs(requestMessage.actorId.entityId.getMostSignificantBits % 30)
      (id.toString)
    }
  }

  val shardStart = ClusterSharding(actorSystem).start(shardType, actorProps,
      ClusterShardingSettings(actorSystem), idExtractor, shardResolver)

  val shardRef = ClusterSharding(actorSystem).shardRegion(shardType)

  def extractRequest(requestMessage: Message): Request = {
    requestMessage.request match {
      case message: Message => extractRequest(message)
      case x => requestMessage.request
    }
  }

}
