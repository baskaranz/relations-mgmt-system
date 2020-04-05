package com.ruralpress.graph.entities.business

import java.util.UUID

import com.ruralpress.graph.entities.Common.{Detail, Entity, ID}

case class Business(id: BusinessId,
                    detail: BusinessDetail,
                    subscriptionTopics: Map[String, Set[ID]] = Map.empty,
                    publishTopics: Map[String, Set[ID]] = Map.empty) extends Entity {

  /**
    * Subscription topics holds the incoming relationships
    * @param edge Relationship name, e.g. friendOf, worksAt, relationOf
    * @param id entity id to add
    * @return
    */
  override def addSubscriptionTopic(edge: String, id: ID): Entity = {
    val newTopics = subscriptionTopics.contains(edge) match {
      case true   => Map(edge -> (subscriptionTopics.get(edge).getOrElse(Set()) ++ Set(id)))
      case false  => subscriptionTopics ++ Map(edge -> Set(id))
    }
    this.copy(subscriptionTopics = newTopics)
  }



  /**
    * Removes the topic when remove subscription topic message is received
    * @param edge Relationship name, e.g. friendOf, worksAt, relationOf
    * @param id entity id to remove
    * @return
    */
  override def removeSubscriptionTopic(edge: String, id: ID): Entity = {
    val newTopics = Map(edge -> (subscriptionTopics.get(edge).getOrElse(Set()) -- Set(id)))
    this.copy(subscriptionTopics = newTopics)
  }

  /**
    * Adds info to this entity's publish topics
    * @param edge Relationship name, e.g. friendOf, worksAt, relationOf
    * @param id entity id to add
    * @return
    */
  override def addPublishTopic(edge: String, id: ID): Entity = {
    val newTopics = publishTopics.contains(edge) match {
      case true   => Map(edge -> (publishTopics.get(edge).getOrElse(Set()) ++ Set(id)))
      case false  => publishTopics ++ Map(edge -> Set(id))
    }
    this.copy(publishTopics = newTopics)
  }

  /**
    * Removes info from this entity's publish topics
    * @param edge Relationship name, e.g. friendOf, worksAt, relationOf
    * @param id entity id to remove
    * @return
    */
  override def removePublishTopic(edge: String, id: ID): Entity = {
    val newTopics = Map(edge -> (publishTopics.get(edge).getOrElse(Set()) -- Set(id)))
    this.copy(publishTopics = newTopics)
  }

}

case class BusinessDetail(name: String, email: String) extends Detail

case class BusinessId(override val entityId: UUID) extends ID(entityId)

object BusinessId {
  def create(entityId: UUID): BusinessId = BusinessId(UUID.randomUUID())
}