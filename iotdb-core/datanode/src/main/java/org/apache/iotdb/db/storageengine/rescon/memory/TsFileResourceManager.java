/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.storageengine.rescon.memory;

import org.apache.iotdb.commons.service.metric.MetricService;
import org.apache.iotdb.commons.service.metric.enums.Metric;
import org.apache.iotdb.commons.service.metric.enums.Tag;
import org.apache.iotdb.commons.utils.TestOnly;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.timeindex.TimeIndexLevel;
import org.apache.iotdb.metrics.utils.MetricLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TreeSet;

public class TsFileResourceManager {
  private static final Logger logger = LoggerFactory.getLogger(TsFileResourceManager.class);

  private static final IoTDBConfig CONFIG = IoTDBDescriptor.getInstance().getConfig();

  /** threshold total memory for all TimeIndex */
  private long timeIndexMemoryThreshold = CONFIG.getAllocateMemoryForTimeIndex();

  /** store the sealed TsFileResource, sorted by priority of TimeIndex */
  private final TreeSet<TsFileResource> sealedTsFileResources =
      new TreeSet<>(TsFileResource::compareIndexDegradePriority);

  /** total used memory for TimeIndex */
  private long totalTimeIndexMemCost;

  // degraded time index number
  private long degradedTimeIndexNum = 0;

  private TsFileResourceManager() {
    MetricService.getInstance()
        .getOrCreateGauge(
            Metric.IOT_MEMORY.toString(),
            MetricLevel.IMPORTANT,
            Tag.NAME.toString(),
            "TimeIndex",
            Tag.TYPE.toString(),
            "threshold",
            Tag.MODULE.toString(),
            "query")
        .set(timeIndexMemoryThreshold);
    MetricService.getInstance()
        .createAutoGauge(
            Metric.IOT_MEMORY.toString(),
            MetricLevel.IMPORTANT,
            this,
            TsFileResourceManager::getTotalTimeIndexMemCost,
            Tag.NAME.toString(),
            "TimeIndex",
            Tag.TYPE.toString(),
            "actual",
            Tag.MODULE.toString(),
            "query");
  }

  @TestOnly
  public void setTimeIndexMemoryThreshold(long timeIndexMemoryThreshold) {
    this.timeIndexMemoryThreshold = timeIndexMemoryThreshold;
  }

  public long getPriorityQueueSize() {
    return sealedTsFileResources.size();
  }

  /**
   * add the closed TsFileResource into priorityQueue and increase memory cost of timeIndex, once
   * memory cost is larger than threshold, degradation is triggered.
   */
  public synchronized void registerSealedTsFileResource(TsFileResource tsFileResource) {
    if (!sealedTsFileResources.contains(tsFileResource)) {
      sealedTsFileResources.add(tsFileResource);
      totalTimeIndexMemCost += tsFileResource.calculateRamSize();
      chooseTsFileResourceToDegrade();
    }
  }

  /** delete the TsFileResource in PriorityQueue when the source file is deleted */
  public synchronized void removeTsFileResource(TsFileResource tsFileResource) {
    if (sealedTsFileResources.contains(tsFileResource)) {
      sealedTsFileResources.remove(tsFileResource);
      if (TimeIndexLevel.valueOf(tsFileResource.getTimeIndexType())
          == TimeIndexLevel.FILE_TIME_INDEX) {
        totalTimeIndexMemCost -= tsFileResource.calculateRamSize();
        degradedTimeIndexNum--;
      } else {
        totalTimeIndexMemCost -= tsFileResource.getRamSize();
      }
    }
  }

  /** once degradation is triggered, the total memory for timeIndex should reduce */
  private void releaseTimeIndexMemCost(long memCost) {
    totalTimeIndexMemCost -= memCost;
  }

  /**
   * choose the top TsFileResource in priorityQueue to degrade until the memory is smaller than
   * threshold.
   */
  private void chooseTsFileResourceToDegrade() {
    while (totalTimeIndexMemCost > timeIndexMemoryThreshold) {
      TsFileResource tsFileResource = sealedTsFileResources.pollFirst();
      if (tsFileResource == null
          || TimeIndexLevel.valueOf(tsFileResource.getTimeIndexType())
              == TimeIndexLevel.FILE_TIME_INDEX) {
        logger.debug("Can't degrade time index any more because all time index are file level.");
        sealedTsFileResources.add(tsFileResource);
        return;
      }
      long memoryReduce = tsFileResource.degradeTimeIndex();
      logger.debug("Degrade tsfile resource {}", tsFileResource.getTsFilePath());
      degradedTimeIndexNum++;
      releaseTimeIndexMemCost(memoryReduce);
      // add the polled tsFileResource to the priority queue
      sealedTsFileResources.add(tsFileResource);
    }
  }

  public long getDegradedTimeIndexNum() {
    return degradedTimeIndexNum;
  }

  public long getTimeIndexMemoryThreshold() {
    return timeIndexMemoryThreshold;
  }

  public long getTotalTimeIndexMemCost() {
    return totalTimeIndexMemCost;
  }

  /** function for clearing TsFileManager */
  public synchronized void clear() {
    this.sealedTsFileResources.clear();
    this.totalTimeIndexMemCost = 0;
    this.degradedTimeIndexNum = 0;
  }

  public static TsFileResourceManager getInstance() {
    return TsFileResourceManager.InstanceHolder.INSTANCE;
  }

  private static class InstanceHolder {
    private InstanceHolder() {}

    private static final TsFileResourceManager INSTANCE = new TsFileResourceManager();
  }
}
