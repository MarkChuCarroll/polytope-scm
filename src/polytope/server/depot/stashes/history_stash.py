# Copyright 2026 Mark C. Chu-Carroll
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
from pymongo.synchronous.cursor import Cursor

from typing import List
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import ArtifactVersion
from polytope.common.stashable.change import Change
from polytope.common.stashable.history import History, HistoryStep
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.pvs import PVSKind, ProjectVersionSpecifier
from polytope.common.stashable import JDict
from polytope.common.stashable.user import Action, AuthenticatedUser
from polytope.server.depot import Depot
from polytope.server.depot.stashes.stash import Stash
from polytope.server.depot.stashes.user_stash import UserStash

INITIAL_HISTORY_NAME = "main"


class HistoryStash(Stash):
    def __init__(self, depot: Depot) -> None:
        self.depot = depot
        self.histories = self.depot.db.get_collection("histories")
        self.steps = self.depot.db.get_collection("steps")

    def init_storage(self, config):
        pass

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def retrieve_history(
        self, auth: AuthenticatedUser, project: str, history: str
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
        h_dict = self.histories.find_one({"project": project, "name": history})

        if h_dict is None:
            raise PtException(
                ErrorKind.NotFound, f"History {history} not found in project {project}"
            )

        return History.from_dict(h_dict)

    def retrieve_history_step(
        self,
        auth: AuthenticatedUser,
        project: str,
        history_name: str,
        number: int | None = None,
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
            s_dict = self.steps.find_one({"_id": str(history.steps[step])})
            if s_dict is not None:
                return HistoryStep.from_dict(s_dict)
        raise PtException(
            ErrorKind.NotFound, f"History step {history_name}@{number} not found"
        )

    def currentStep(self, auth: AuthenticatedUser, project: str, history: str) -> int:
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
        limit: int | None = None,
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
        at_step: int | None = None,
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
        if any([h for h in self.list_histories(auth, project) if h[0] == name]):
            raise PtException(
                ErrorKind.Conflict,
                f"Project {project} already has a history named {name}",
            )
        baseStep = self.retrieve_history_step(auth, project, from_history, at_step)
        history_first_step = copy.replace(
            baseStep,
            id=Id.new_id(IdKind.ID_HISTORY_STEP),
            history_name=name,
            idx=0,
            description="branch into new history",
        )
        history = History(
            project=project,
            name=name,
            description=description,
            timestamp=datetime.now(),
            basis=ProjectVersionSpecifier(
                PVSKind.History, project, from_history, idx=at_step
            ),
            steps=[history_first_step.id],
        )
        self.histories.insert_one(history.to_dict())
        self.steps.insert_one(history_first_step.to_dict())
        self.depot.project_stash.add_history_to_project(auth, project, history.name)
        return history

    def add_history_step(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change: Id[Change],
        baseline_version: ArtifactVersion,
        description: str,
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
        new_step = HistoryStep(
            id=Id.new_id(IdKind.ID_HISTORY_STEP),
            project=project,
            history_name=history,
            idx=len(hist.steps),
            baseline_id=baseline_version.artifact_id,
            baseline_version_id=baseline_version.id,
            change=change,
            description=description,
        )
        self.steps.insert_one(new_step.to_dict())
        update = self.histories.update_one(
            {"project": project, "name": history},
            {"$push": {"steps": str(new_step.id)}},
        )
        if update.modified_count != 1:
            raise PtException(
                ErrorKind.NotFound,
                f"Failed to update steps, because history {history} was not found",
            )
        return new_step

    def list_histories(
        self, auth: AuthenticatedUser, project: str
    ) -> List[History]:
        """
        List the histories of a project

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the history.

        Returns the list of histories
        """
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        histories_cursor: Cursor[JDict] = self.histories.find({"project": project})
        histories = histories_cursor.to_list()
        if len(histories) == 0:
            raise PtException(
                ErrorKind.NotFound, f"No histories found for project {project}"
            )
        return list(History.from_dict(h) for h in histories)

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
            ProjectVersionSpecifier(
                PVSKind.Baseline,
                project,
                INITIAL_HISTORY_NAME,
                baseline=baseline_version.id,
            ),
            steps=[],
        )

        step = HistoryStep(
            id=Id.new_id(IdKind.ID_HISTORY_STEP),
            project=project,
            history_name=INITIAL_HISTORY_NAME,
            idx=0,
            baseline_id=baseline_version.artifact_id,
            baseline_version_id=baseline_version.id,
            change=None,
            description="initial project version",
        )

        history.steps.append(step.id)
        self.histories.insert_one(history.to_dict())
        self.steps.insert_one(step.to_dict())
        return history
