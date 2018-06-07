package de.up.hpi.informationsystems.sampleapp.dactors

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import de.up.hpi.informationsystems.adbms.Dactor
import de.up.hpi.informationsystems.adbms.definition.ColumnCellMapping._
import de.up.hpi.informationsystems.adbms.definition._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Cart {

  def props(id: Int): Props = Props(new Cart(id))

  object AddItems {
    case class Order(inventoryId: Int, sectionId: Int, quantity: Int)

    // orders: item_id, i_quantity
    case class Request(orders: Seq[Order], customerId: Int)
    case class Success(sessionId: Int)
    case class Failure(e: Throwable)
  }

  object Checkout {
    case class Request(sessionId: Int)
    case class Success(amount: Double)
    case class Failure(e: Throwable)
  }

  object CartInfo extends RelationDef {
    val customerId: ColumnDef[Int] = ColumnDef("c_id")
    val storeId: ColumnDef[Int] = ColumnDef("store_id")
    val sessionId: ColumnDef[Int] = ColumnDef("session_id")

    override val columns: Set[UntypedColumnDef] = Set(customerId, storeId, sessionId)
    override val name: String = "cart_info"
  }

  object CartPurchases extends RelationDef {
    val sectionId: ColumnDef[Int] = ColumnDef("sec_id")
    val sessionId: ColumnDef[Int] = ColumnDef("session_id")
    val inventoryId: ColumnDef[Int] = ColumnDef("i_id")
    val quantity: ColumnDef[Int] = ColumnDef("i_quantity")
    val fixedDiscount: ColumnDef[Double] = ColumnDef("i_fixed_disc")
    val minPrice: ColumnDef[Double] = ColumnDef("i_min_price")
    val price: ColumnDef[Double] = ColumnDef("i_price")

    override val columns: Set[UntypedColumnDef] =
      Set(sectionId, sessionId, inventoryId, quantity, fixedDiscount, minPrice, price)
    override val name: String = "cart_purchases"
  }

  private object AddItemsHelper {
    def apply(system: ActorSystem, backTo: ActorRef, askTimeout: Timeout): AddItemsHelper =
      new AddItemsHelper(system, backTo, askTimeout)

    case class Success(results: Seq[Record], newSessionId: Int, replyTo: ActorRef)
    case class Failure(e: Throwable, replyTo: ActorRef)

  }

  private class AddItemsHelper(system: ActorSystem, recipient: ActorRef, implicit val askTimeout: Timeout) {
    import de.up.hpi.informationsystems.sampleapp.dactors.Cart.AddItems.Order

    def help(orders: Seq[Order], customerId: Int, currentSessionId: Int, replyTo: ActorRef): Unit = {

      val priceRequests = orders
        .groupBy(_.sectionId)
        .map{ case (sectionId, sectionOrders) =>
          sectionId -> StoreSection.GetPrice.Request(sectionOrders.map(_.inventoryId))
        }
      val priceList: FutureRelation = Dactor.askDactor[StoreSection.GetPrice.Success](system, classOf[StoreSection], priceRequests)
      // FutureRelation: i_id, i_price, i_min_price

      val groupIdRequest = Map(customerId -> Customer.GetCustomerGroupId.Request())
      val groupId: FutureRelation = Dactor
        .askDactor[Customer.GetCustomerGroupId.Success](system, classOf[Customer], groupIdRequest)

      val fixedDiscount: FutureRelation = groupId.flatTransform( groupId => {
        val id = groupId.records.get.head.get(Customer.CustomerInfo.custGroupId).get
        val fixedDiscountRequest = Map(id -> GroupManager.GetFixedDiscounts.Request(orders.map(_.inventoryId)))
        Dactor.askDactor(system, classOf[GroupManager], fixedDiscountRequest)
      })
      // FutureRelation: i_id, fixed_disc

      val priceDisc: FutureRelation = priceList.innerJoin(fixedDiscount, (priceRec, discRec) =>
        priceRec.get(CartPurchases.inventoryId) == discRec.get(CartPurchases.inventoryId)
      )
      // FutureRelation: i_id, i_price, i_min_price, fixed_disc

      val orderRecordBuilder = Record(Set(CartPurchases.inventoryId, CartPurchases.sectionId, CartPurchases.quantity))
      val orderRelation = FutureRelation.fromRecordSeq(Future{orders.map(order => orderRecordBuilder(
        CartPurchases.inventoryId ~> order.inventoryId &
          CartPurchases.sectionId ~> order.sectionId &
          CartPurchases.quantity ~> order.quantity
      ).build())})
      val priceDiscOrder: FutureRelation = priceDisc.innerJoin(orderRelation, (priceRec, orderRec) =>
        priceRec.get(CartPurchases.inventoryId) == orderRec.get(CartPurchases.inventoryId)
      )
      // FutureRelation: i_id, i_price, i_min_price, fixed_disc, sec_id, i_quantity

      val result = FutureRelation.fromRecordSeq(priceDiscOrder.future.map(_.records.get.map( (rec: Record) => rec + (CartPurchases.sessionId -> currentSessionId))))
      // FutureRelation: i_id, i_price, i_min_price, fixed_disc, sec_id, i_quantity, session_id

      result.pipeAsMessageTo(relation => AddItemsHelper.Success(relation.records.get, currentSessionId, replyTo), recipient)
    }
  }
}

class Cart(id: Int) extends Dactor(id) {
  import Cart._

  private val timeout: Timeout = Timeout(2.seconds)

  override protected val relations: Map[RelationDef, MutableRelation] =
    Dactor.createAsRowRelations(Seq(CartInfo, CartPurchases))

  private var currentSessionId = 0

  override def receive: Receive = {
    case AddItems.Request(orders, customerId) => {
      currentSessionId += 1
      relations(CartInfo)
        .update(CartInfo.sessionId ~> currentSessionId)
        .where[Int](CartInfo.customerId -> { _ == customerId })
      AddItemsHelper(context.system, self, timeout).help(orders, customerId, currentSessionId, sender())
    }

    case AddItemsHelper.Success(records, newSessionId, replyTo) =>
      relations(CartPurchases).insertAll(records) match {
        case Success(_) => replyTo ! AddItems.Success(newSessionId)
        case Failure(e) => replyTo ! AddItems.Failure(e)
      }

    case Checkout.Request(_) => sender() ! Checkout.Failure(new NotImplementedError)
  }
}
