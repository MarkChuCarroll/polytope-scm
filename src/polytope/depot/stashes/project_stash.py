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
from typing import List, NamedTuple
from pymongo.synchronous.cursor import Cursor
from polytope.common.agents.baseline import Baseline
from polytope.common.agents.directory import Directory
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.history import History
from polytope.common.stashable.ids import Id
from polytope.common.stashable.project import Project
from polytope.common.stashable.pvs import PVSKind, ProjectVersionSpecifier
from polytope.common.stashable.user import Action
from polytope.common.stashable.stashable import JDict
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash
from polytope.depot.stashes.user_stash import AuthenticatedUser, UserStash
from polytope.depot.storage.storage import Content


class ProjectContents(NamedTuple):
    baseline: Artifact
    rootDir: Artifact
    history: History


class ProjectStash(Stash):
    def __init__(self, depot: Depot):
        self.depot = depot
        self.projects = self.depot.db.get_collection("projects")

    def init_storage(self, config):
        pass

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def resolve_project_version_specifier(
        self, auth: AuthenticatedUser, pvs: ProjectVersionSpecifier
    ) -> Id[ArtifactVersion]:
        """
        Compute the versionID of the project baseline associated with a project version
        specifier.

        Arguments:
        auth -- the authenticated user performing the operation
        pvs -- the project version specifier.

        Returns the version identifier of the baseline version that's
        specified by this PVS.
        """
        match pvs.kind:
            case PVSKind.Baseline:
                assert pvs.baseline is not None
                return pvs.baseline
            case PVSKind.History:
                hStep = self.depot.history_stash.retrieve_history_step(
                    auth, pvs.project, pvs.history, pvs.idx
                )
                return hStep.baseline_version_id
            case PVSKind.HistoryVersion:
                hStep = self.depot.history_stash.retrieve_history_step(
                    auth, pvs.project, pvs.history, pvs.idx
                )
                return hStep.baseline_version_id
            case PVSKind.Change:
                if pvs.idx is None:
                    assert pvs.change is not None
                    ch = self.depot.change_stash.retrieve_change_by_name(
                        auth, pvs.project, pvs.history, pvs.change
                    )
                    assert pvs.change is not None
                    latest = self.depot.change_stash.retrieve_save_point(
                        auth, pvs.project, pvs.history, pvs.change, ch.save_points[-1]
                    )
                    return latest.baseline_version
                else:
                    assert pvs.change is not None
                    ch = self.depot.change_stash.retrieve_change_by_name(
                        auth, pvs.project, pvs.history, pvs.change
                    )
                    if len(ch.save_points) <= pvs.idx:
                        raise PtException(
                            ErrorKind.NotFound,
                            f"No step with index {pvs.idx} "
                            + f"exists in change {pvs.change}",
                        )
                    assert pvs.change is not None
                    baseline = self.depot.change_stash.retrieve_save_point(
                        auth,
                        pvs.project,
                        pvs.history,
                        pvs.change,
                        ch.save_points[pvs.idx],
                    )
                    return baseline.baseline_version

    def create_project(
        self, auth: AuthenticatedUser, project_name: str, description: str
    ) -> Project:
        """
        Create a new project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project_name -- the name of the new project.
        description -- a description of the new project.

        Returns the new project.
        """
        self.user_stash.validate_permissions(auth, Action.create_project())
        if self.project_exists(auth, project_name):
            raise PtException(
                ErrorKind.Conflict, f"A project with name {project_name} already exists"
            )

        (baseline, root_dir, initial_history) = self.create_initial_contents(
            auth, project_name
        )
        project = Project(
            name=project_name,
            creator=auth.user_id,
            timestamp=datetime.now(),
            description=description,
            root_dir=root_dir.id,
            baseline=baseline.id,
            histories=[initial_history.name],
        )
        self.projects.insert_one(project.to_dict())
        return project

    def create_initial_contents(
        self, auth: AuthenticatedUser, project_name: str
    ) -> ProjectContents:
        """
        Create the initial contents of a new project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project_name -- the name of the new project
        """
        # Initial contents is a baseline and an empty directory,
        # plus a single history.  Maybe later set
        # up templates?
        dir = Directory({})
        dir_content_id = self.depot.storage.put(
            Content(self.depot.agents["directory"].encode_to_bytes(dir))
        )
        (dir_art, dir_version) = self.depot.artifact_stash.create_artifact(
            auth, project_name, "directory", dir_content_id, {}
        )

        baseline = Baseline(dir_art.id, {dir_art.id: dir_version.id})
        b_cid = self.depot.storage.put(
            Content(self.depot.agents["baseline"].encode_to_bytes(baseline))
        )
        (baseline_art, baseline_version) = self.depot.artifact_stash.create_artifact(
            auth, project_name, "baseline", b_cid, {}
        )

        history = self.depot.history_stash.create_initial_history(
            auth, project_name, baseline_version
        )
        return ProjectContents(baseline_art, dir_art, history)

    def retrieve_project(self, auth: AuthenticatedUser, name: str) -> Project:
        """
        Retrieve a project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project_name -- the name of the project

        Returns the project
        """

        self.user_stash.validate_permissions(auth, Action.read_project(name))
        pr_dict = self.projects.find_one({"name": name})
        if pr_dict is None:
            raise PtException(ErrorKind.NotFound, f"Project '{name}' not found")
        return Project.from_dict(pr_dict)

    def update_project(self, auth: AuthenticatedUser, project: Project) -> None:
        """
        Update the metadata of a stored project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the updated project object.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project.name))
        self.projects.replace_one({"name": project.name}, project.to_dict())

    def list_projects(self, auth: AuthenticatedUser) -> List[Project]:
        """
        Retrieve a list of all projects stored in the depot.

        Arguments:
        auth -- the authenticated user performing the operation.

        Returns the list of all projects.
        """
        self.user_stash.validate_permissions(auth, Action.read_depot())
        projects_dicts_cursor: Cursor[JDict] = self.projects.find({})
        projects = projects_dicts_cursor.to_list()
        result: List[Project] = []
        for p in projects:
            result.append(Project.from_dict(p))
        return result

    def project_exists(self, auth: AuthenticatedUser, project_name: str) -> bool:
        """
        Check if a project with a given name already exists.


        Arguments:
        auth -- the authenticated user performing the operation.
        project_name -- the name

        Returns True if a project with the name already exists.
        """
        self.user_stash.validate_permissions(auth, Action.read_depot())
        p = self.projects.find_one({"name": project_name})
        return p is not None

    def add_history_to_project(
        self, auth: AuthenticatedUser, project: str, history: str
    ) -> None:
        """
        Internal utility method to add the name of a newly created
        history to its parent project.
        """
        update = self.projects.update_one(
            {"name": project}, {"$push": {"histories": history}}
        )
        if update.matched_count != 1:
            raise PtException(ErrorKind.NotFound, f"Project {project} not found")
