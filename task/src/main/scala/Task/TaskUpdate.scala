package Task

import DataPipes.Common.Data._
import DataPipes.Common.{Dom, Observer, Task}
import Term.TermExecutor

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Cache {
  var dim: scala.collection.mutable.HashMap[String, String] = mutable.HashMap()

  def clear: Unit = { dim.clear() }
}

class TaskUpdate(val name: String, val config: DataSet, version: String) extends Task {

  private val _observer: ListBuffer[Observer[Dom]] = ListBuffer()
  private val namespace = config("namespace").stringOption.getOrElse("Term.Functions")
  private val termExecutor = new TermExecutor(namespace)
  private val keyRightTerm = termExecutor.getTemplateTerm(config("keyR").stringOption.getOrElse(""))
  private val changeRightTerm = termExecutor.getTemplateTerm(config("changeR").stringOption.getOrElse(""))
  private val keyLeftTerm = termExecutor.getTemplateTerm(config("keyL").stringOption.getOrElse(""))
  private val changeLeftTerm = termExecutor.getTemplateTerm(config("changeL").stringOption.getOrElse(""))
  private val queryDataSet = queryAdjust(config("dataSource")("query"))
  private val termRead = TaskLookup.getTermTree(queryDataSet("read"))
  private val termCreate = TaskLookup.getTermTree(queryDataSet("create"))

  var initialised = false

  // add a $ sign to any templates if it looks like a template variable
  def queryAdjust(query: DataSet): DataSet =
    if(version == "v1") {
      query match {
        case r: DataRecord => DataRecord(r.label, r.fields.map(f => queryAdjust(f)))
        case DataString(label, str) if str.matches("[a-zA-Z_$][a-zA-Z_$0-9]*$") => DataString(label, "$" + str)
        case ds => ds
      }
    }
    else
      query


  def subscribe(observer: Observer[Dom]): Unit = _observer.append(observer)

  def completed(): Unit = _observer.foreach(o => o.completed())

  def error(exception: Throwable): Unit = ???

  def next(value: Dom): Unit = {

    if(!initialised) {

      val query = TaskLookup.interpolate(termExecutor, termRead,
        value.headOption.map(m => m.success).getOrElse(DataNothing()))

      val src = DataSource(config("dataSource"))

      val localObserver = new Observer[DataSet] {

        override def completed(): Unit = {

        }

        override def error(exception: Throwable): Unit = ???

        override def next(value: DataSet): Unit = {
          Cache.dim.put(
            termExecutor.eval(value, keyRightTerm).stringOption.getOrElse(""),
            termExecutor.eval(value, changeRightTerm).stringOption.getOrElse(""))
        }
      }

      src.subscribe(localObserver)

      src.execute(config("dataSource"), query)

      initialised = true

    }

    val incoming = value
      .headOption
      .toList
      .flatMap(_.success.map(m => (
        m,
        termExecutor.eval(m, keyLeftTerm).stringOption.getOrElse(""),
        termExecutor.eval(m, changeLeftTerm).stringOption.getOrElse("")
      )).toList
      .groupBy(g => g._2)
      .map(f => f._2.head)
      .toList)

    val inserts = incoming.filter(d => Cache.dim.get(d._2).isEmpty)
    val updates = incoming.filter(d => Cache.dim.get(d._2).isDefined)
      .filterNot(d => Cache.dim.get(d._2).contains(d._3))

    if(config("dataSource")("query")("create").toOption.isDefined && inserts.nonEmpty) {
      val src = DataSource(config("dataSource"))

      val query = inserts.map(i =>
        TaskLookup.interpolate(termExecutor, termCreate, i._1))

      src.executeBatch(config("dataSource"), query)

      Cache.dim.++=(inserts.map(i => i._2 -> i._3))
    }


  }
}