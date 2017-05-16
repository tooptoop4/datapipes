package DataPipes.Common

trait Observer[-T] {
  def completed(): Unit

  def error(exception: Throwable): Unit

  def next(value: T): Unit
}
