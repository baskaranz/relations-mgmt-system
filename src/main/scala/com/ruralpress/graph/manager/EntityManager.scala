package com.ruralpress.graph.manager

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence._
import akka.util.Timeout
import com.ruralpress.graph.entities.Common._
import com.ruralpress.graph.manager.EntityManagerProtocol.DestroySelf
import com.ruralpress.graph.service.{BusinessService, EntityService, PersonService}
import com.ruralpress.graph.util._

import scala.concurrent.duration._
import scala.language.postfixOps


object EntityManager {

  def personManager(implicit system: ActorSystem) = Props(new EntityManager(new PersonService(system)))

  def businessManager(implicit system: ActorSystem) = Props(new EntityManager(new BusinessService(system)))

}

/**
  * An EntityManager is an actor for managing all operations
  * with Entity.  It's primary functions are:
  * Create, Read, Update and Destruction.
  *
  * @param entityService The entityService to associate with EntityManager
  */
class EntityManager(var entityService: EntityService) extends PersistentActor
  with ActorLogging
  with PubSubManager {
  var entityId: Option[ID] = None
  protected var persistentEntity: Entity = null
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher
  implicit val actorSystem = context.system
  protected var snapshotMetaData: SnapshotMetadata = null
  context.setReceiveTimeout(120.seconds)

  def receiveRecover = {

    case SnapshotOffer(metadata, entitySnapshot: Entity) =>

      if (entitySnapshot != null) {
        this.persistentEntity = entitySnapshot
      } else {
        log.error("Exception - EntitySnapshot is null")
      }
      this.snapshotMetaData = metadata
    case entity: Entity =>
      this.persistentEntity = entity
    case RecoveryCompleted =>
      if (persistentEntity == null) {
        log.error(s"Exception -  Recovery completed with Null persistentEntity. persistenceId: $persistenceId entityId: $entityId")
      }
  }

  context.setReceiveTimeout(2 minutes)

  override def persistenceId = s"${self.path.name}"

  wrappedReceive = {

    case request: Message =>
      entityId match {
        case None =>
          this.entityId = Some(request.actorId)
        case Some(_) =>
      }
      self forward request.request

    case request: CreateEntity =>
      persistentEntity = request.entity
      saveSnapshot(persistentEntity)
      sender ! CreateEntitySuccess(persistentEntity)

    case request: GetEntity =>
      if ((null == persistentEntity) && (entityId.isEmpty)) {
        log.error("Failed to get entity. persistentEntity is null and no entityId")
        sender ! GetEntityError(request.id.getClass.toString, ErrorMessage(s"Exception - persistentEntity is null. " +
          s"request: $request entityId: $entityId."))

      } else if (null == persistentEntity && entityId.isDefined) {
        log.error(s"Failed to get entity. persistentEntity is null and entityId: ${entityId}")
        sender ! GetEntityError(request.id.getClass.toString, ErrorMessage(s"Exception - persistentEntity is null. " +
          s"request: $request entityId: $entityId"))
      }
      else if (persistentEntity.id == request.id) {
        sender() ! GetEntitySuccess(persistentEntity)
      } else {
        sender() ! GetEntityError(request.id.getClass.toString, ErrorMessage("Exception - Requested id does not match actual " +
          "entity id"))
      }

    case request: GetEntitySuccess =>
      log.info(s"request: $request sender: ${sender()}")

    case request: GetEntityError =>
      log.error(s"Unable to add as a subscriber to parent of entity: ${request.entityName}")

    case request: DestroyEntity =>
      log.info(s"request: $request sender: ${sender()}")

      val subscribers = persistentEntity.publishTopics
      subscribers.keySet.map {
        key =>
          subscribers.get(key).map {
            subSet =>
              subSet.foreach {
                entityResource =>
                  val entityResolver = context.actorOf(EntityManagerResolver.props)
                  entityResolver ! Message(entityResource, RemoveSubscriptionTopic(EdgesConfig.getRelationPath(key), persistentEntity.id))
              }
          }
      }

      val publishers = persistentEntity.subscriptionTopics
      publishers.keySet.map {
        key =>
          publishers.get(key).map {
            publisherSubSet =>
              publisherSubSet.foreach {
                entityResource =>
                  val entityResolver = context.actorOf(EntityManagerResolver.props)
                  entityResolver ! Message(entityResource, RemovePublishTopic(key, persistentEntity.id))
              }
          }
      }

      self forward DestroySelf

    case request: UpdateDetail =>
      this.persistentEntity = entityService.updateEntity(persistentEntity, request.detail)
      saveSnapshot(this.persistentEntity)
      sender ! UpdateDetailSuccess(persistentEntity)


    /**
      * Establish an edge from an entity to recipient entity
      */
    case request: AddEdge =>
      log.info(s"request: $request sender: ${sender()}")
      this.persistentEntity = persistentEntity.addPublishTopic(request.edgeLabel, request.node)
      saveSnapshot(this.persistentEntity)

      val subscriberResolver = context.actorOf(EntityManagerResolver.props)
      subscriberResolver ! Message(request.node, AddSubscriptionTopic(EdgesConfig.getRelationPath(request.edgeLabel), persistentEntity.id))
      sender ! AddEdgeSuccess(persistentEntity)

    case request: RemoveEdge =>
      log.info(s"request: $request sender: ${sender()}")
      this.persistentEntity = persistentEntity.removePublishTopic(request.edgeLabel, request.node)
      saveSnapshot(this.persistentEntity)

      val subscriberResolver = context.actorOf(EntityManagerResolver.props)
      subscriberResolver ! Message(request.node, RemoveSubscriptionTopic(EdgesConfig.getRelationPath(request.edgeLabel), persistentEntity.id))
      sender ! AddEdgeSuccess(persistentEntity)

    case request: SaveSnapshotSuccess =>
      log.info(s"request: $request sender: ${sender()}")
      this.snapshotMetaData = request.metadata

    case request: ReceiveTimeout =>
      log.info(s"request: $request sender: ${sender()}")
      context.parent ! Passivate(stopMessage = Symbol("stop"))

    case Symbol("stop") =>
      context.stop(self)

    case DestroySelf =>
      log.info(s"sender:${sender()}")
      deleteSnapshot(snapshotMetaData.sequenceNr)
      sender ! DestroyEntitySuccess(persistentEntity)
      self ! PoisonPill

  }


}

object EntityManagerProtocol {

  case object DestroySelf

}