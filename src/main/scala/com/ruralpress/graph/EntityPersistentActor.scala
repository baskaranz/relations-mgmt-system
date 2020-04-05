package com.ruralpress.graph

import akka.actor.ActorLogging
import akka.pattern.ask
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import akka.util.Timeout
import com.ruralpress.graph.EntityDirectorProtocol._
import com.ruralpress.graph.entities.Common._
import com.ruralpress.graph.entities.business.{Business, BusinessDetail, BusinessId}
import com.ruralpress.graph.entities.person.{Person, PersonDetail, PersonId}
import com.ruralpress.graph.manager.EntityManagerResolver
import com.ruralpress.graph.service.{BusinessService, PersonService}
import com.ruralpress.graph.util.EnsembleActor

import scala.language.postfixOps
import scala.util.{Failure, Success}


object EntityDirectorProtocol {

  trait Event

  final case class GetEntitiesSuccess(entities: Set[_ <: ID]) extends SuccessResponse

  final case class GetEntitiesFailure(error: ErrorResponse) extends FailureResponse

  final case class AddPerson(personId: PersonId, email: String) extends Event

  final case class RemovePerson(personId: PersonId) extends Event

  final case class AddBusiness(businessId: BusinessId, email: String) extends Event

  final case class RemoveBusiness(businessId: BusinessId) extends Event

  final case class FindPerson(email: String) extends Request

  final case class FindBusiness(email: String) extends Request

  case object GetPersons

  case object GetBusinesses

}


/**
  * A EntityPersistentActor stores only the entities Id for lookups
  */
class EntityPersistentActor extends PersistentActor with EnsembleActor with ActorLogging {

  import scala.concurrent.duration._

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher
  implicit val actorSystem = context.system

  context.setReceiveTimeout(120.seconds)
  var entities: EntitiesById = EntitiesById()
  protected var personService = new PersonService(actorSystem)
  protected var businessService = new BusinessService(actorSystem)

  override def persistenceId = s"EntityActor"

  def receiveRecover = {
    case event: Event =>
      updateState(event)
    case SnapshotOffer(_, personsSnapshot: EntitiesById) =>
      entities = personsSnapshot
  }

  def updateState(event: Event) = {
    event match {
      case AddPerson(personId, email) =>
        val byPersonsId = entities.byPersonsId + (personId -> email)
        val byPersonsEmail = entities.byPersonsEmail + (email -> personId)
        entities = EntitiesById(byPersonsId, byPersonsEmail, entities.byBusinessId, entities.byBusinessEmail)

      case RemovePerson(personId) =>
        val personEmail = entities.byPersonsId.get(personId)
        personEmail foreach {
          emailId =>
            val byPersonsId = entities.byPersonsId - personId
            val byPersonsEmail = entities.byPersonsEmail - emailId
            entities = EntitiesById(byPersonsId, byPersonsEmail, entities.byBusinessId, entities.byBusinessEmail)
        }

      case AddBusiness(businessId, email) =>
        val byBusinessId = entities.byBusinessId + (businessId -> email)
        val byBusinessEmail = entities.byBusinessEmail + (email -> businessId)
        entities = EntitiesById(entities.byPersonsId, entities.byPersonsEmail, byBusinessId, byBusinessEmail)

      case RemoveBusiness(businessId) =>
        val businessEmail = entities.byBusinessId.get(businessId)
        businessEmail foreach {
          emailId =>
            val byBusinessId = entities.byBusinessId - businessId
            val byBusinessEmail = entities.byBusinessEmail - emailId
            entities = EntitiesById(entities.byPersonsId, entities.byPersonsEmail, byBusinessId, byBusinessEmail)
        }
    }
  }

  wrappedReceive = {

    case message: Message =>
      self forward message.request

    /**
      * The EntityPersistentActor handles creating new entities.
      * CreateNewEntity message contains the Entity Detail
      */
    case request: CreateNewEntity =>
      val requester = sender()

      request.detail match {
        case personDetail: PersonDetail =>
          val resolver = context.actorOf(EntityManagerResolver.props)
          val personId = request.id.asInstanceOf[PersonId]
          val person = Person(personId, detail = personDetail)
          val email = person.detail.email
          val byPersonsId = entities.byPersonsId + (person.id -> email)
          val byPersonsEmail = entities.byPersonsEmail + (email -> person.id)
          entities = EntitiesById(byPersonsId, byPersonsEmail, entities.byBusinessId, entities.byBusinessEmail)
          persist(AddPerson(person.id, email))(updateState)
          resolver forward Message(person.id, CreateEntity(person))


        case businessDetail: BusinessDetail =>
          val resolver = context.actorOf(EntityManagerResolver.props)
          val businessId = request.id.asInstanceOf[BusinessId]
          val business = Business(businessId, detail = businessDetail)
          val email = business.detail.email
          val byBusinessId = entities.byBusinessId + (business.id -> email)
          val byBusinessEmail = entities.byBusinessEmail + (email -> business.id)
          entities = EntitiesById(entities.byPersonsId, entities.byPersonsEmail, byBusinessId, byBusinessEmail)
          persist(AddBusiness(business.id, email))(updateState)
          resolver forward Message(business.id, CreateEntity(business))

        case x => sender ! CreateEntityError("Unknown", ErrorMessage("Detail doesn't match with any existing entity detail"))
      }

    /**
      * Handles returning all the Person entities id, the entity id is just like : EntityName(UUID)
      */
    case GetPersons =>
      sender ! GetEntitiesSuccess(entities.byPersonsId.keySet)

    /**
      * Handles returning all Business entities
      */
    case GetBusinesses =>
      sender ! GetEntitiesSuccess(entities.byBusinessId.keySet)

    /**
      * Returns the respective entity by ID passed into the message GetEntity(ID)
      */
    case request: GetEntity =>
      request.id match {
        case personId: PersonId =>
          if (entities.byPersonsId.contains(personId)) {
            val entityResolver = context.actorOf(EntityManagerResolver.props)
            entityResolver forward Message(personId, request)
          } else {
            sender ! GetEntityError(Person.getClass.getSimpleName, ErrorMessage("Person Does not Exist"))
          }

        case businessId: BusinessId =>
          if (entities.byBusinessId.contains(businessId)) {
            val entityResolver = context.actorOf(EntityManagerResolver.props)
            entityResolver forward Message(businessId, request)
          } else {
            sender ! GetEntityError(Business.getClass.getSimpleName, ErrorMessage("Business Does not Exist"))
          }
      }

    /**
      * Finds a person by email passed into this message
      */
    case request: FindPerson =>
      log.info(s"FindPerson: $request")

      val email = request.email
      val personId = entities.byPersonsEmail.get(email)
      personId match {
        case Some(id) =>
          context.actorOf(EntityManagerResolver.props) forward Message(id, GetEntity(id))

        case None =>
          sender ! GetEntityNotFound(Person.getClass.getSimpleName, ErrorMessage(s"Person with email=$email"))
      }

    /**
      * Finds a business by email passed into this message
      */
    case request: FindBusiness =>
      log.info(s"FindBusiness: $request")

      val email = request.email
      val businessId = entities.byBusinessEmail.get(email)
      businessId match {
        case Some(id) =>
          context.actorOf(EntityManagerResolver.props) forward Message(id, GetEntity(id))

        case None =>
          sender ! GetEntityNotFound(Business.getClass.getSimpleName, ErrorMessage(s"Business with email=$email"))
      }

    /**
      * Destroys the persistent entity actor
      * Based on which type of entity Id is sent in this DestroyEntity message
      */
    case request: DestroyEntity =>

      val replyTo = sender()

      request.id match {
        case personId: PersonId =>
          val matches = entities.byPersonsId filter {
            case (existingId, email) => existingId == personId
          }
          if (matches.size == 0) {
            replyTo ! DeleteEntityError("Person", ErrorMessage("Person does not exist"))
          }
          matches foreach {
            case (existingId, email) =>
              val entityResolver = context.actorOf(EntityManagerResolver.props)
              val eventualResponse = (entityResolver ? Message(existingId, request)).mapTo[Response]
              eventualResponse onComplete {
                case Success(response) =>
                  response match {
                    case success: DestroyEntitySuccess =>
                      persist(RemovePerson(personId))(updateState)
                      saveSnapshot(entities)
                      replyTo ! success
                    case error: DeleteEntityError => replyTo ! error
                    case x => replyTo ! x

                  }
                case Failure(e) =>
                  replyTo ! Failure(e)
              }
          }

        case businessId: BusinessId =>
          val matches = entities.byBusinessId filter {
            case (existingId, email) => existingId == businessId
          }
          if (matches.size == 0) {
            replyTo ! DeleteEntityError("Business", ErrorMessage("Business does not exist"))
          }
          matches foreach {
            case (existingId, email) =>
              val entityResolver = context.actorOf(EntityManagerResolver.props)
              val futureResponse = (entityResolver ? Message(existingId, request)).mapTo[Response]
              futureResponse onComplete {
                case Success(response) =>
                  response match {
                    case success: DestroyEntitySuccess =>
                      persist(RemoveBusiness(businessId))(updateState)
                      saveSnapshot(entities)
                      replyTo ! success
                    case error: DeleteEntityError => replyTo ! error
                    case x => replyTo ! x

                  }
                case Failure(e) =>
                  replyTo ! Failure(e)
              }
          }

        case x =>
          sender() ! DeleteEntityError(x.getClass.getSimpleName, ErrorMessage("Exception - Cannot destroy entity"))
      }

    case saveSnapShotSuccess: SaveSnapshotSuccess =>

    case saveSnapShotFailure: SaveSnapshotFailure =>

    case x => log.info(s"Received unhandled message: $x")

  }


}

// Note: this redundant Maps exists to provide O(1) operations for entityId lookups by id or email
// It is a tradeoff in memory for performance
case class EntitiesById(byPersonsId: Map[PersonId, String] = Map.empty,
                        byPersonsEmail: Map[String, PersonId] = Map.empty,
                        byBusinessId: Map[BusinessId, String] = Map.empty,
                        byBusinessEmail: Map[String, BusinessId] = Map.empty)

