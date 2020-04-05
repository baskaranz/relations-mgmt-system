package com.ruralpress.graph.service

import java.util.UUID

import akka.actor.ActorSystem
import com.ruralpress.graph.entities.Common.{Detail, Entity, ID}
import com.ruralpress.graph.entities.business.{Business, BusinessDetail, BusinessId}
import com.ruralpress.graph.manager.EntityManagerResolver

class BusinessService(val system: ActorSystem) extends EntityService {

  override def createEmptyEntity(entityId: UUID, detail: Detail): Business = {
    val businessDetail = detail.asInstanceOf[BusinessDetail]
    Business(BusinessId(entityId), detail = businessDetail)
  }

  override def getActorProps(implicit actorSystem: ActorSystem) = {
    EntityManagerResolver.props
  }

  override def updateEntity(entity: Entity, detail: Detail): Business = {
    val business = entity.asInstanceOf[Business]
    val businessDetail = detail.asInstanceOf[BusinessDetail]
    business.copy(detail = businessDetail)
  }

  override def getAllListeners(entity: Entity): List[ID] = {
    val business = entity.asInstanceOf[Business]
    (business.publishTopics ++ business.subscriptionTopics).flatMap(_._2).toList
  }

}
