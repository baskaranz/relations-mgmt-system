package com.ruralpress.graph.util

import akka.actor.{Actor, ActorLogging}
import akka.persistence.PersistentActor
import com.ruralpress.graph.entities.Common.{ID, Request}
import com.ruralpress.graph.manager.EntityManager

import scala.concurrent.duration._

trait EnsembleActor extends PersistentActor {

  context.setReceiveTimeout(120.seconds)
  var wrappedReceive: Receive = {
    case x => unhandled(x)
  }

  def wrappedBecome(r: Receive) = {
    wrappedReceive = r
  }

  def receiveCommand: Receive = {
    case x => if (wrappedReceive.isDefinedAt(x)) wrappedReceive(x) else unhandled(x)
  }
}

/**
  * Handles the publishing and subscribing of messages
  */
trait PubSubManager extends EnsembleActor with ActorLogging {
  self: EntityManager =>
  override def receive: Actor.Receive = {

    case AddPublishTopic(edge, id) =>
      log.info(s"Adding publish topic id: $id entity: $persistentEntity")
      persistentEntity = persistentEntity.addPublishTopic(edge, id)
      saveSnapshot(persistentEntity)

    case RemovePublishTopic(edge, id) =>
      log.debug(s"Removing publish topic id: $id entity: $persistentEntity")
      persistentEntity = persistentEntity.removePublishTopic(edge, id)
      saveSnapshot(persistentEntity)

    case AddSubscriptionTopic(edge, id) =>
      log.info(s"Adding subscription topic id: $id entity: $persistentEntity")
      persistentEntity = persistentEntity.addSubscriptionTopic(edge, id)
      saveSnapshot(persistentEntity)

    case RemoveSubscriptionTopic(edge, id) =>
      log.debug(s"Removing subscription topic id: $id entity: $persistentEntity")
      persistentEntity = persistentEntity.removeSubscriptionTopic(edge, id)
      saveSnapshot(persistentEntity)

    case x => {
      super.receive(x)
    }
  }
}

case class AddPublishTopic(edge: String, id: ID) extends Request

case class RemovePublishTopic(edge: String, id: ID) extends Request

case class AddSubscriptionTopic(edge: String, id: ID) extends Request

case class RemoveSubscriptionTopic(edge: String, id: ID) extends Request
