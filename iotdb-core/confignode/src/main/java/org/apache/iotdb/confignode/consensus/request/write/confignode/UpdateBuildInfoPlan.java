/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.confignode.consensus.request.write.confignode;

import org.apache.iotdb.confignode.consensus.request.ConfigPhysicalPlan;
import org.apache.iotdb.confignode.consensus.request.ConfigPhysicalPlanType;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UpdateBuildInfoPlan extends ConfigPhysicalPlan {

  private int nodeId;
  private String buildInfo;

  public UpdateBuildInfoPlan() {
    super(ConfigPhysicalPlanType.UpdateBuildInfo);
  }

  public UpdateBuildInfoPlan(String buildInfo, int nodeId) {
    this();
    this.buildInfo = buildInfo;
    this.nodeId = nodeId;
  }

  public String getBuildInfo() {
    return buildInfo;
  }

  public int getNodeId() {
    return nodeId;
  }

  @Override
  protected void serializeImpl(DataOutputStream stream) throws IOException {
    ReadWriteIOUtils.write(getType().getPlanType(), stream);
    ReadWriteIOUtils.write(nodeId, stream);
    ReadWriteIOUtils.write(buildInfo, stream);
  }

  @Override
  protected void deserializeImpl(ByteBuffer buffer) {
    nodeId = ReadWriteIOUtils.readInt(buffer);
    buildInfo = ReadWriteIOUtils.readString(buffer);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!getType().equals(((UpdateBuildInfoPlan) o).getType())) {
      return false;
    }
    UpdateBuildInfoPlan that = (UpdateBuildInfoPlan) o;
    return nodeId == that.nodeId && buildInfo.equals(that.buildInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeId, buildInfo);
  }
}
