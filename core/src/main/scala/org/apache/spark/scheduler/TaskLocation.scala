/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.scheduler

import org.apache.hadoop.fs.StorageType

/**
 * A location where a task should run. This can either be a host or a (host, executorID) pair.
 * In the latter case, we will prefer to launch the task on that executorID, but our next level
 * of preference will be executors on the same host if this is not possible.
 */
private[spark] sealed trait TaskLocation {
  def host: String
}

/**
 * A location that includes both a host and an executor id on that host.
 */
private [spark]
case class ExecutorCacheTaskLocation(override val host: String, executorId: String)
  extends TaskLocation {
  override def toString: String = s"${TaskLocation.executorLocationTag}${host}_$executorId"
}

/**
 * A location on a host.
 */
private [spark] case class HostTaskLocation(override val host: String) extends TaskLocation {
  override def toString: String = host
}

/**
 * A location on a host that is cached by HDFS.
 */
private [spark] case class HDFSCacheTaskLocation(override val host: String) extends TaskLocation {
  override def toString: String = TaskLocation.inMemoryLocationTag + host
}

/**
 * A location on a host with storage type information.
 */
private [spark] case class HostTaskLocationWithStorageType(override val host: String,
  storageType: StorageType) extends TaskLocation {
  override def toString: String = s"${TaskLocation.storageTypeTag}${host}_${storageType.toString}"
}

/**
 * A location on a host with storage type information that is cached by HDFS.
 */
private [spark] case class HDFSCacheTaskLocationWithStorageType(override val host: String,
  storageType: StorageType) extends TaskLocation {
  override def toString: String = TaskLocation.inMemoryLocationTag +
    s"${TaskLocation.storageTypeTag}${host}_${storageType.toString}"
}

private[spark] object TaskLocation {
  // We identify hosts on which the block is cached with this prefix.  Because this prefix contains
  // underscores, which are not legal characters in hostnames, there should be no potential for
  // confusion.  See  RFC 952 and RFC 1123 for information about the format of hostnames.
  val inMemoryLocationTag = "hdfs_cache_"

  // Identify locations of executors with this prefix.
  val executorLocationTag = "executor_"

  // Identify locations with storage type
  val storageTypeTag = "storage_type_"

  def apply(host: String, executorId: String): TaskLocation = {
    new ExecutorCacheTaskLocation(host, executorId)
  }

  /**
   * Create a TaskLocation from a string returned by getPreferredLocations.
   * These strings have the form executor_[hostname]_[executorid], [hostname], or
   * hdfs_cache_[hostname], depending on whether the location is cached.
   */
  def apply(str: String): TaskLocation = {
    val hstr = str.stripPrefix(inMemoryLocationTag)
    if (hstr.equals(str)) {
      if (str.startsWith(executorLocationTag)) {
        val hostAndExecutorId = str.stripPrefix(executorLocationTag)
        val splits = hostAndExecutorId.split("_", 2)
        require(splits.length == 2, "Illegal executor location format: " + str)
        val Array(host, executorId) = splits
        new ExecutorCacheTaskLocation(host, executorId)
      } else if (str.startsWith(storageTypeTag)) {
        val splits = parseStorageType(str)
        new HostTaskLocationWithStorageType(splits(0), StorageType.valueOf(splits(1)))
      } else {
        new HostTaskLocation(str)
      }
    } else {
      if (hstr.startsWith(storageTypeTag)) {
        val splits = parseStorageType(str)
        new HDFSCacheTaskLocationWithStorageType(splits(0), StorageType.valueOf(splits(1)))
      } else {
        new HDFSCacheTaskLocation(hstr)
      }
    }
  }

  private def parseStorageType(str: String): Array[String] = {
    if (str.startsWith(storageTypeTag)) {
      val hostAndStorageType = str.stripPrefix(storageTypeTag)
      hostAndStorageType.split("_", 2)
    } else {
      new Array[String](0)
    }
  }
}
