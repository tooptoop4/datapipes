package actio.datapipes.task.Term

import java.lang.reflect.Parameter

import actio.common.Data._
import actio.common.Data.ImplicitCasts._

import scala.annotation.tailrec
import scala.util.Try

object FunctionExecutor {

  def execute(nameSpace: String, methodName: String, params: List[DataSet]): DataSet =

  // Tries to invoke the best possible implementation of a function based on name, input types and parameter length
  // Also casts the return type to a DataSet
    Class.forName(nameSpace)
      .getDeclaredMethods
      .filter(f => f.getName.equalsIgnoreCase(methodName) && params.size >= f.getParameterCount)
      .map(m => (m, getParamValues(m.getParameters.toList, params, Nil)))
      .find(c => c._2.isDefined)
      .flatMap(i =>
        i._1.invoke(null, i._2.get.map(_.asInstanceOf[Object]): _*) match { // return type conversion
          case r: DataSet => Some(r)
          case str: String => Some(str: DataSet)
          case arr: Array[String] => Some(DataArray(arr.map(DataString(_)).toList))
          case bool: java.lang.Boolean => Some(bool: DataSet)
          case num: BigDecimal => Some(num: DataSet)
          case date: java.util.Date => Some(date: DataSet)
          case _ => None
        })
      .getOrElse(throw new Exception(s"Method not found: $nameSpace.$methodName"))

  // pattern match on parameters and cast if necessary
  @tailrec
  private def getParamValues(parameters: List[(Parameter, DataSet)], result: List[Any]): Option[List[Any]] = parameters match {
    case Nil => Some(result.reverse)
    case ((methodParameter, DataString(_, str)) :: tail)
      if methodParameter.getType == classOf[String] || methodParameter.getType == classOf[Object] =>
        getParamValues(tail, Option(str).getOrElse("") :: result)
    case ((methodParameter, DataNumeric(_, num)) :: tail)
      if methodParameter.getType == classOf[Int] && Try(num.toInt).isSuccess => getParamValues(tail, num.toInt :: result)
    case ((methodParameter, DataNumeric(_, num)) :: tail)
      if methodParameter.getType == classOf[BigDecimal] => getParamValues(tail, num :: result)
    case ((methodParameter, DataDate(_, date)) :: tail)
      if methodParameter.getType == classOf[java.util.Date] => getParamValues(tail, new java.util.Date(date) :: result)
    case ((methodParameter, ds: DataSet) :: tail)
      if methodParameter.getType == classOf[DataSet] => getParamValues(tail, ds :: result)
    case ((methodParameter, ds: DataSet) :: tail)
      if methodParameter.getType == classOf[Array[String]] =>
      getParamValues(tail, ds.map(_.stringOption.getOrElse("")).toArray :: result)
    case ((methodParameter, ds: DataSet) :: tail)
      if methodParameter.getType == classOf[String] => getParamValues(tail, ds.toString :: result)
    case _ => None
  }

  private def getParamValue(parameter: Parameter, input: DataSet): Option[Any] = (parameter,input) match {
    case ((methodParameter, DataString(_, str)))
      if methodParameter.getType == classOf[String] || methodParameter.getType == classOf[Object] =>
        Some(Option(str).getOrElse(""))
    case ((methodParameter, DataNumeric(_, num)))
      if methodParameter.getType == classOf[Int] && Try(num.toInt).isSuccess => Some(num.toInt)
    case ((methodParameter, DataNumeric(_, num)))
      if methodParameter.getType == classOf[BigDecimal] => Some(num)
    case ((methodParameter, DataDate(_, date)) )
      if methodParameter.getType == classOf[java.util.Date] => Some(new java.util.Date(date))
    case ((methodParameter, ds: DataSet))
      if methodParameter.getType == classOf[DataSet] => Some(ds)
    case ((methodParameter, ds: DataSet))
      if methodParameter.getType == classOf[Array[String]] => Some(ds.map(_.stringOption.getOrElse("")).toArray)
    case ((methodParameter, ds: DataSet))
      if methodParameter.getType == classOf[String] => Some(ds.stringOption.getOrElse(""))
    case _ => None
  }

  @tailrec
  private def getParamValues(sigParams: List[Parameter], inputParams: List[DataSet], result: List[Any]): Option[List[Any]] =
    sigParams match {
      case Nil => Some(result.reverse)
      case (sigParam :: sigTail) => inputParams match {
        case Nil => Some(null)
        case (inputParam :: inputTail) => {
          val trySingle = getParamValue(sigParam, inputParam)
          if(trySingle.isDefined)
            getParamValues(sigTail, inputTail, trySingle.get :: result)
          else {
            if(sigParam.getType == classOf[List[DataSet]]) {
              val listLength = inputParams.length - sigParams.length + 1
                getParamValues(sigTail, inputParams.drop(listLength), inputParams.take(listLength) :: result)
            }
            else
              None
          }
        }
      }
    }


}
