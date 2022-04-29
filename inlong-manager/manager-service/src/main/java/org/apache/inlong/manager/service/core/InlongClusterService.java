/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.service.core;

import com.github.pagehelper.PageInfo;
import org.apache.inlong.manager.common.pojo.cluster.ClusterNodeRequest;
import org.apache.inlong.manager.common.pojo.cluster.ClusterNodeResponse;
import org.apache.inlong.manager.common.pojo.cluster.InlongClusterPageRequest;
import org.apache.inlong.manager.common.pojo.cluster.InlongClusterRequest;
import org.apache.inlong.manager.common.pojo.cluster.InlongClusterResponse;

/**
 * Inlong cluster service layer interface
 */
public interface InlongClusterService {

    /**
     * Save cluster info.
     *
     * @param request inlong cluster info
     * @param operator name of operator
     * @return cluster id after saving
     */
    Integer save(InlongClusterRequest request, String operator);

    /**
     * Get cluster info by id.
     *
     * @param id cluster id
     * @return cluster info
     */
    InlongClusterResponse get(Integer id);

    /**
     * Paging query clusters according to conditions.
     *
     * @param request page request conditions
     * @return cluster list
     */
    PageInfo<InlongClusterResponse> list(InlongClusterPageRequest request);

    /**
     * Update cluster information
     *
     * @param request cluster info to be modified
     * @param operator current operator
     * @return whether succeed
     */
    Boolean update(InlongClusterRequest request, String operator);

    /**
     * Delete cluster information.
     *
     * @param id cluster id to be deleted
     * @param operator current operator
     * @return whether succeed
     */
    Boolean delete(Integer id, String operator);

    /**
     * Save cluster node info.
     *
     * @param request inlong cluster info
     * @param operator name of operator
     * @return cluster id after saving
     */
    Integer saveNode(ClusterNodeRequest request, String operator);

    /**
     * Get cluster node info by id.
     *
     * @param id cluster id
     * @return cluster info
     */
    ClusterNodeResponse getNode(Integer id);

    /**
     * Paging query cluster nodes according to conditions.
     *
     * @param request page request conditions
     * @return cluster node list
     */
    PageInfo<ClusterNodeResponse> listNode(InlongClusterPageRequest request);

    /**
     * Update cluster node.
     *
     * @param request cluster node to be modified
     * @param operator current operator
     * @return whether succeed
     */
    Boolean updateNode(ClusterNodeRequest request, String operator);

    /**
     * Delete cluster node.
     *
     * @param id cluster node id to be deleted
     * @param operator current operator
     * @return whether succeed
     */
    Boolean deleteNode(Integer id, String operator);

}
