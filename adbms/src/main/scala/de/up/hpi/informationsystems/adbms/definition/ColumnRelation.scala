package de.up.hpi.informationsystems.adbms.definition


sealed trait ColumnRelation extends Relation {
  protected val colMap: Map[String, ColumnDef]

  /**
    * Insert new values into the relation
    * @param column
    * @param value
    * @tparam T
    */
  // FIXME: not single values, but whole lines
  def insert[T](column: TypedColumnDef[T], value: T): Unit

  // FIXME: why should we need this?
  def getCol[T](column: TypedColumnDef[T]): Option[TypedColumnStore[T]]
}

/**
  * Defines a column-oriented relation schema, which's store gets automatically generated.
  */
object ColumnRelation {

  //def apply(columnDef: ColumnDef): ColumnRelationDef = new ColumnRelationDef(Seq(columnDef))

  //def apply(columnDef: ColumnDef, columnDefs: ColumnDef*): ColumnRelationDef = new ColumnRelationDef(Seq(columnDef) ++ columnDefs.toSeq)

  /**
    * Defines a column-oriented relation schema, which gets automatically generated.
    *
    * @param columnDefs sequence of column definitions
    * @return the generated column-oriented relational store
    */
  def apply(columnDefs: Seq[ColumnDef]): ColumnRelation = new ColumnRelationStore(columnDefs)

  /**
    * Indicates that a [[de.up.hpi.informationsystems.adbms.definition.ColumnDef]] was not found in
    * the column relation.
    *
    * @param message gives details
    */
  class ColumnNotFoundException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = this(cause.toString, cause)

    def this() = this(null: String)
  }

  /**
    * Private (hidden) implementation of the [[de.up.hpi.informationsystems.adbms.definition.ColumnRelation]] trait.
    * @param colDefs column definitions used to construct the underlying data store
    */
  private final class ColumnRelationStore(private val colDefs: Seq[ColumnDef]) extends ColumnRelation {

    private val data: Map[ColumnDef, ColumnStore] =
      colDefs.map { colDef: ColumnDef =>
        Map(colDef -> colDef.buildColumnStore())
      }.reduce(_ ++ _)

    private var n: Int = 0


    /** @inheritdoc */
    override def columns: Seq[ColumnDef] = colDefs

    /** @inheritdoc */
    override def insert(record: Record): Unit = ???

    /** @inheritdoc */
    override def where[T](f: (TypedColumnDef[T], T => Boolean)): Seq[Record] = ???

    /** @inheritdoc */
    override def whereAll(fs: Map[ColumnDef, Any => Boolean]): Seq[Record] = ???

    override protected val colMap: Map[String, ColumnDef] =
      colDefs.map(c => Map(c.name -> c)).reduce(_ ++ _)

    @throws[ColumnNotFoundException]
    override def insert[T](column: TypedColumnDef[T], value: T): Unit =
      if (data.contains(column)) {
        val col = data(column).asInstanceOf[TypedColumnStore[T]]
        col.append(value)
        if (col.length - 1 > n) n = col.length - 1
      } else
        throw new ColumnNotFoundException(s"Column $column is not part of this relation")

    override def getCol[T](column: TypedColumnDef[T]): Option[TypedColumnStore[T]] =
      if (data.contains(column))
        Some(data(column).asInstanceOf[TypedColumnStore[T]])
      else
        None

    override def toString: String = {
      val header = columns.map { c => s"${c.name}[${c.tpe}]" }.mkString(" | ")
      val line = "-" * header.length
      var content: String = ""
      for (i <- 0 to n) {
        val col: Seq[ColumnStore] = columns.map(data)
        content = content + col.map(_.get(i)).mkString(" | ") + "\n"
      }
      header + "\n" + line + "\n" + content + "\n" + line + "\n"
    }
  }

}