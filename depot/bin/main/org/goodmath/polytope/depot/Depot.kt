/*
 * Copyright 2023 Mark C. Chu-Carroll
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
package org.goodmath.polytope.depot

import maryk.rocksdb.RocksDB
import maryk.rocksdb.openRocksDB
import org.goodmath.polytope.depot.agents.Agent
import org.goodmath.polytope.depot.agents.BaselineAgent
import org.goodmath.polytope.depot.agents.text.TextAgent
import org.goodmath.polytope.depot.stashes.*
import org.goodmath.polytope.depot.agents.DirectoryAgent
import org.goodmath.polytope.workspace.WorkspaceStash
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import kotlin.text.Charsets.UTF_8

data class Config(
    val rootUser: String,
    val rootEmail: String,
    val password: String,
    val dbPath: String,

)

interface Stash {
    fun initStorage(config: Config)
}

class Depot(cfg: Config) {
    companion object {
        val families= listOf(
            "users", "artifacts", "versions", "changes",
            "savePoints", "histories", "historyVersions", "projects",
            "workspaces"
        )
        fun initializeStorage(cfg: Config) {
            val rocksDb = openRocksDB(cfg.dbPath)
            rocksDb.createColumnFamilies(families.map {
                ColumnFamilyDescriptor(it.toByteArray(UTF_8))
            })
            rocksDb.close()
            val depot = Depot(cfg)
            depot.users.initStorage(cfg)
            depot.artifacts.initStorage(cfg)
            depot.changes.initStorage(cfg)
            depot.histories.initStorage(cfg)
            depot.projects.initStorage(cfg)
            depot.workspaces.initStorage(cfg)

            depot.close()
         }
    }

    val db: RocksDB
    private val cfHandles: Map<String, ColumnFamilyHandle>
    val agents = listOf(DirectoryAgent, TextAgent, BaselineAgent)

    init {
        val handles: MutableList<ColumnFamilyHandle> = mutableListOf()
        val descriptors = families.map { ColumnFamilyDescriptor(it.toByteArray(UTF_8)) } + ColumnFamilyDescriptor("default".toByteArray())
        db = openRocksDB(cfg.dbPath, descriptors, handles)
        cfHandles = handles.associateBy { h -> h.name.toString(UTF_8) }
    }

    val users = UserStash(db, cfHandles["users"]!!, this)
    val artifacts = ArtifactStash(db, cfHandles["artifacts"]!!,
        cfHandles["versions"]!!, this)
    val changes = ChangeStash(db, cfHandles["changes"]!!,
        cfHandles["savePoints"]!!, this)
    val histories = HistoryStash(db, cfHandles["histories"]!!,
        cfHandles["historyVersions"]!!, this)
    val projects = ProjectStash(db, cfHandles["projects"]!!, this)
    val workspaces = WorkspaceStash(this, cfHandles["workspaces"]!!)

    fun close() {
        db.close()
    }

    fun agentFor(artifactType: String): Agent<out Any> {
        return agents.first { it.artifactType == artifactType }
    }

}