package com.ruralpress.graph.manager

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.ruralpress.graph.entities.Common.{ID, Message, Request}
import com.ruralpress.graph.entities.business.BusinessId
import com.ruralpress.graph.entities.person.PersonId
import com.ruralpress.graph.util.ShardDefinitions

import scala.concurrent.duration._
import scala.language.postfixOps

object EntityManagerResolver {

  def props(implicit actorSystem: ActorSystem): Props = {
    Props(new EntityManagerResolver(new ActorLookup()))
  }

}

/**
  * A EntityManagerResolver is an actor for EntityManager actors' lookup
  *
  * @param actorReference
  */
class EntityManagerResolver(actorReference: ActorLookup) extends Actor with ActorLogging {


  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(5 seconds)
  implicit val actorSystem = context.system


  def receive = {

    case message: Message =>

      val recipientActor = actorReference.actorLookup(message.actorId)
      log.info(s"request message: $message to actor: $recipientActor")
      recipientActor forward message

    case request: RouteRequest =>
      val recipientId = request.id
      val recipientActor: ActorRef = actorReference.actorLookup(recipientId)
      val recipientRequest = request.request
      val message = Message(recipientId, recipientRequest)
      recipientActor.tell(message, sender)

    case _ =>
  }
}

/**
  * This is a delegate class that's used to determine the appropriate
  * recipient for messages.
  *
  * @param actorSystem actorSystem for the cluster
  */
class ActorLookup(implicit actorSystem: ActorSystem) {

  val shards = ShardDefinitions.shards
  val businessManager = shards.businessManager.shardStart
  val personManager = shards.personManager.shardStart


  def actorLookup(id: ID): ActorRef = {
    id match {
      case _: BusinessId => businessManager
      case _: PersonId => personManager
    }
  }

}

case class RouteRequest(id: ID, request: Request)