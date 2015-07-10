package orientdb.streams.impl

import akka.actor.{ Props, ActorSystem }
import akka.stream.actor.ActorPublisher
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.ORecordOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.{ OResultSet, OLiveQuery, OLiveResultListener }
import org.reactivestreams.Publisher
import orientdb.streams.impl.ActorSourceLiveQuery.{TokenFound, Enqueue}
import orientdb.streams.{LiveQueryDataWithToken, LiveQueryData, LiveQuery}

private[streams] class LiveQueryImpl(query: String)(implicit system: ActorSystem) extends LiveQuery {
  def execute(args: AnyRef*)(implicit db: ODatabaseDocumentTx): Publisher[LiveQueryData] = {
    val actorRef = system.actorOf(Props(new ActorSourceLiveQuery(db)))

    val listener = new OLiveResultListener {
      override def onLiveResult(iLiveToken: Int, iOp: ORecordOperation): Unit = {
        actorRef ! Enqueue(LiveQueryDataWithToken(LiveQueryData(iOp), iLiveToken))
      }
    }

    val reply: OResultSet[ODocument] = db.query(new OLiveQuery[ODocument](query, listener), args: _*)
    val token: Integer = reply.get(0).field("token") // from orientdb documentation
    actorRef ! TokenFound(token)

   ActorPublisher[LiveQueryData](actorRef)
  }
}
