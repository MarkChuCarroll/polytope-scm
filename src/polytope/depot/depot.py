# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http: // www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from typing import TYPE_CHECKING, List
from pymongo import MongoClient

from polytope.depot.config import Config
from polytope.depot.storage.storage import FileStorage

if TYPE_CHECKING:
    from polytope.common.agents.agents import Agent


class Depot:
    """The storage system for all polytope data."""

    def __init__(self, config: Config) -> None:
        """Initialize a depot."""
        self.mongo: MongoClient = MongoClient(config["db"]["connection_str"])
        self.db = self.mongo.get_database(config["db"]["db_name"])

        self.storage = FileStorage(config["storage"]["storage_path"])
        from polytope.depot.stashes.artifact_stash import ArtifactStash
        from polytope.depot.stashes.change_stash import ChangeStash
        from polytope.depot.stashes.history_stash import HistoryStash
        from polytope.depot.stashes.project_stash import ProjectStash
        from polytope.depot.stashes.user_stash import UserStash
        from polytope.depot.stashes.workspace_stash import WorkspaceStash

        from polytope.common.agents.directory import DirectoryAgent
        from polytope.common.agents.baseline import BaselineAgent
        from polytope.common.agents.text import TextAgent

        agents: List[Agent] = [
            TextAgent(self.storage),
            DirectoryAgent(self.storage),
            BaselineAgent(self.storage),
        ]
        self.agents = {agent.artifact_type(): agent for agent in agents}

        self.user_stash = UserStash(self)
        self.artifact_stash = ArtifactStash(self)
        self.change_stash = ChangeStash(self)
        self.history_stash = HistoryStash(self)
        self.project_stash = ProjectStash(self)
        self.workspace_stash = WorkspaceStash(self)
        self.user_stash.init_storage(config)
        self.artifact_stash.init_storage(config)
        self.change_stash.init_storage(config)
        self.history_stash.init_storage(config)
        self.project_stash.init_storage(config)
        self.workspace_stash.init_storage(config)

    def get_agent(self, s: str) -> "Agent":
        return self.agents[s]
