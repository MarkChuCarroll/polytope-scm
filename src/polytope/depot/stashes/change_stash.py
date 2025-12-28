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


from datetime import datetime
from typing import List
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.change import Change, ChangeStatus, SavePoint
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.user import Action, AuthenticatedUser
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash
from polytope.depot.stashes.user_stash import UserStash


class ChangeStash(Stash):
    """
    The stash for storing changes and savepoints within changes.

    Storage schema:

        stash[CHANGE][project: str][history: str][id: Id[Change]]: Change
        stash[CHANGE_INDEX][project: str][history: str][name: str]: Id[Change]
        stash[SAVEPOINT][project: str][history: str][id: Id[SavePoint]]: SavePoint
    """

    def __init__(self, depot: Depot) -> None:
        self.depot = depot
        self.changes = depot.db.get_collection("changes")
        self.saves = depot.db.get_collection("saves")

    def init_storage(self, config):
        pass

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def retrieve_change(
            self,
            auth: AuthenticatedUser,
            project: str,
            id: Id[Change]) -> Change:
        """
        Retrieve a change.
        auth -- the user performing the operation.
        project -- the project containing the change.
        id -- the change ID.
        @return the change
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        ch_dict = self.changes.find_one({"_id": id,
                                         "project": project
                                         })
        if ch_dict is None:
            raise PtException(ErrorKind.NotFound,
                              f"Change {id} not found")
        return Change.from_dict(ch_dict)

    def get_change_id(
            self,
            auth: AuthenticatedUser,
            project: str,
            history: str,
            change_name: str) -> Id[Change]:
        """
        Get the ID for a named change.

        Arguments:
        project -- the project containing the change.
        history -- the name of the history containing the change.
        change_name -- the name of the change.

        Returns the change ID.
        """
        ch = self.retrieve_change_by_name(auth, project, history, change_name)
        return ch.id

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
        change_name -- the name of the change.

        Returns the change
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        ch_dict = self.changes.find_one({"project": project,
                                         "history": history,
                                         "name": change_name})
        if ch_dict is None:
            raise PtException(ErrorKind.NotFound,
                              f"Change named {change_name} not found in history {history} of project {project}")
        return Change.from_dict(ch_dict)

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
            id=Id.new_id(IdKind.ID_CHANGE),
            project=project_name,
            name=change_name,
            history=history,
            baseline=baseline,
            basis=basis,
            description=description,
            save_points=[],
            status=ChangeStatus.Open,
            timestamp=datetime.now()
        )
        self.changes.insert_one(change.to_dict())
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
        update = self.changes.update_one({"project": project,
                                          "history": history,
                                          "name": change_name,
                                          },
                                         {"$set": {
                                             "status": status.value
                                         }})
        if update.modified_count != 1:
            raise PtException(ErrorKind.NotFound,
                              f"Change named {change_name} not found in history {history} of project {project}")

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
        auth - - the user performing the operation.
        project - - the project containing the change.
        history - - the name of the history containing the change.
        change_name - - the name of the change.
        changed_artifacts - - a list of the artifacts that were modified since the last
            baseline.
        description - - a description of the savepoint.
        basis - - a PVS for the starting point of the change.
        baseline_version - - the version ID of the new baseline.

        Returns a new savepoint.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        ch = self.retrieve_change_by_name(auth, project, history, change_name)
        save_point = SavePoint(
            id=Id.new_id(IdKind.ID_CHANGE_SAVE),
            change_id=ch.id,
            modified_artifacts=changed_artifacts,
            basis=basis,
            baseline_version=baseline_version,
            creator=auth.user_id,
            description=description,
            timestamp=datetime.now()
        )
        self.saves.insert_one(save_point.to_dict())
        self.changes.update_one({"_id": str(ch.id)},
                                {"$push": {
                                    "save_points": str(save_point.id)
                                }})
        return save_point

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
        change_name -- the name of the change.
        save_id -- the ID of the savepoint.

        Returns the savepoint
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        change_id = self.get_change_id(auth, project, history, change_name)
        sp_dict = self.saves.find_one({"_id": str(save_id), "change_id": change_id})
        if sp_dict is None:
            raise PtException(ErrorKind.NotFound, f"Save point not found in change {change_name}")
        return SavePoint.from_dict(sp_dict)

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
        auth - - the user performing the operation.
        project - - the project containing the change.
        history - - the name of the history containing the change.
        change - - the name of the change.

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
        changes = self.changes.find({"project": project, "history": history_name})
        result: List[Change] = []
        for ch in changes:
            match show:
                case ChangeStatus.Aborted:
                    result.append(Change.from_dict(ch))
                case ChangeStatus.Closed:
                    if ch.status == ChangeStatus.Closed or ch.status == ChangeStatus.Open:
                        result.append(Change.from_dict(ch))
                case ChangeStatus.Open:
                    if ch.status == ChangeStatus.Open:
                        result.append(Change.from_dict(ch.to_change()))
        return result
