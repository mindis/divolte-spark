/*
 * Copyright 2014 GoDataDriven B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.divolte.spark.avro

import java.io.{Serializable => JSerializable}

import org.apache.avro.generic.IndexedRecord
import org.apache.avro.mapred.AvroWrapper
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

/**
 * Magnet for operations on a RDD containing Avro records.
 *
 * This is motivated by the fact that Avro records (ironically) don't implement the
 * [[java.io.Serializable]] interface, which means that the only safe operations are
 * those which cannot result in Spark trying to serialize the records.
 *
 * For convenience, we provide two operations:
 *  1. Extract a set of fields, specified by name. These are extracted to a
 *     [[scala.Seq[Option[AnyRef]]]] collection.
 *  2. Convert to a [[io.divolte.spark.avro.Record]].
 *
 * @param rdd the RDD being wrapped.
 * @tparam T  the type of the deserialized Avro record.
 */
class AvroRDDMagnet[+T <: IndexedRecord : ClassTag] private[AvroRDDMagnet] (rdd: RDD[T]) {

  /**
   * Extract serializable values from a RDD of Avro records.
   *
   * @param f   A function to extract the serializable values from a record.
   * @tparam U  The (serializable) type of the value extracted from a record.
   * @return    A RDD containing the extracted values.
   */
  def map[U <: JSerializable : ClassTag](f: T => U): RDD[U] = rdd.map(f)

  /**
   * View the RDD of Avro records as [[io.divolte.spark.avro.Record]] instances.
   *
   * This operation must perform a deep copy of the Avro record with conversions
   * to ensure that everything can be serialized. If you only wish to access a
   * small subset of the Avro record, it can be more efficient to extract the
   * fields you need using [[AvroRDDMagnet#fields]].
   *
   * @return a RDD of [[io.divolte.spark.avro.Record]] instances built from the Avro records.
   */
  def toRecords: RDD[Record] = map(Record.apply)

  /**
   * Extract specific fields from a RDD of Avro records.
   *
   * This operation extracts specific fields from a RDD of Avro records. Field values
   * are wrapped in an [[Option]].
   *
   * @param fieldNames the names of the fields to extract
   * @return a RDD of sequences containing the field values requested.
   */
  def fields(fieldNames: String*): RDD[Seq[Option[JSerializable]]] = {
    rdd.map(AvroConverters.extractFields(_, fieldNames:_*))
  }
}

object AvroRDDMagnet {
  @inline
  private[spark] def apply[K <: IndexedRecord: ClassTag, V, W <: AvroWrapper[K]](rdd: RDD[(W,V)]) =
    new AvroRDDMagnet[K](rdd.map(_._1.datum()))
}
