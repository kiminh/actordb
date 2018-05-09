package de.up.hpi.informationsystems.adbms.definition
import scala.util.Try

sealed trait RowRelation extends Relation


/**
  * Defines a row-oriented relational schema, which's store gets automatically generated.
  */
object RowRelation {

  /**
    * Defines a row-oriented relational schema, which gets automatically generated.
    *
    * @param columnDefs sequence of column definitions
    * @return the generated row-oriented relational store
    */
  def apply(columnDefs: Seq[ColumnDef]): RowRelation = new RowRelationStore(columnDefs)

  /**
    * Indicates that a [[de.up.hpi.informationsystems.adbms.definition.ColumnDef]] was not found in
    * the row relation.
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
    * Private (hidden) implementation of the [[de.up.hpi.informationsystems.adbms.definition.RowRelation]] trait.
    * @param colDefs column definitions used to construct the underlying data store
    */
  private final class RowRelationStore(private val colDefs: Seq[ColumnDef]) extends RowRelation {

    private var data: Seq[Record] = Seq.empty

    /** @inheritdoc */
    override def columns: Seq[ColumnDef] = colDefs

    /** @inheritdoc */
    override def insert(record: Record): Unit = data = data :+ record

    /** @inheritdoc */
    override def where[T](f: (TypedColumnDef[T], T => Boolean)): Seq[Record] =
      data.filter{ record => record.get[T](f._1).exists(f._2) }

    /** @inheritdoc */
    override def whereAll(fs: Map[ColumnDef, Any => Boolean]): Seq[Record] =
      // filter all records
      data.filter{ record =>
        fs.keys
          // map over all supplied filters (key = column)
          .map { col: ColumnDef =>
            /* `val rVal = record(col)` returns the value in the record for the column `col`
             * `val filterF = fs(col)` returns the filter for column `col`
             * `val res = filterF(rVal)` applies the filter to the value of the record and corresponding column,
             * returning `true` or `false`
             */
          fs(col)(record(col))
          }
          // test if all filters for this record are true
          .forall(_ == true)
      }

    override def project(columnDefs: Seq[ColumnDef]): Try[Seq[Record]] = Try(
      if(columnDefs.toSet subsetOf columns.toSet)
        data.map(_.project(columnDefs).get)
      else
        throw IncompatibleColumnDefinitionException(s"this relation does not contain all specified columns {$columnDefs}")
    )

    /** @inheritdoc */
    override def toString: String = {
      val header = columns.map { c => s"${c.name}[${c.tpe}]" }.mkString(" | ")
      val line = "-" * header.length
      val content = data.map(_.values.mkString(" | ")).mkString("\n")
      header + "\n" + line + "\n" + content + "\n" + line + "\n"
    }
  }

}