package com.ruralpress.graph.entities

import java.util.UUID

object Common {

  trait Entity extends Serializable {

    def id: ID

    def detail: Detail

    def addSubscriptionTopic(edge: String, id: ID): Entity

    def removeSubscriptionTopic(edge: String, id: ID): Entity

    def subscriptionTopics: Map[String, Set[_ <: ID]]

    def removePublishTopic(edge: String, id: ID): Entity

    def addPublishTopic(edge: String, id: ID): Entity

    def publishTopics: Map[String, Set[_ <: ID]]
  }

  trait Detail

  trait Request

  trait Response

  trait SuccessResponse extends Response

  trait FailureResponse extends Response

  trait ErrorResponse extends Response

  class ID(val entityId: UUID) extends Serializable {

    override def equals(other: Any): Boolean = other match {
      case that: ID =>
        (that canEqual this) &&
          entityId == that.entityId
      case _ => false
    }

    def canEqual(other: Any): Boolean = other.isInstanceOf[ID]

    override def hashCode(): Int = {
      entityId.hashCode()
    }

    override def toString = s"ID($entityId)"


  }

  case class ErrorMessage(error: String) extends ErrorResponse

  final case class CreateNewEntity(id: ID, detail: Detail) extends Request

  final case class CreateEntity(entity: Entity) extends Request

  final case class CreateEntitySuccess(entity: Entity) extends SuccessResponse

  final case class CreateEntityError(entityName: String, message: ErrorMessage) extends ErrorResponse

  final case class GetEntity(id: ID) extends Request

  final case class GetEntitySuccess(entity: Entity) extends SuccessResponse

  final case class GetEntityError(entityName: String, message: ErrorMessage) extends ErrorResponse

  final case class GetEntityNotFound(entityName: String, message: ErrorMessage) extends ErrorResponse

  final case class DestroyEntity(id: ID) extends Request

  final case class DestroyEntitySuccess(entityName: Entity) extends SuccessResponse

  final case class DeleteEntityError(entityName: String, message: ErrorMessage) extends ErrorResponse

  final case class UpdateDetail(detail: Detail) extends Request

  final case class UpdateDetailSuccess(entity: Entity) extends SuccessResponse

  final case class EntityDetailUpdateError(entityName: String, message: ErrorMessage) extends ErrorResponse

  final case class AddEdge(node: ID, edgeLabel: String) extends Request

  final case class AddEdgeSuccess(entity: Entity) extends SuccessResponse

  final case class RemoveEdge(node: ID, edgeLabel: String) extends Request

  final case class RemoveEdgeSuccess(entity: Entity) extends SuccessResponse

  final case class Message(actorId: ID, request: Request) extends Request

}
