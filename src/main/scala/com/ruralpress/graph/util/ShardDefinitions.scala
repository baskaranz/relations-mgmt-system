package com.ruralpress.graph.util

import akka.actor.ActorSystem
import com.ruralpress.graph.manager.EntityManager

class ShardDefinitions(implicit val actorSystem: ActorSystem) {

  val personManager = new ShardableEntity(10, "personManager", EntityManager.personManager, actorSystem)
  val businessManager = new ShardableEntity(10, "businessManager", EntityManager.businessManager, actorSystem)

}

object ShardDefinitions {

  private var definitions: Option[ShardDefinitions] = None

  def shards(implicit actorSystem: ActorSystem): ShardDefinitions = {
    definitions match {
      case Some(value) =>
        value
      case None =>
        val localDefinitions = new ShardDefinitions()
        definitions = Some(localDefinitions)
        localDefinitions
    }
  }

  def apply(implicit actorSystem: ActorSystem) = new ShardDefinitions()
}
