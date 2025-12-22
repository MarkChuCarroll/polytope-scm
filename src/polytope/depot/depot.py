
from logging import config
from typing import Dict, List, cast
from polytope.common.agents.agents import Agent
from polytope.common.agents.baseline import BaselineAgent
from polytope.common.agents.directory import DirectoryAgent
from polytope.common.agents.text import TextAgent
from polytope.depot.config import Config
from polytope.depot.stashes.project_stash import ProjectStash
from polytope.depot.stashes.artifact_stash import ArtifactStash
from polytope.depot.stashes.change_stash import ChangeStash
from polytope.depot.stashes.history_stash import HistoryStash
from polytope.depot.stashes.stash import Stash
from polytope.depot.stashes.user_stash import UserStash


class Depot:

    def __init__(self, config: Config) -> None:
        self.db_dir = config.db_dir
        self.stashes: Dict[str, Stash] = {
            "user": UserStash(config.db_dir, self),
            "artifact": ArtifactStash(config.db_dir, self),
            "change": ChangeStash(config.db_dir, self),
            "history": HistoryStash(config.db_dir, self),
            "project": ProjectStash(config.db_dir, self),
        }

        agents: List[Agent] = [ TextAgent.get(), DirectoryAgent.get(),
                  BaselineAgent.get() ]
        self.agents = { agent.artifact_type: agent for agent in agents }

        for stash in self.stashes.values():
            stash.init_storage(config)

    @property
    def project_stash(self) -> ProjectStash:
        return cast(ProjectStash, self.stashes["project"])

    @property
    def user_stash(self) -> UserStash:
        return cast(UserStash, self.stashes["user"])

    @property
    def artifact_stash(self) -> ArtifactStash:
        return cast(ArtifactStash, self.stashes["artifacts"])

    @property
    def history_stash(self) -> HistoryStash:
        return cast(HistoryStash, self.stashes["history"])

    @property
    def change_stash(self) -> ChangeStash:
        return cast(ChangeStash, self.stashes["changes"])

