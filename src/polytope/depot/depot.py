from re import S
from typing import TYPE_CHECKING, Dict, List, cast

import os
from pymongo import MongoClient


from polytope.depot.config import Config
from polytope.depot.stashes.stash import Stash
from polytope.depot.storage.storage import FileStorage

if TYPE_CHECKING:
    from polytope.common.agents.agents import Agent
    from polytope.common.agents.baseline import BaselineAgent
    from polytope.common.agents.directory import DirectoryAgent
    from polytope.common.agents.text import TextAgent
    from polytope.depot.stashes.artifact_stash import ArtifactStash
    from polytope.depot.stashes.change_stash import ChangeStash
    from polytope.depot.stashes.history_stash import HistoryStash
    from polytope.depot.stashes.project_stash import ProjectStash
    from polytope.depot.stashes.user_stash import UserStash


class Depot:
    """The storage system for all polytope data."""

    def __init__(self, config: Config) -> None:
        """Initialize a depot."""
        print(f"Connection str: {config["db"]["connection_str"]}")
        self.mongo: MongoClient = MongoClient(config["db"]["connection_str"])
        self.db = self.mongo.get_database(config["db"]["db_name"])

        self.storage = FileStorage(config["storage"]["storage_path"])
        from polytope.depot.stashes.artifact_stash import ArtifactStash
        from polytope.depot.stashes.change_stash import ChangeStash
        from polytope.depot.stashes.history_stash import HistoryStash
        from polytope.depot.stashes.project_stash import ProjectStash
        from polytope.depot.stashes.user_stash import UserStash
        from polytope.common.agents.directory import DirectoryAgent
        from polytope.common.agents.baseline import BaselineAgent
        from polytope.common.agents.text import TextAgent

        agents: List[Agent] = [
            TextAgent(self.storage),
            DirectoryAgent(self.storage),
            BaselineAgent(self.storage)
        ]
        self.agents = {agent.artifact_type(): agent for agent in agents}

        self.user_stash = UserStash(self)
        self.artifact_stash = ArtifactStash(self)
        self.change_stash = ChangeStash(self)
        self.history_stash = HistoryStash(self)
        self.project_stash = ProjectStash(self)
        self.user_stash.init_storage(config)
        self.artifact_stash.init_storage(config)
        self.change_stash.init_storage(config)
        self.history_stash.init_storage(config)
        self.project_stash.init_storage(config)

    def get_agent(self, s: str) -> Agent:
        return self.agents[s]
