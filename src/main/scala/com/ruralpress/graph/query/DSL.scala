package com.ruralpress.graph.query

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.ruralpress.graph.entities.Common._
import com.ruralpress.graph.manager.RouteRequest

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait DSL {
  self =>

  implicit val timeout: akka.util.Timeout = Timeout(2 seconds)

  /**
    * Given a set of entityIds of type ID, relationship edge name and limit, the method returns all related Ids
    * matching the relation edge name
    *
    * @param entities set of Entity IDs
    * @param edgeName Relationship name
    * @param limit    The limit Int value
    * @tparam EntityIDType
    * @tparam EntityType
    * @return
    */
  protected def getEntitiesHavingEdgeLimit[EntityIDType <: ID, EntityType <: Entity](
                                                                                      entities: Set[EntityIDType],
                                                                                      edgeName: String,
                                                                                      limit: => Int)
                                                                                    (implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val seqSet: Seq[Set[_ <: ID]] = Seq()
    val set: Set[ID] = Set()

    val intermediate = entities.map {
      entity =>
        val eventualOptionEntity = getEntity[EntityType](GetEntity(entity))
        val eventualSeq = eventualOptionEntity.map {
          x => x.map(y => getEntityWithIncomingEdgeLimit(y, edgeName, limit))
        }.map(either => either.toSeq.flatMap(another => another.toSeq))
        eventualSeq
    }
    val futSeq = Future.sequence(intermediate)

    val result = futSeq.map(s => s.foldLeft(seqSet)(_ ++ _)).map(s => s.foldLeft(set)(_ ++ _))

    result

  }

  /**
    * This method checks if an Entity/Resource has more than a given relationship limit
    * Incoming relation is the subscribed topics of an entity/resource
    *
    * @param entity
    * @param edgeName Relationship name e.g. "employed"
    * @param limit    The limit to check
    * @return
    */
  private def getEntityWithIncomingEdgeLimit(entity: Entity,
                                             edgeName: String,
                                             limit: Int
                                            ): Either[ErrorMessage, Set[_ <: ID]] = {

    val subscribedTopics = entity.subscriptionTopics

    val result = subscribedTopics.get(edgeName) match {
      case Some(ids) =>
        if (ids.size > limit)
          Right(Set(entity.id))
        else Left(ErrorMessage(s"Exception - No relationship for edgeName: $edgeName entity: ${entity.id.entityId}"))
      case None => Left(ErrorMessage(s"Exception - No relationship for edgeName: $edgeName entity: ${entity.id.entityId}"))
    }
    result
  }

  /**
    * This method returns an entity/resource from cluster
    * type parameterised of type Entity, e.g. Person or Business
    */
  protected def getEntity[EntityType <: Entity](request: GetEntity)
                                               (implicit resolver: ActorRef,
                                                ec: ExecutionContext): Future[Either[ErrorMessage, EntityType]] = {
    val routeRequest = RouteRequest(request.id, request)
    val eventualEntity = (resolver ? routeRequest).mapTo[Response]
    val entity = eventualEntity.map {
      case success: GetEntitySuccess =>
        Right(success.entity.asInstanceOf[EntityType])

      case error: GetEntityError => Left(ErrorMessage(error.message.error))
      case _ => Left(ErrorMessage("Unknown Error"))
    }
    entity

  }

  /**
    * Given a set of entitiesId of type ID and relationship edge name, the method returns all related Ids
    * matching the relation edge name
    *
    * @param entities
    * @param edgeName
    * @tparam EntityIDType
    * @tparam EntityType
    * @return
    */
  protected def getBiDirectionalRelationshipOfEntities[EntityIDType <: ID, EntityType <: Entity](
                                                                                                  entities: Set[EntityIDType],
                                                                                                  edgeName: String)
                                                                                                (implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val seqSet: Seq[Set[_ <: ID]] = Seq()
    val set: Set[ID] = Set()

    val intermediate = entities.map {
      entity =>
        val eventualOptionEntity = getEntity[EntityType](GetEntity(entity))
        val eventualSeq = eventualOptionEntity.map {
          x => x.map(y => getInOutRelationsOfEntity(y, edgeName))
        }.map(either => either.toSeq.flatMap(another => another.toSeq))
        eventualSeq
    }
    val futSeq = Future.sequence(intermediate)

    val result = futSeq.map(s => (s.foldLeft(seqSet)(_ ++ _))).map(s => s.foldLeft(set)(_ ++ _))

    result

  }

  /**
    * Get bidirectional relations between entities
    *
    * @param id       id of the entity
    * @param edgeName Relationship name
    * @param resolver
    * @param ec
    * @tparam EntityIDType
    * @tparam EntityType
    * @return
    */
  protected def getBidirectionalRelationsOfAnEntity[EntityIDType <: ID, EntityType <: Entity](
                                                                                               id: EntityIDType,
                                                                                               edgeName: String)
                                                                                             (implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val eventualOptionEntity = getEntity[EntityType](GetEntity(id))

    val eventualSeq = eventualOptionEntity.map {
      either => either.map(y => getInOutRelationsOfEntity(y, edgeName))
    }

    val futureIdSets = eventualSeq.map(either => either.toSeq.flatMap(another => another.toSeq))
    val emptySet: Set[ID] = Set()
    val result = futureIdSets.map(s => (s.foldLeft(emptySet)(_ ++ _)))

    result

  }

  /**
    * This method returns related entity Ids given a single entity/resource
    *
    * @param entity
    * @param edgeName Relationship name i.e. friendOf
    * @return
    */
  private def getInOutRelationsOfEntity(entity: Entity,
                                        edgeName: String): Either[ErrorMessage, Set[_ <: ID]] = {

    //subscription topics are incoming relationships
    val subscribedTopics = entity.subscriptionTopics

    //publishTopics are outgoing relationships
    val publishedTopics = entity.publishTopics

    val setValuesAddition = new MergeMonoid[Set[_ <: ID]] {
      def op(a1: Set[_ <: ID], a2: Set[_ <: ID]): Set[_ <: ID] = a1 ++ a2

      def zero: Set[_ <: ID] = Set()
    }

    val topicsMerger: MergeMonoid[Map[String, Set[_ <: ID]]] = mapMerger(setValuesAddition)

    val mergedTopics = topicsMerger.op(subscribedTopics, publishedTopics)

    val result = mergedTopics.get(edgeName) match {
      case Some(ids) => {
        Right(ids)
      }
      case None => Left(ErrorMessage(s"No relationship - edgeName: $edgeName entity: ${entity.id.entityId}"))
    }
    result
  }

  /**
    * Merging subscription topics and publish topics safely
    *
    * @param V
    * @tparam K
    * @tparam V
    * @return
    */
  private def mapMerger[K, V](V: MergeMonoid[V]): MergeMonoid[Map[K, V]] =
    new MergeMonoid[Map[K, V]] {
      def zero = Map[K, V]()

      def op(a: Map[K, V], b: Map[K, V]) =
        (a.keySet ++ b.keySet).foldLeft(zero) { (acc, k) =>
          acc.updated(k, V.op(a.getOrElse(k, V.zero),
            b.getOrElse(k, V.zero)))
        }
    }

  /**
    * This method returns all the entity ids matching subscribed topics of an entityId
    *
    * @param id       BusinessId
    * @param edgeName relationship name, e.g. in case of business, it can be "employed"
    * @param resolver EntityManagerResolver actor reference
    * @param ec
    * @tparam EntityIDType
    * @tparam EntityType
    * @return
    */
  protected def getIncomingRelationsOfAnEntity[EntityIDType <: ID, EntityType <: Entity](
                                                                                          id: EntityIDType,
                                                                                          edgeName: String)
                                                                                        (implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val eventualOptionEntity = getEntity[EntityType](GetEntity(id))

    val eventualSeq = eventualOptionEntity.map {
      either => either.map(y => getIncomingRelationsOfEntity(y, edgeName))
    }

    val futureIdSets = eventualSeq.map(either => either.toSeq.flatMap(another => another.toSeq))
    val emptySet: Set[ID] = Set()
    val result = futureIdSets.map(s => (s.foldLeft(emptySet)(_ ++ _)))

    result

  }

  /**
    * Incoming relation is the subscribed topics of an entity/resource
    * e.g if an entity establishes a relation with another entity, then the initiator has this info in publish topics
    * and another entity has that info in subscribed topics
    *
    * @param entity
    * @param edgeName
    * @return
    */
  private def getIncomingRelationsOfEntity(entity: Entity,
                                           edgeName: String): Either[ErrorMessage, Set[_ <: ID]] = {

    val subscribedTopics = entity.subscriptionTopics

    val result = subscribedTopics.get(edgeName) match {
      case Some(ids) => {
        Right(ids)
      }
      case None => Left(ErrorMessage(s"Exception - No given relationship edgeName: $edgeName entity: ${entity.id.entityId}"))
    }
    result
  }

  /**
    * This method gives back the all relatives of an entity
    *
    * @param id       The entity whose relatives is to be found
    * @param entities Pass All entities for checking
    * @param edgeName relationship name
    * @param resolver EntityManagerResolver actor
    * @param ec       Execution Ctx
    * @tparam EntityIDType of type ID
    * @tparam EntityType   of type Entity
    * @return
    */
  protected def relationsOfEntity[EntityIDType <: ID, EntityType <: Entity](id: EntityIDType, entities: Set[EntityIDType],
                                                                            edgeName: String)(implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val eventualOptionEntity = getEntity[EntityType](GetEntity(id))

    val eventualSeq = eventualOptionEntity.map {
      either => either.map(y => getOutgoingRelationsOfEntity(y, edgeName))
    }

    val futureIdSets = eventualSeq.map(either => either.toSeq.flatMap(another => another.toSeq))
    val emptySet: Set[ID] = Set()
    val idsPublished = futureIdSets.map(s => (s.foldLeft(emptySet)(_ ++ _)))

    val idsSubscribed = getSubscribedRelationshipOfEntities(entities, edgeName)

    val result = for {
      pubIds <- idsPublished
      subIds <- idsSubscribed
    } yield pubIds ++ subIds

    result

  }

  /**
    * Outgoing relation is the publish topics of an entity/resource
    *
    * @param entity
    * @param edgeName
    * @return
    */
  private def getOutgoingRelationsOfEntity(entity: Entity,
                                           edgeName: String): Either[ErrorMessage, Set[_ <: ID]] = {

    val publishedTopics = entity.publishTopics

    val result = publishedTopics.get(edgeName) match {
      case Some(ids) => {
        Right(ids)
      }
      case None => Left(ErrorMessage(s"No relationship edgeName: $edgeName entity: ${entity.id.entityId}"))
    }
    result
  }

  /**
    * Given a set of entitiesId of type ID and relationship edge name, the method returns all related Ids
    * matching the relation edge name
    *
    * @param entities set of ID
    * @param edgeName relationship name
    * @tparam EntityIDType
    * @tparam EntityType
    * @return
    */
  protected def getSubscribedRelationshipOfEntities[EntityIDType <: ID, EntityType <: Entity](
                                                                                                 entities: Set[EntityIDType],
                                                                                                 edgeName: String)
                                                                                             (implicit resolver: ActorRef, ec: ExecutionContext): Future[Set[ID]] = {

    val seqSet: Seq[Set[_ <: ID]] = Seq()
    val set: Set[ID] = Set()

    val intermediate = entities.map {
      entity =>
        val eventualOptionEntity = getEntity[EntityType](GetEntity(entity))
        val eventualSeq = eventualOptionEntity.map {
          x => x.map(y => getIncomingRelationsOfEntity(y, edgeName))
        }.map(either => either.toSeq.flatMap(another => another.toSeq))
        eventualSeq
    }
    val futSeq = Future.sequence(intermediate)

    val result = futSeq.map(s => (s.foldLeft(seqSet)(_ ++ _))).map(s => s.foldLeft(set)(_ ++ _))

    result

  }

  sealed trait MergeMonoid[A] {
    def op(a1: A, a2: A): A

    def zero: A
  }


  implicit class QueryDSLOps[EntityIDType <: ID, EntityType <: Entity](a: Future[Set[ID]])(implicit resolver: ActorRef,
                                                                                           ec: ExecutionContext) {
    def getBiDirectionalRelationshipOfEntities(edgeName: String): Future[Set[ID]] =
      a.flatMap(self.getBiDirectionalRelationshipOfEntities(_, edgeName))

    def getSubscribedRelationshipOfEntities(edgeName: String): Future[Set[ID]] =
      a.flatMap(self.getSubscribedRelationshipOfEntities(_, edgeName))

    def getEntitiesHavingEdgeLimit(edgeName: String, limit: Int): Future[Set[ID]] =
      a.flatMap(self.getEntitiesHavingEdgeLimit(_, edgeName, limit))

    def relationsOfEntity(id: EntityIDType, edgeName: String): Future[Set[ID]] =
      a.flatMap(self.relationsOfEntity(id, _, edgeName))
  }

}

