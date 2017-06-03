package Task

import DataPipes.Common._
import DataPipes.Common.Data._
import Term.TermExecutor

import scala.util.Try
import scala.collection.mutable.{ListBuffer, Queue}

class TaskExtract(val name: String, val config: DataSet, val version: String) extends Task {

  val size: Int = config("size").stringOption.flatMap(m => Try(m.toInt).toOption).getOrElse(100)
  val dataSource: DataSource = DataSource(config("dataSource"))
  private val _observer: ListBuffer[Observer[Dom]] = ListBuffer()
  val buffer = Queue[DataSet]()
  private val namespace = config("namespace").stringOption.getOrElse("Term.Legacy.Functions")
  private val termExecutor = new TermExecutor(namespace)
  private val termRead = TaskLookup.getTermTree(config("dataSource")("query")("read"))

  def completed(): Unit = { Unit }

  def error(exception: Throwable): Unit = ???

  def next(value: Dom): Unit = {
    dataSource.subscribe(dsObserver)


    if(config("dataSource")("query")("read").toOption.isDefined)
      dataSource.execute(config("dataSource"), TaskLookup.interpolate(termExecutor, termRead,
        value.headOption.map(m => m.success).getOrElse(DataNothing())))
    else
      dataSource.execute(config("dataSource"))
  }

  lazy val dsObserver = new Observer[DataSet] {
    def completed(): Unit = {
      if(buffer.nonEmpty) {
        responseAdjust()
        buffer.clear()
        _observer.foreach(o => o.completed())
      }
    }

    def error(exception: Throwable): Unit = _observer.foreach(o => o.error(exception))

    def next(value: DataSet): Unit = {
      buffer.enqueue(value)

      if(buffer.size == size)
      {
        responseAdjust()
        buffer.clear()
      }
    }

  }

  // dont send an array for rest data source if v1
  def responseAdjust() =
    if (config("dataSource")("type").stringOption.contains("rest") && version == "v1") {
      val send = for {
        o <- _observer
        b <- buffer
      } yield (o,b)
      send.foreach(s => s._1.next(Dom() ~ Dom(name, List(), s._2("root"), DataNothing())))
    } else
      _observer.foreach(o => o.next(Dom() ~ Dom(name, List(), DataArray(buffer.toList), DataNothing())))


  def subscribe(observer: Observer[Dom]): Unit = {
    _observer.append(observer)
  }


}
