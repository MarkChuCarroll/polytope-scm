# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import copy
from datetime import datetime
import os
import shelve
from typing import List
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.action import Action
from polytope.common.stashable.artifact import ArtifactVersion
from polytope.common.stashable.change import Change
from polytope.common.stashable.history import History, HistoryStep
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.pvs import PVSKind, ProjectVersionSpecifier
from polytope.common.stashable.user import AuthenticatedUser
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash
from polytope.depot.stashes.user_stash import UserStash


HISTORY = "history"
STEP = "step"
INITIAL_HISTORY_NAME = "main"

# TODO: histories are always accessed by name, so we should
# remove the ID type.
class HistoryStash(Stash):
    """
    Schema:
        shelf[HISTORY][project: str][name: str]: History
        shelf[STEP][project: str][name: str][history: str][id: Id[HistoryStep]]: HistoryStep
    """
    def __init__(self, db_dir: str, depot: Depot) -> None:
        self.db_dir = db_dir
        self.db_path = os.path.join(db_dir, "history.db")
        self.depot = depot

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def retrieve_history(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str
    ) -> History:
        """
        Retrieve a history.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        history -- the name of the history

        Returns the history
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        with shelve.open(self.db_path) as shelf:
            if shelf[HISTORY].get(project) is None or shelf[HISTORY][project].get(history) is None:
                raise PtException(ErrorKind.NotFound,
                    f"History {history} not found in project {project}"
                )
            return shelf[HISTORY][project][history]


    def retrieve_history_step(
        self,
        auth: AuthenticatedUser,
        project: str,
        history_name: str,
        number: int | None
    ) -> HistoryStep:
        """
        Retrieve a specific step of a history.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        historyName -- the name of the history.
        number -- the step number to retrieve. If null, then this will
        retrieve the most recent step.

        Returns the history step.
        """
        history = self.retrieve_history(auth, project, history_name)
        if number is None:
            step = len(history.steps) - 1
        else:
            step = number

        if step < len(history.steps):
            with shelve.open(self.db_path) as shelf:
                return shelf[STEP][history.steps[step]]
        else:
            raise PtException(ErrorKind.NotFound,
                f"History step {history_name}@{number} not found")

    def currentStep(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str
    ) -> int:
        """
        Retrieve the step number of the most recent step in a history.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        history -- the name of the history.

        Returns the step index
        """
        return len(self.retrieve_history(auth, project, history).steps) - 1

    def list_history_steps(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        limit: int | None = None
    ) -> List[Id[HistoryStep]]:
        """
        Retrieve a list of the steps in a history.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        history -- the name of the history.
        limit -- the maximum number of steps to retrieve. If null,
            then all steps in the history will be retrieved.

        Returns a list of history steps
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        hist = self.retrieve_history(auth, project, history)
        if limit is not None:
            return hist.steps[-limit:]
        else:
            return hist.steps


    def create_history(
        self,
        auth: AuthenticatedUser,
        project: str,
        name: str,
        description: str,
        from_history: str,
        at_step: int | None = None
    ) -> History:
        """
        Create a new history

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        name -- the name of the new history to create.
        description -- a description of the new history.
        from_history -- the name of the history that the new history branches from.
        at_step -- the step number of the history to use as a starting point. If null,
            then this will use the most recent step.

        Return the new history
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        if self.history_exists_in_project(project, name):
            raise PtException(ErrorKind.Conflict,
                f"Project {project} already has a history named {name}")
        baseStep = self.retrieve_history_step(auth, project, from_history, at_step)
        historyFirstStep = copy.replace(baseStep,
            id = Id.new_id( IdKind.ID_HISTORY_STEP),
            history_name = name,
            number = 0,
            description = "branch into new history")
        history = History(
            project=project,
            name=name,
            description=description,
            timestamp=datetime.now(),
            basis=ProjectVersionSpecifier(PVSKind.History, project, from_history,
                                          idx=at_step),
            steps = [historyFirstStep.id])

        with shelve.open(self.db_path) as shelf:
            shelf[HISTORY][project][name] = history
            shelf[STEP][project][historyFirstStep.id] = historyFirstStep
        return history


    def history_exists_in_project(self, project: str, name: str) -> bool:
        """
        Check if a history exists in a project

        Arguments:
        project -- the name of the project containing the history.
        name -- the name of the history.

        Returns true if a history with the name already exists in the project.
        """
        with shelve.open(self.db_path) as shelf:
            return name in shelf[HISTORY][project]



    def add_history_step(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change: Id[Change],
        baseline_version: ArtifactVersion,
        description: str
    ) -> HistoryStep:
        """
        Add a new step to a history.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        history -- the name of the history.
        change -- the ID of the change being added to the history.
        baseline_version -- the final baseline of the change being added to the history.
        description -- a description of the change.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        hist = self.retrieve_history(auth, project, history)
        newStep = HistoryStep(
            id = Id.new_id(IdKind.ID_HISTORY_STEP),
            project = project,
            history_name = history,
            idx = len(hist.steps),
            baseline_id = baseline_version.artifact_id,
            baseline_version_id = baseline_version.id,
            change = change,
            description = description
        )
        hist.steps.append(newStep.id)
        with shelve.open(self.db_path) as shelf:
            shelf[HISTORY][project][history] = hist
            shelf[STEP][project][newStep.id] = newStep
        return newStep


    def list_histories(
        self,
        auth: AuthenticatedUser,
        project: str
    ) -> List[str]:
        """
        List the histories of a project

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.

        Returns the list of histories
        """
        with shelve.open(self.db_path) as shelf:
            return list(shelf[HISTORY][project].keys())


    def create_initial_history(
        self,
        auth: AuthenticatedUser,
        project: str,
        baseline_version: ArtifactVersion,
    ) -> History:
        """
        Create the initial history for a new project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.
        baseline_version -- the initial baseline of the history.

        Returns the new history.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        history = History(
            project,
            INITIAL_HISTORY_NAME,
            "initial project history",
            datetime.now(),
            ProjectVersionSpecifier(PVSKind.Baseline, project, INITIAL_HISTORY_NAME,
                                    baseline=baseline_version.id),
            steps = [])

        step = HistoryStep(
            id = Id.new_id(IdKind.ID_HISTORY_STEP),
            project = project,
            history_name = INITIAL_HISTORY_NAME,
            idx = 0,
            baseline_id = baseline_version.artifact_id,
            baseline_version_id = baseline_version.id,
            change = None,
            description = "initial project version")

        history.steps.append(step.id)
        with shelve.open(self.db_path) as shelf:
            shelf[HISTORY][project] = {}
            shelf[HISTORY][project][history.name] = history
            shelf[STEP][project] = {}
            shelf[STEP][project][step.id] = step
        return history

    def init_storage(self, config: Config) -> None:
        with shelve.open(self.db_path) as shelf:
            if shelf.get(HISTORY) is None:
                shelf[HISTORY] = {}
            if shelf.get(STEP) is None:
                shelf[STEP] = {}

