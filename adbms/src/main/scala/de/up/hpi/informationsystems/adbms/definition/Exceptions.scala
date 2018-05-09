package de.up.hpi.informationsystems.adbms.definition


sealed class AdbmsException(message: String) extends Exception(message) {
  def this(message: String, cause: Throwable) = {
    this(message)
    initCause(cause)
  }

  def this(cause: Throwable) = this(cause.toString, cause)

  def this() = this(null: String)
}
/**
  * Indicates that the supplied column definition is not applicable to the current schema.
  *
  * @param message gives details
  */
case class IncompatibleColumnDefinitionException(message: String) extends AdbmsException(message)
