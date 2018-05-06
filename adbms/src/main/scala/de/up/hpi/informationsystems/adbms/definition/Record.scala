package de.up.hpi.informationsystems.adbms.definition

import java.util.Objects

import scala.collection.{MapLike, mutable}

class Record private (cells: Map[ColumnDef, Any]) extends MapLike[ColumnDef, Any, Record] with Map[ColumnDef, Any] {

  private val data = cells

  val columns: Seq[ColumnDef] = cells.keys.toSeq

  def get[T](columnDef: TypedColumnDef[T]): Option[T] =
    if(data.contains(columnDef))
      Some(data(columnDef).asInstanceOf[T])
    else
      None


  // from MapLike
  override def empty: Record = new Record(Map.empty)

  /**
    * Use [[de.up.hpi.informationsystems.adbms.definition.Record#get]] instead!
    * It takes care of types!
    */
  @Deprecated
  override def get(key: ColumnDef): Option[Any] = get(key.asInstanceOf[TypedColumnDef[Any]])

  override def iterator: Iterator[(ColumnDef, Any)] = data.iterator

  override def +[V1 >: Any](kv: (ColumnDef, V1)): Map[ColumnDef, V1] = data.+(kv)

  override def -(key: ColumnDef): Record = new Record(data - key)

  // from Iterable
  override def seq: Map[ColumnDef, Any] = data.seq

  // from Object
  override def toString: String = s"Record($data)"

  override def hashCode(): Int = Objects.hash(columns, data)

  override def equals(o: scala.Any): Boolean =
    if (o == null || getClass != o.getClass)
      false
    else {
      // cast other object
      val otherRecord: Record = o.asInstanceOf[Record]
      if (this.columns.equals(otherRecord.columns) && this.data.equals(otherRecord.data))
        true
      else
        false
    }

  // FIXME: I don't know what to do here.
  override protected[this] def newBuilder: mutable.Builder[(ColumnDef, Any), Record] = ???
}

object Record {
  /**
    * Creates a [[de.up.hpi.informationsystems.adbms.definition.Record]] with the builder pattern.
    *
    * @example {{{
    * val firstnameCol = ColumnDef[String]("Firstname")
    * val lastnameCol = ColumnDef[String]("Lastname")
    * val ageCol = ColumnDef[Int]("Age")
    *
    * // syntactic sugar
    * val record = Record(Seq(firstnameCol, lastnameCol, ageCol))(
    *     firstnameCol -> "Hans"
    *   )(
    *     ageCol -> 45
    *   )
    *   .withCellContent(lastnameCol -> "")
    *   .build()
    *
    * // is the same:
    * var rb = Record(Seq(firstnameCol, lastnameCol, ageCol))
    * rb = rb(firstnameCol -> "Hans")
    * rb = rb(lastnameCol -> "")
    * rb = rb(ageCol -> 45)
    * val sameRecord = rb.build()
    *
    * assert(record == sameRecord)
    * }}}
    *
    * This call initiates the [[de.up.hpi.informationsystems.adbms.definition.Record.RecordBuilder]] with
    * the column definitions of the corresponding relational schema
    */
  def apply(columnDefs: Seq[ColumnDef]): RecordBuilder = new RecordBuilder(columnDefs, Map.empty)

  /**
    * Builder for a [[de.up.hpi.informationsystems.adbms.definition.Record]]
    * @param columnDefs all columns of the corresponding relational schema
    */
  class RecordBuilder(columnDefs: Seq[ColumnDef], recordData: Map[ColumnDef, Any]) {

    /**
      *
      * @param in mapping from column to cell content
      * @tparam T value type, same as for the column definition
      * @return the [[RecordBuilder]] itself for
      */
    def apply[T](in: (TypedColumnDef[T], T)): RecordBuilder =
      new RecordBuilder(columnDefs, recordData ++ Map(in))

    def withCellContent[T](in: (TypedColumnDef[T], T)): RecordBuilder = apply(in)

    def build(): Record = {
      val data: Map[ColumnDef, Any] = columnDefs
        .map{ colDef => Map(colDef -> recordData.getOrElse(colDef, null)) }
        .reduce( _ ++ _)
      new Record(data)
    }
  }
}