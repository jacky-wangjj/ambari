/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.controller;

import org.apache.ambari.server.state.Mpacks;

/**
 * Represents a mpack response.
 */
public class MpackResponse {

  private Long mpackId;
  private String mpackVersion;
  private String mpackName;
  private String mpackUri;
  private Long registryId;

  public MpackResponse(Mpacks mpacks) {
    this.mpackId = mpacks.getMpackId();
    this.mpackVersion = mpacks.getVersion();
    this.mpackUri = mpacks.getMpacksUri();
    this.mpackName = mpacks.getName();
    this.registryId = mpacks.getRegistryId();
  }

  public String getMpackVersion() {
    return mpackVersion;
  }

  public String getMpackUri() {
    return mpackUri;
  }

  public Long getMpackId() {
    return mpackId;
  }

  public String getMpackName() {
    return mpackName;
  }

  public Long getRegistryId() {
    return registryId;
  }

  public void setMpackVersion(String mpackVersion) {
    this.mpackVersion = mpackVersion;
  }

  public void setMpackName(String mpackName) {
    this.mpackName = mpackName;
  }

  public void setMpackUri(String mpackUri) {
    this.mpackUri = mpackUri;
  }

  public void setRegistryId(Long registryId) {
    this.registryId = registryId;
  }

  public void setMpackId(Long mpackId) {
    this.mpackId = mpackId;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 + getMpackId().hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MpackResponse)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    MpackResponse MpackResponse = (MpackResponse) obj;
    return getMpackId().equals(MpackResponse.getMpackId());
  }
}
