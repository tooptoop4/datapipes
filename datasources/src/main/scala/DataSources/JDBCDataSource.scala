package DataSources

import DataPipes.Common.Data._
import DataPipes.Common.{DataSource, Dom, Observer}
import java.sql._

import com.typesafe.scalalogging.Logger

import scala.collection.mutable.ListBuffer

class JDBCDataSource extends DataSource {

  val _observer: ListBuffer[Observer[DataSet]] = ListBuffer()

  val logger = Logger("JDBCDataSource")

  def subscribe(observer: Observer[DataSet]): Unit = _observer.append(observer)

  def execute(config: DataSet, statement: String, executeQuery: Boolean): Unit = {
    val connectionString = config("connect").stringOption.getOrElse("")

    val cn = DriverManager.getConnection(connectionString)

    logger.info("Connected...")

    val stmt: PreparedStatement = cn.prepareStatement(statement)

    logger.info("Executing SQL batch statement...")
    logger.info(statement)

    if(executeQuery) {
      val rs = stmt.executeQuery()

      val metaData = rs.getMetaData
      val ordinals = 1 to metaData.getColumnCount
      val header = ordinals.map(o => (metaData.getColumnType(o), metaData.getColumnName(o))).toList

      while (rs.next()) {
        _observer.foreach(o => o.next(DataRecord("row", header.map(v =>
          JDBCDataSource.typeMap.get(v._1).map(m => m(v._2, rs)).getOrElse(DataString(v._2, rs.getObject(v._2).toString))))))
      }
    } else {
      stmt.execute()
    }

    _observer.foreach(o => o.completed())

    logger.info("Successfully executed statement.")

    cn.close()
  }

  def execute(config: DataSet, query: DataSet): Unit = {
    execute(config, query.stringOption.getOrElse(""), query.label == "read")
  }

  override def executeBatch(config: DataSet, query: Seq[DataSet]): Unit = {
    execute(config, query.flatMap(_.stringOption).mkString(";"), false)
  }
}

object JDBCDataSource {

  lazy val typeMap = List(

    (List(Types.BIGINT, Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.INTEGER, Types.NUMERIC),
      (name: String, rs: ResultSet) =>
        DataNumeric(name,
          BigDecimal(BigDecimal(rs.getObject(name).toString)
            .underlying()
            .stripTrailingZeros()
            .toPlainString))),

    (List(Types.DATE, Types.TIME, Types.TIMESTAMP),
      (name: String, rs: ResultSet) =>
        DataDate(name, rs.getDate(name)))

  ).flatMap(t => t._1.map(s => s -> t._2)).toMap

}