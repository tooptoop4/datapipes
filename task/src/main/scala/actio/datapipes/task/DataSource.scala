package actio.datapipes.task

import actio.common.Data.DataSet
import actio.common.DataSource
import actio.datapipes.dataSources._


object DataSource {

  private lazy val sources = Map(
    "stdin" -> ((_: DataSet) =>
      new StdInDataSource()),
    "file" -> ((config: DataSet) => {
      val behavior = config("behavior").stringOption

      if(behavior.contains("DBF"))
        new LocalFileSystemDataSource("dbf")
      else if(behavior.contains("csv"))
        new LocalFileSystemDataSource("csv")
      else if(behavior.contains("dump"))
        new LocalFileSystemDataSource("dump")
      else
        new TextFileDataSource()
    }),
    "txt" -> ((_: DataSet) =>
      new LocalFileSystemDataSource("txt")),
    "rest" -> ((_: DataSet) =>
      new RESTJsonDataSource()),
    "sql" -> ((_: DataSet) =>
      new JDBCDataSource())
  )


  def apply(parameters: DataSet): DataSource = sources(parameters("type").stringOption.get)(parameters)

}
