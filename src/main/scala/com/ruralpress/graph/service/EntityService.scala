package com.ruralpress.graph.service

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import com.ruralpress.graph.entities.Common.{Detail, Entity, ID}

trait EntityService {

  def updateEntity(entity: Entity, detail: Detail): Entity

  def createEmptyEntity(entityId: UUID, detail: Detail): Entity

  def getActorProps(implicit actorSystem: ActorSystem): Props

  def getAllListeners(entity: Entity): List[ID]

}
