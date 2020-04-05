package com.ruralpress.graph.manager

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.ruralpress.graph.EntityPersistentActor
import com.ruralpress.graph.entities.Common.{AddEdge, CreateNewEntity, Message, RemoveEdge}
import com.ruralpress.graph.entities.business.{BusinessDetail, BusinessId}
import com.ruralpress.graph.entities.person.{PersonDetail, PersonId}
import com.ruralpress.graph.query.DSL
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class DSLTest extends TestKit(ActorSystem("relationship_management_system_test", TestConfig.config))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with MockitoSugar
  with DSL
  with ImplicitSender {

  implicit val ec = system.dispatcher
  implicit override val timeout = Timeout(10 seconds)
  val entityActor = system.actorOf(Props[EntityPersistentActor], "EntityActor")
  val actorReference = mock[ActorLookup]
  implicit val resolver = system.actorOf(EntityManagerResolver.props)

  val marty = PersonId(UUID.randomUUID)
  val wendy = PersonId(UUID.randomUUID)
  val ruth = PersonId(UUID.randomUUID)
  val saul_goodman = PersonId(UUID.randomUUID)
  val kim_wexler = PersonId(UUID.randomUUID)
  val elliot_alderson = PersonId(UUID.randomUUID)
  val darleen_alderson = PersonId(UUID.randomUUID)

  val byrde_foundation = BusinessId(UUID.randomUUID)
  val jmm_law = BusinessId(UUID.randomUUID)
  val allsafe_inc = BusinessId(UUID.randomUUID)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "DSLTest run" should {

    "create entities and establish relation between entities" in {

      entityActor ! CreateNewEntity(marty, PersonDetail("Marty", "marty@byrdefoundation.com"))
      entityActor ! CreateNewEntity(wendy, PersonDetail("Wendy", "stew@byrdefoundation.com"))
      entityActor ! CreateNewEntity(ruth, PersonDetail("Ruth", "ruth@byrdefoundation.com"))
      entityActor ! CreateNewEntity(saul_goodman, PersonDetail("Saul Goodman", "saul.goodman@jmm.com"))
      entityActor ! CreateNewEntity(kim_wexler, PersonDetail("Kim Wexler", "kim.wexler@jmm.com"))
      entityActor ! CreateNewEntity(elliot_alderson, PersonDetail("Elliot Alderson", "elliot.alderson@allsafe.com"))
      entityActor ! CreateNewEntity(darleen_alderson, PersonDetail("Darleen Alderson", "darleen.alderson@allsafe.com"))

      entityActor ! CreateNewEntity(byrde_foundation, BusinessDetail("Byrde Foundation", "info@byrdefoundation.com"))
      entityActor ! CreateNewEntity(jmm_law, BusinessDetail("JMM Law", "info@jmm.com"))
      entityActor ! CreateNewEntity(allsafe_inc, BusinessDetail("AllSafe Inc", "info@allsafe.com"))

      Thread.sleep(5000)

      resolver ! Message(marty, AddEdge(wendy, "relativeOf"))
      resolver ! Message(marty, AddEdge(ruth, "friendOf"))
      resolver ! Message(marty, AddEdge(saul_goodman, "relativeOf"))
      resolver ! Message(wendy, AddEdge(ruth, "friendOf"))

      resolver ! Message(marty, AddEdge(byrde_foundation, "worksAt"))
      resolver ! Message(wendy, AddEdge(byrde_foundation, "worksAt"))
      resolver ! Message(ruth, AddEdge(byrde_foundation, "worksAt"))

      resolver ! Message(saul_goodman, AddEdge(jmm_law, "worksAt"))
      resolver ! Message(saul_goodman, AddEdge(kim_wexler, "relativeOf"))

      resolver ! Message(elliot_alderson, AddEdge(darleen_alderson, "relativeOf"))
      resolver ! Message(elliot_alderson, AddEdge(allsafe_inc, "worksAt"))
      resolver ! Message(darleen_alderson, AddEdge(allsafe_inc, "worksAt"))

      Thread.sleep(5000)

    }

    "All relatives of Person(Marty)" in {
      val eventualResult = getBidirectionalRelationsOfAnEntity(marty, "relativeOf")
      val idSet = Await.result(eventualResult, 5 second)
      idSet.size should be(2)
    }

    "List the relatives of an every person who works at Business(byrde_foundation)" in {
      val eventualResult = getIncomingRelationsOfAnEntity(byrde_foundation, "employed")
        .flatMap(getBiDirectionalRelationshipOfEntities(_, "relativeOf"))
      val idSet = Await.result(eventualResult, 5 second)
      idSet.size should be(3)
    }

    "List all business with more than Z employees" in {
      val businessSet = Set(byrde_foundation, allsafe_inc, jmm_law)
      val eventualResult = getEntitiesHavingEdgeLimit(businessSet, "employed", 1)
      val idSet = Await.result(eventualResult, 5 second)
      idSet.size should be(2)
    }

    "List every person who has friends with employed relatives" in {
      val businessSet = Set(byrde_foundation, allsafe_inc, jmm_law)
      val eventualResult = getSubscribedRelationshipOfEntities(businessSet, "employed")
        .flatMap(getBiDirectionalRelationshipOfEntities(_, "relativeOf"))
        .flatMap(getBiDirectionalRelationshipOfEntities(_, "friendOf"))
      val idSet = Await.result(eventualResult, 5 second)
      idSet.size should be(1)
    }

  }
}

object TestConfig {
  val config = ConfigFactory.load()
}
