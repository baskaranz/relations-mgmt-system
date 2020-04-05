package com.ruralpress.graph.service

import java.util.UUID

import akka.actor.ActorSystem
import com.ruralpress.graph.entities.Common.{Detail, Entity, ID}
import com.ruralpress.graph.entities.person.{Person, PersonDetail, PersonId}
import com.ruralpress.graph.manager.EntityManagerResolver

class PersonService(val system: ActorSystem) extends EntityService {

  override def createEmptyEntity(entityId: UUID, detail: Detail): Person = {
    val personDetail = detail.asInstanceOf[PersonDetail]
    Person(PersonId(entityId), detail = personDetail)
  }

  override def getActorProps(implicit actorSystem: ActorSystem) = {
    EntityManagerResolver.props
  }

  override def updateEntity(entity: Entity, detail: Detail): Person = {
    val person = entity.asInstanceOf[Person]
    val personDetail = detail.asInstanceOf[PersonDetail]
    person.copy(detail = personDetail)
  }

  override def getAllListeners(entity: Entity): List[ID] = {
    val person = entity.asInstanceOf[Person]
    (person.publishTopics ++ person.subscriptionTopics).flatMap(_._2).toSet.toList
  }

}
