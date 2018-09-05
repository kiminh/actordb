package de.up.hpi.informationsystems.fouleggs.dactors

import akka.actor.{Actor => AkkaActor, ActorLogging, ActorRef, Props}
import de.up.hpi.informationsystems.adbms.Dactor
import de.up.hpi.informationsystems.adbms.protocols.DefaultMessagingProtocol.{InsertIntoRelation, SelectAllFromRelation}
import de.up.hpi.informationsystems.adbms.record.Record
import de.up.hpi.informationsystems.adbms.record.ColumnCellMapping._
import de.up.hpi.informationsystems.adbms.relation.Relation
import de.up.hpi.informationsystems.adbms.protocols.RequestResponseProtocol
import de.up.hpi.informationsystems.fouleggs.movieScoringService.movies.MovieActor

object AdminSession {

  final case object Up

  object AddCastToFilm {
    sealed trait AddCastToFilm extends RequestResponseProtocol.Message
    final case class Request(personId: Int, filmId: Int, roleName: String) extends RequestResponseProtocol.Request[AddCastToFilm]
    final case class Success(result: Relation) extends RequestResponseProtocol.Success[AddCastToFilm]
    final case class Failure(e: Throwable) extends RequestResponseProtocol.Failure[AddCastToFilm]
  }

  def props: Props = Props[AdminSession]
}

/**
  * Provides top level functionalities
  */
class AdminSession extends AkkaActor with ActorLogging {
  override def receive: Receive = commonBehaviour

  def commonBehaviour: Receive = {
    case AdminSession.Up => sender() ! akka.actor.Status.Success
    case AdminSession.AddCastToFilm.Request(personId, filmId, roleName) =>
      addCastToFilm(personId, filmId, roleName)
    case SelectAllFromRelation.Success(rel) => log.info(rel.toString)
  }

  def addCastToFilm(personId: Int, filmId: Int, roleName: String): Unit = {
    log.info(s"Adding person $personId as $roleName to film $filmId")
    val functor: ActorRef = context.system.actorOf(CastAndFilmographyFunctor.props(personId, filmId, roleName, self))
    context.become(waitingForSuccess(functor) orElse commonBehaviour)
  }

  def waitingForSuccess(from: ActorRef): Receive = {
    case akka.actor.Status.Success if sender == from => {
      context.become(commonBehaviour)
      log.info("Connected cast to film")

      val empire = Dactor.dactorSelection(context.system, classOf[MovieActor], 1)
      val mark = Dactor.dactorSelection(context.system, classOf[Actor], 1)

      empire ! SelectAllFromRelation.Request(MovieActor.Cast.name)
      mark ! SelectAllFromRelation.Request(Actor.Filmography.name)
    }
  }
}

object CastAndFilmographyFunctor {
  def props(personId: Int, filmId: Int, roleName: String, backTo: ActorRef): Props =
    Props(new CastAndFilmographyFunctor(personId, filmId, roleName, backTo))
}

class CastAndFilmographyFunctor(personId: Int, filmId: Int, roleName: String, backTo: ActorRef) extends AkkaActor {

  val sub1: ActorRef = context.system.actorOf(AddFilmFunctor.props(personId, filmId, roleName, self))
  val sub2: ActorRef = context.system.actorOf(AddCastFunctor.props(personId, filmId, roleName, self))

  override def receive: Receive = waitingForAck(Seq(sub1, sub2))

  def waitingForAck(pending: Seq[ActorRef]): Receive = {
    case akka.actor.Status.Success =>
      val remainingACKs = pending.filterNot(_ == sender())

      if(remainingACKs.isEmpty) {
        backTo ! akka.actor.Status.Success
        context.stop(self)
      } else {
        context.become(waitingForAck(remainingACKs))
      }

    case akka.actor.Status.Failure(e) =>
      backTo ! akka.actor.Status.Failure(e)
      context.stop(self)
  }
}

object AddFilmFunctor {
  def props(personId: Int, filmId: Int, roleName: String, backTo: ActorRef): Props =
    Props(new AddFilmFunctor(personId: Int, filmId: Int, roleName: String, backTo: ActorRef))
}

class AddFilmFunctor(personId: Int, filmId: Int, roleName: String, backTo: ActorRef) extends AkkaActor {

  override def receive: Receive = waitingForFilmInfo orElse commonBehaviour

  Dactor.dactorSelection(context.system, classOf[MovieActor], filmId) ! SelectAllFromRelation.Request("film_info")

  def waitingForFilmInfo: Receive = {
    case SelectAllFromRelation.Failure(e) => fail(e)
    case SelectAllFromRelation.Success(relation: Relation) =>
      val filmInfoOption: Option[Record] = relation.records.toOption match {
        case Some(records: Seq[Record]) => records.headOption
        case _ => None
      }
    filmInfoOption match {
      case None => fail(new RuntimeException("Received empty film info"))

      case Some(filmInfo: Record) =>
        val newFilmRecord: Record = Actor.Filmography.newRecord(
          Actor.Filmography.filmId ~> filmId &
            Actor.Filmography.roleName ~> roleName &
            Actor.Filmography.filmName ~> filmInfo(MovieActor.Info.title) &
            Actor.Filmography.filmRelease ~> filmInfo(MovieActor.Info.release)
        ).build()
        Dactor.dactorSelection(context.system, classOf[Actor], personId) ! InsertIntoRelation("filmography", Seq(newFilmRecord))
        context.become(waitingForInsertAck orElse commonBehaviour)
    }
  }

  def waitingForInsertAck: Receive = {
    case akka.actor.Status.Success =>
      backTo ! akka.actor.Status.Success
      context.stop(self) // because this is our last state
  }

  def commonBehaviour: Receive = {
    case akka.actor.Status.Failure(e) => fail(e)
  }

  private def fail(e: Throwable): Unit = {
    backTo ! akka.actor.Status.Failure(e)
    context.stop(self)
  }
}

object AddCastFunctor {
  def props(personId: Int, filmId: Int, roleName: String, backTo: ActorRef): Props =
    Props(new AddCastFunctor(personId: Int, filmId: Int, roleName: String, backTo: ActorRef))
}

class AddCastFunctor(personId: Int, filmId: Int, roleName: String, backTo: ActorRef) extends AkkaActor {

  override def receive: Receive = waitingForPersonInfo orElse commonBehaviour

  // very first message has to be sent outside of Receives
  Dactor.dactorSelection(context.system, classOf[Actor], personId) ! SelectAllFromRelation.Request("person_info")

  def waitingForPersonInfo: Receive = {
    case SelectAllFromRelation.Failure(e) => fail(e)
    case SelectAllFromRelation.Success(relation: Relation) => {
      val personInfoOption: Option[Record] = relation.records.toOption match {
        case Some(records: Seq[Record]) => records.headOption
        case _ => None
      }
      personInfoOption match {
        case None => fail(new RuntimeException("Received empty personInfo"))

        case Some(personInfo: Record) =>
          val newCastRecord: Record = MovieActor.Cast.newRecord(
            MovieActor.Cast.firstName ~> personInfo(Actor.Info.firstName) &
              MovieActor.Cast.lastName ~> personInfo(Actor.Info.lastName) &
              MovieActor.Cast.roleName ~> roleName &
              MovieActor.Cast.personId ~> personId
          ).build()
          Dactor.dactorSelection(context.system, classOf[MovieActor], filmId) ! InsertIntoRelation("film_cast", Seq(newCastRecord))
          context.become(waitingForInsertAck orElse commonBehaviour)
      }
    }
  }

  def waitingForInsertAck: Receive = {
    case akka.actor.Status.Success =>
      backTo ! akka.actor.Status.Success
      context.stop(self)
  }

  def commonBehaviour: Receive = {
    case akka.actor.Status.Failure(e) => fail(e)
  }

  private def fail(e: Throwable): Unit = {
    backTo ! akka.actor.Status.Failure(e)
    context.stop(self)
  }
}