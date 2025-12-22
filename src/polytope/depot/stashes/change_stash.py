
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
from typing import Dict, List
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.action import Action
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.change import Change, ChangeStatus, SavePoint
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.user import AuthenticatedUser
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash
from polytope.depot.stashes.user_stash import UserStash

CHANGE="change"
CHANGE_INDEX="change_idx"
SAVEPOINT = "savepoint"

class ChangeStash(Stash):
    """
    The stash for storing changes and savepoints within changes.

    Storage schema:
        stash[CHANGE][project: str][history: str][id: Id[Change]]: Change
        stash[CHANGE_INDEX][project: str][history: str][name: str]: Id[Change]
        stash[SAVEPOINT][project: str][history: str][id: Id[SavePoint]]: SavePoint
    """
    def __init__(self, db_dir: str, depot: Depot) -> None:
        self.db_dir = db_dir
        self.db_path = os.path.join(db_dir, "changes.db")
        self.depot = depot

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def retrieve_change(
        self,
        auth: AuthenticatedUser,
        project: str,
        id: Id[Change])-> Change:
        """
        Retrieve a change.
        auth -- the user performing the operation.
        project -- the project containing the change.
        id -- the change ID.
        @return the change
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        with shelve.open(self.db_path) as shelf:
            return self.get_in_shelf(shelf, [CHANGE, project, id],
                                    f"Change {id} not found")

    def get_change_id(
        self,
        project: str,
        history: str,
        change_name: str) -> Id[Change]:
        """
        Get the ID for a named change.

        Arguments:
        project -- the project containing the change.
        history -- the name of the history containing the change.
        changeName -- the name of the change.

        Returns the change ID.
        """
        with shelve.open(self.db_path) as shelf:
            return self.get_in_shelf(shelf, [CHANGE_INDEX, project, history, change_name],
                    f"Change named {change_name} not found in history {history} of project {project}")


    def retrieve_change_by_name(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change_name: str) -> Change:
        """
        Retrieve a change by name.

        Arguments:
        auth -- the user performing the operation.
        project -- the project containing the change.
        history -- the name of the history containing the change
        changeName -- the name of the change.

        Returns the change
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        id = self.get_change_id(project, history, change_name)
        return self.retrieve_change(auth, project, id)

    def create_change(
        self,
        auth: AuthenticatedUser,
        project_name: str,
        history: str,
        change_name: str,
        basis: ProjectVersionSpecifier,
        description: str
    ) -> Change:
        """
        Create a new change

        Arguments:
        auth -- the user performing the operation.
        project_name -- the project containing the change.
        history -- the name of the history that will contain the change.
        change_name -- the name of the new change.
        basis -- the project version specifier of the baseline of the change.
        description -- a description of the change.

        Returns the new change.
        """

        self.user_stash.validate_permissions(auth, Action.write_project(project_name))
        project = self.depot.project_stash.retrieve_project(auth, project_name)
        baseline = self.depot.project_stash.resolve_project_version_specifier(auth, basis)
        change = Change(
            id = Id.new_id(IdKind.ID_CHANGE),
            project = project_name,
            name = change_name,
            history = history,
            baseline = baseline,
            basis = basis,
            description = description,
            save_points = [],
            status = ChangeStatus.Open,
            timestamp = datetime.now()
        )
        with shelve.open(self.db_path) as shelf:
            self.set_in_shelf(shelf, [CHANGE, project, history, change.id], change)
            self.set_in_shelf(shelf, [CHANGE_INDEX, project, history, change.name], change.id)
        return change


    def update_change_status(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change_name: str,
        status: ChangeStatus
    ) -> None:
        """
        Update the status of an in-progress change.
        auth -- the user performing the operation.
        project -- the project containing the change.
        history -- the history containing the change.
        change_name -- the name of the change.
        status -- the new status of the change.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        changeId = self.get_change_id(project, history, change_name)
        change = self.retrieve_change(auth, project, changeId)
        updated = copy.replace(change, timestamp = datetime.now(),
            status = status)
        with shelve.open(self.db_path) as shelf:
            self.set_in_shelf(shelf, [CHANGE, project, history, changeId], updated)


    def create_save_point(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change_name: str,
        changed_artifacts: List[Id[Artifact]],
        description: str,
        basis: ProjectVersionSpecifier,
        baseline_version: Id[ArtifactVersion]
    ) -> SavePoint:
        """
        Create a save point in an open change.

        Arguments:
        auth -- the user performing the operation.
        project -- the project containing the change.
        history -- the name of the history containing the change.
        change_name -- the name of the change.
        changed_artifacts -- a list of the artifacts that were modified since the last
            baseline.
        description -- a description of the savepoint.
        basis -- a PVS for the starting point of the change.
        baseline_version -- the version ID of the new baseline.

        Returns a new savepoint.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        ch = self.retrieve_change_by_name(auth, project, history, change_name)
        savePoint = SavePoint(
            id = Id.new_id(IdKind.ID_CHANGE_SAVE),
            change_id = ch.id,
            modified_artifacts = changed_artifacts,
            basis = basis,
            baseline_version = baseline_version,
            creator = auth.user_id,
            description = description,
            timestamp = datetime.now()
        )
        updatedChange = copy.replace(ch, savePoints = ch.save_points + [savePoint.id])
        with shelve.open(self.db_path) as shelf:
            self.set_in_shelf(shelf, [CHANGE, project, history, ch.id], updatedChange)
            self.set_in_shelf(shelf, [SAVEPOINT, project, history, savePoint.id], savePoint)
        return savePoint

    def retrieve_save_point(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change_name: str,
        save_id: Id[SavePoint]
    ) -> SavePoint:
        """
        Retrieve a savepoint

        Arguments:
        auth -- the user performing the operation.
        project -- the project containing the change.
        history -- the name of the history containing the change.
        changeName -- the name of the change.
        save_id -- the ID of the savepoint.

        Returns the savepoint
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        ch = self.get_change_id(project, history, change_name)
        with shelve.open(self.db_path) as shelf:
            sp: SavePoint = self.get_in_shelf(shelf, [SAVEPOINT, project, history, save_id],
                    "Save point not found")
            if sp.change_id != ch:
                raise PtException(ErrorKind.NotFound,
                    f"Save point not found in change {change_name}")
            return sp


    def list_save_points(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        change: str
    ) -> List[Id[SavePoint]]:
        """
        List the save points in a change.

        Arguments:
        auth -- the user performing the operation.
        project -- the project containing the change.
        history -- the name of the history containing the change.
        change -- the name of the change.

        Returns a list of save point IDs.
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        ch = self.retrieve_change_by_name(auth, project, history, change)
        return ch.save_points


    def list_changes(
        self,
        auth: AuthenticatedUser,
        project: str,
        history_name: str,
        show: ChangeStatus
    ) -> List[Change]:
        """
        List the changes in a history.

        Arguments:
        auth -- the user performing the operation.
        project -- the project containing the change.
        history_name -- the name of the history.
        show -- the minimum status to include in the result list. If this is "Aborted", then
           all changes, open, closed, and aborted will be included; if it's "Closed", then both open
           and closed will be included, but aborted changes will not; if it's "Open", then all changes
           will be included

        Returns a list of changes.
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        with shelve.open(self.db_path) as shelf:
            all_changes: Dict[Id[Change], Change] = self.get_in_shelf(shelf, [CHANGE, project, history_name],
                                                            "Change not found")
            result: List[Change] = []
            for id in all_changes.keys():
                ch: Change =  all_changes[id]
                match show:
                    case ChangeStatus.Aborted:
                        result.append(ch)
                    case ChangeStatus.Closed:
                        if ch.status == ChangeStatus.Closed or ch.status ==ChangeStatus.Open:
                            result.append(ch)
                    case ChangeStatus.Open:
                        if ch.status == ChangeStatus.Open:
                            result.append(ch)
            return result

    def init_storage(self, config: Config):
        with shelve.open(self.db_path) as shelf:
            if shelf.get(CHANGE) is None:
                shelf[CHANGE] = {}
            if shelf.get(CHANGE_INDEX) is None:
                shelf[CHANGE_INDEX] = {}
            if shelf.get(SAVEPOINT) is None:
                shelf[SAVEPOINT] = {}

