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


from datetime import datetime
import re
from typing import Dict, List, Tuple, Set

from polytope.common.agents.agents import MergeConflict, MergeResult
from polytope.common.agents.baseline import (
    Baseline,
    BaselineConflict,
    BaselineConflictType,
)
from polytope.common.agents.directory import Directory
from polytope.common.api.requests import WorkspaceFileContents
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.change import Change, ChangeStatus, SavePoint
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.stashable import JDict
from polytope.common.stashable.user import Action, AuthenticatedUser
from polytope.common.stashable.workspace import Workspace, WorkspaceDescriptor
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash


class WorkspaceIndexKey:
    def __init__(self, project: str, name: str) -> None:
        self.project = project
        self.name = name

    def __str__(self) -> str:
        return f"{self.project}/{self.name}"

    parser = re.compile(r"([\w\d_-]+)/([\w\d_-]+)")

    @classmethod
    def from_str(cls, s: str) -> "WorkspaceIndexKey":
        match = cls.parser.fullmatch(s)
        if match is not None:
            return cls(match.group(1), match.group(2))
        else:
            raise PtException(ErrorKind.Parsing, f"Invalid workspace index key '{s}'")


class WorkspaceIndex:
    INDEX_KEY = "__WORKSPACE__INDEX__"

    def __init__(self, entries: Dict[WorkspaceIndexKey, Id[Workspace]]) -> None:
        self.entries = entries

    def get(self, project: str, name: str) -> Id[Workspace] | None:
        return self.entries.get(WorkspaceIndexKey(project, name))

    def add(self, project: str, name: str, id: Id[Workspace]):
        self.entries[WorkspaceIndexKey(project, name)] = id

    def to_dict(self) -> JDict:
        return {
            "_id": self.INDEX_KEY,
            "entries": dict(
                {str(key): str(value) for (key, value) in self.entries.items()}
            ),
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "WorkspaceIndex":
        return cls(
            dict(
                {
                    WorkspaceIndexKey.from_str(k): Id.from_string(v)
                    for k, v in d["entries"]
                }
            )
        )


class WorkspaceStash(Stash):
    def __init__(self, depot: Depot) -> None:
        self.depot: Depot = depot
        self.workspaces = self.depot.db.get_collection("workspaces")
        self.ws_cache: Dict[Id[Workspace], Workspace] = {}
        self.cached_baselines: Dict[Id[ArtifactVersion], Baseline] = {}
        self.ws_index: WorkspaceIndex | None = None

    def init_storage(self, config: Config) -> None:
        if self.workspaces.find_one({"_id": WorkspaceIndex.INDEX_KEY}) is None:
            idx = WorkspaceIndex({})
            self.workspaces.insert_one(idx.to_dict())

    @property
    def index(self) -> WorkspaceIndex:
        if self.ws_index is None:
            idx = self.workspaces.find_one({"_id": WorkspaceIndex.INDEX_KEY})
            if idx is not None:
                self.ws_index = WorkspaceIndex.from_dict(idx)
            else:
                raise PtException(
                    ErrorKind.Internal, "Workspace index could not be loaded"
                )
        return self.ws_index

    def _add_to_index(self, project: str, name: str, id: Id[Workspace]) -> None:
        idx = self.index
        idx.add(project, name, id)
        self.workspaces.update_one(
            {"_id": WorkspaceIndex.INDEX_KEY},
            {"$set": {f"entries.{str(WorkspaceIndexKey(project, name))}": str(id)}},
        )

    def create_workspace(
        self,
        auth: AuthenticatedUser,
        project: str,
        history: str,
        name: str,
        description: str,
    ) -> Workspace:
        """
        Create a new workspace for a project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project
        name -- the name of the new workspace
        history -- the name of the history that will initially populate the workspace.
        description -- a description of the workspace.

        Returns an initialized, populated workspace.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        if self.workspace_exists(auth, project, name):
            raise PtException(
                ErrorKind.Conflict,
                f"A workspace named '{name}' already exists in project '{project}'",
            )
        hist_step = self.depot.history_stash.retrieve_history_step(
            auth, project, history
        )
        ws = Workspace(
            id=Id.new_id(IdKind.ID_WORKSPACE),
            name=name,
            project=project,
            history=history,
            baseline_id=hist_step.baseline_id,
            baseline_version=hist_step.baseline_version_id,
            creator=auth.user_id,
            created_at=datetime.now(),
            last_modified=datetime.now(),
            basis=ProjectVersionSpecifier.make_history(
                project, hist_step.history_name, hist_step.idx
            ),
            description=description,
            change=None,
            working_versions={},
        )

        self.workspaces.insert_one(ws.to_dict())
        self._add_to_index(project, name, ws.id)
        self.ws_cache[ws.id] = ws
        return ws

    def list_workspaces(
        self, auth: AuthenticatedUser, project_name: str, name_pattern: str | None
    ) -> List[WorkspaceDescriptor]:
        """
        Get a list of the workspaces created under a project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project_name -- the name of the project.
        name_pattern -- an optional regex; if provided, only workspaces whose name
           matches the pattern will be returned.

        Returns a list of workspace descriptors
        """
        result: List[WorkspaceDescriptor] = []
        pat: re.Pattern | None = None
        if name_pattern is not None:
            pat = re.compile(name_pattern)
        for key, id in self.index.entries.items():
            if key.project == project_name and (
                pat is None or pat.fullmatch(key.name) is not None
            ):
                ws = self.retrieve_workspace_by_id(auth, project_name, id)
                result.append(
                    WorkspaceDescriptor(
                        id=id,
                        ws_name=ws.name,
                        project=project_name,
                        creator=ws.creator,
                        description=ws.description,
                        created_at=ws.created_at,
                        last_modified=ws.last_modified,
                    )
                )
        return result

    def workspace_exists(
        self, auth: AuthenticatedUser, project: str, name: str
    ) -> bool:
        """
        Check if a workspace with a name exists in a project.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project
        name -- the name

        Returns True if a workspace with the name exists in the project.
        """
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        return self.index.get(project, name) is not None

    def get_identifier_for_workspace(
        self, auth: AuthenticatedUser, project: str, name: str
    ) -> Id[Workspace]:
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        w = self.index.get(project, name)
        if w is None:
            raise PtException(
                ErrorKind.NotFound, f"Workspace {name} not found in project {project}"
            )
        else:
            return w

    def retrieve_workspace_by_id(
        self, auth: AuthenticatedUser, project: str, id: Id[Workspace]
    ) -> Workspace:
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        if id in self.ws_cache:
            ws = self.ws_cache[id]
            if ws.project != project:
                raise PtException(
                    ErrorKind.NotFound, f"Workspace {id} not found in project {project}"
                )
            return ws
        record = self.workspaces.find_one({"_id": str(id)})
        if record is None:
            raise PtException(
                ErrorKind.NotFound, f"workspace {id} not found in project {project}"
            )
        ws = Workspace.from_dict(record)
        self.ws_cache[id] = ws
        if record["project"] != project:
            raise PtException(
                ErrorKind.NotFound, f"workspace {id} not found in project {project}"
            )
        return ws

    def retrieve_workspace(
        self, auth: AuthenticatedUser, project: str, name: str
    ) -> Workspace:
        """
        Retrieve a workspace. Workspaces are cached, so this will only read from the
        database if the workspace isn't already in the cache.

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the name of the project containing the workspace.
        name -- the name of the workspace
        """

        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        id = self.get_identifier_for_workspace(auth, project, name)
        return self.retrieve_workspace_by_id(auth, project, id)

    # Methods for working with individual workspaces

    def _update_stored_workspace(self, ws: Workspace) -> None:
        """Update the stored version of the workspace after a change."""
        result = self.workspaces.replace_one({"_id": str(ws.id)}, ws.to_dict())
        if result.modified_count == 0:
            raise PtException(ErrorKind.Internal, "Workspace update failed")
        self.ws_cache[ws.id] = ws

    def _get_baseline_version(
        self, auth: AuthenticatedUser, ws: Workspace, id: Id[ArtifactVersion]
    ) -> ArtifactVersion:
        return self.depot.artifact_stash.retrieve_version(
            auth, ws.project, ws.baseline_id, id
        )

    def _get_baseline(
        self, auth: AuthenticatedUser, ws: Workspace, id: Id[ArtifactVersion]
    ) -> Baseline:
        base = self._get_baseline_version(auth, ws, id)
        return self.depot.agents["baseline"].decode_from_bytes(
            self.depot.storage.get(base.content_id)
        )

    def _current_baseline(self, auth: AuthenticatedUser, ws: Workspace) -> Baseline:
        return self._get_baseline(auth, ws, ws.baseline_version)

    # *************************************************
    #  workspace population methods.
    # *************************************************

    def create_change(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        history: str,
        change_name: str,
        description: str,
    ) -> Change:
        """
        Create a new change, and set up the workspace to operate in it.

        The basis for the change will be the current basis of the workspace.
        To choose a starting point, just populate the workspace with whatever
        basis you want, and then start a change.

        Most of the time, the starting basis will be the current version
        of a history; while it's possible to do other things, you can't
        deliver a change to a history without being up-to-date with it,
        so you'll need to do some kind of merging to be able to deliver.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the project containing the workspace
        ws_id -- the ID of the workspace
        history -- the name of the history which will contain the new change
        change_name -- the name of the new change
        description -- a brief description of the change.

        Returns the newly created change.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        self._ensure_no_unsaved_changes(ws)
        if ws.history != history:
            self.open_history(auth, project, wsid, history)
        ch = self.depot.change_stash.create_change(
            auth, ws.project, ws.history, change_name, ws.basis, description
        )
        ws.change = ch.name
        self._update_stored_workspace(ws)
        return ch

    def _ensure_no_unsaved_changes(self, ws: Workspace):
        if len(ws.modified_artifacts) > 0 or len(ws.working_versions) > 0:
            raise PtException(ErrorKind.Constraint, "Unsaved changes in workspace")

    def open_history(
        self, auth: AuthenticatedUser, project: str, wsid: Id[Workspace], history: str
    ) -> Workspace:
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        self._ensure_no_unsaved_changes(ws)
        ws.history = history
        ws.change = None
        basis = ProjectVersionSpecifier.make_history(ws.project, history)
        self.set_basis(auth, project, ws.id, basis)

        return ws

    def open_change(
        self,
        auth: AuthenticatedUser,
        wsid: Id[Workspace],
        project: str,
        history: str,
        change_name: str,
    ) -> Workspace:
        """
        Configure and populate the workspace for an existing in -progress change.

        Arguments:
        auth -- the authenticated user performing the action.
        ws_id -- the workspace
        project -- the project containing the workspace
        history -- the name of the history containing the change.
        change_name -- the name of the change.

        Returns the workspace, updated with the open chane.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if len(ws.modified_artifacts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "A new change can't be opened in a workspace with unsaved changes",
            )
        ws.change = None
        ws.history = history
        basis = ProjectVersionSpecifier.make_change(ws.project, history, change_name)
        self.set_basis(auth, project, ws.id, basis)
        return ws

    def set_basis(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        project_version_specifier: ProjectVersionSpecifier,
    ) -> None:
        """
        Populate the workspace from a basis point. Most of the time, this should
        be a history version.

        Arguments:
        auth -- the authenticated user performing the action.
        wsid -- the workspace id
        projectVersionSpecifier
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if len(ws.modified_artifacts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Basis can't be changed in a workspace with unsaved changes",
            )
        if len(ws.conflicts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Basis can't be changed in a workspace with unresolved conflicts",
            )

        resolved = self.depot.project_stash.resolve_project_version_specifier(
            auth, project_version_specifier
        )
        ws.basis = project_version_specifier
        ws.baseline_version = resolved
        self._update_stored_workspace(ws)

    def get_root_dir(self, auth: AuthenticatedUser, ws: Workspace) -> Directory:
        """
        Get the current root directory of the workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        ws -- the workspace
        Returns the directory
        """
        baseline = self._current_baseline(auth, ws)
        dir_version = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, baseline.root_dir, baseline.entries[baseline.root_dir]
        )
        dir_content = self.depot.storage.get(dir_version.content_id)
        return self.depot.agents["directory"].decode_from_bytes(dir_content)

    def _get_artifact_at_path(
        self, auth: AuthenticatedUser, ws: Workspace, path_parts: List[str]
    ) -> Id[Artifact]:
        cbl = self._current_baseline(auth, ws)
        root_dir = cbl.root_dir
        art_id: Id[Artifact] = root_dir
        for part in path_parts:
            art = self.depot.artifact_stash.retrieve_version(
                auth, ws.project, art_id, cbl.entries[art_id]
            )
            if art.artifact_type != "directory":
                raise PtException(
                    ErrorKind.NotFound, f"Invalid path {'/'.join(path_parts)}"
                )
            dir = self.depot.agents["directory"].decode_from_bytes(
                self.depot.storage.get(art.content_id)
            )
            next_art_id = dir.get_binding(part)
            if next_art_id is None:
                raise PtException(
                    ErrorKind.NotFound,
                    f"Path {'/'.join(path_parts)} not found in workspace",
                )
            art_id = next_art_id

        return art_id

    def _path_exists_in_workspace(
        self, auth: AuthenticatedUser, ws: Workspace, path_parts: List[str]
    ) -> bool:
        cbl = self._current_baseline(auth, ws)
        root_dir = cbl.root_dir
        art_id: Id[Artifact] = root_dir
        for part in path_parts:
            art = self.depot.artifact_stash.retrieve_version(
                auth, ws.project, art_id, cbl.entries[art_id]
            )
            if art.artifact_type != "directory":
                raise PtException(
                    ErrorKind.NotFound, f"Invalid path {'/'.join(path_parts)}"
                )
            dir = self.depot.agents["directory"].decode_from_bytes(
                self.depot.storage.get(art.content_id)
            )
            next_art_id = dir.get_binding(part)
            if next_art_id is None:
                return False
            art_id = next_art_id
        return True

    def _get_dir(
        self, auth: AuthenticatedUser, ws: Workspace, path_parts: List[str]
    ) -> Tuple[ArtifactVersion, Directory]:
        """
        Get the current contents of the directory object located at a path in
        the workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        ws -- the workspace
        path_parts -- the directory path, structured as a list of path segments.

        Returns a pair of the directory artifact version, and the decoded directory content.
        """
        art_id = self._get_artifact_at_path(auth, ws, path_parts)
        if art_id is None:
            raise PtException(
                ErrorKind.NotFound, f"Workspace file {'/'.join(path_parts)} not found"
            )
        ver_id = self._current_baseline(auth, ws).get(art_id)
        if ver_id is None:
            raise PtException(
                ErrorKind.NotFound,
                f"Workspace file {'/'.join(path_parts)} not found in workspace baseline",
            )
        art = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, art_id, ver_id
        )
        if art.artifact_type != "directory":
            raise PtException(
                ErrorKind.TypeError,
                f"Artifact at {'/'.join(path_parts)} is not a directory",
            )
        return (
            art,
            self.depot.agents["directory"].decode_from_bytes(
                self.depot.storage.get(art.content_id)
            ),
        )

    # *************************************************
    # change operations within a populated workspace
    # *************************************************

    def _get_or_create_working_version(
        self, auth: AuthenticatedUser, project: str, ws: Workspace, id: Id[Artifact]
    ) -> ArtifactVersion:
        """
        Get or create a new working version of an artifact in the workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        wsid -- the Id of the workspace
        id -- the ID of the artifact.

        Returns an ArtifactVersion for the new, modifiable working version of
            the artifact.
        """
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )

        if id not in ws.working_versions:
            ver_id: Id[ArtifactVersion] | None
            if id == ws.baseline_id:
                ver_id = ws.baseline_version
            else:
                ver_id = self._current_baseline(auth, ws).get(id)
            if ver_id is None:
                raise PtException(
                    ErrorKind.NotFound, f"artifact {id} not found in project baseline"
                )
            working = self.depot.artifact_stash.create_working_version(
                auth, ws.project, id, ver_id
            )

            ws.working_versions[id] = working.id
            if id != ws.baseline_id:
                ws.modified_artifacts.add(id)
                working_baseline_version = self._get_or_create_working_version(
                    auth, project, ws, ws.baseline_id
                )
                working_baseline = self.depot.agents["baseline"].decode_from_bytes(
                    self.depot.storage.get(working_baseline_version.content_id)
                )
                working_baseline.change(id, working.id)
                baseline_content_id = self.depot.storage.put_transient(
                    self.depot.agents["baseline"].encode_to_bytes(working_baseline)
                )
                self.depot.artifact_stash.update_working_version(
                    auth=auth,
                    project=ws.project,
                    artifact_id=ws.baseline_id,
                    version_id=working_baseline_version.id,
                    updated_content_id=baseline_content_id,
                    updated_parents=None,
                    updated_metadata=None,
                )
                return working
            else:
                ws.baseline_version = working.id
                self._update_stored_workspace(ws)
                return working
        else:
            return self.depot.artifact_stash.retrieve_version(
                auth, ws.project, id, ws.working_versions[id]
            )

    def add_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        path: str,
        artifact_type: str,
        content: bytes,
    ) -> Id[Artifact]:
        """
        Add a new file to the project in the workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the name of the project containing the workspace
        wsid -- the workspace
        path -- the path of the new object.
        artifact_type -- the type of the new object.
        content -- the contents of the new object, encoded into bytes by its agent.

        Returns the ID of the newly created artifact.
        """

        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )
        path_parts = path.split("/")
        name = path_parts[-1]
        dir_path_parts = path_parts[:-1]

        (dArt, working_dir) = self._get_dir(auth, ws, dir_path_parts)
        working_dir_ver = self._get_or_create_working_version(
            auth, project, ws, dArt.artifact_id
        )

        baseline_ver = self._get_or_create_working_version(
            auth, project, ws, ws.baseline_id
        )
        baseline = self.depot.agents["baseline"].decode_from_bytes(
            self.depot.storage.get(baseline_ver.content_id)
        )

        cid = self.depot.storage.put(content)
        (new_art, new_ver) = self.depot.artifact_stash.create_artifact(
            auth=auth,
            project=ws.project,
            art_type=artifact_type,
            initial_content_id=cid,
            metadata={},
        )
        ws.modified_artifacts.add(new_art.id)
        working_dir.add_binding(name, new_art.id)
        self.depot.artifact_stash.update_working_version(
            auth=auth,
            project=ws.project,
            artifact_id=dArt.artifact_id,
            version_id=working_dir_ver.id,
            updated_content_id=self.depot.storage.put_transient(
                self.depot.agents["directory"].encode_to_bytes(working_dir)
            ),
            updated_parents=None,
            updated_metadata=None,
        )
        baseline.add(new_art.id, new_ver.id)
        baseline.change(dArt.artifact_id, working_dir_ver.id)

        self.depot.artifact_stash.update_working_version(
            auth=auth,
            project=ws.project,
            artifact_id=ws.baseline_id,
            version_id=baseline_ver.id,
            updated_content_id=self.depot.storage.put_transient(
                self.depot.agents["baseline"].encode_to_bytes(baseline)
            ),
            updated_metadata=None,
            updated_parents=None,
        )
        ws.baseline_version = baseline_ver.id
        self._update_stored_workspace(ws)
        return new_art.id

    def move_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        old_path: str,
        new_path: str,
    ) -> None:
        """
        Move a file from its current location to a new path.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the project containing the workspace
        wsid -- the ID of workspace
        old_path -- the path to the file in the current workspace.
        new_path -- the path to move the file to.
        """
        print(f"Moving {old_path} to {new_path}")
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )
        old_path_parts = old_path.split("/")
        old_path_dir = old_path_parts[:-1]
        old_name = old_path_parts[-1]
        new_path_parts = new_path.split("/")
        if not self._path_exists_in_workspace(auth, ws, new_path_parts):
            new_path_dir = new_path_parts[:-1]
            new_name = new_path_parts[-1]
        else:
            new_path_dir = new_path_parts
            new_name = old_name

        if "/".join(old_path_dir) == "/".join(new_path_dir):
            print(f"Path ==: {old_path_dir}")
            old_dir_id = self._get_artifact_at_path(auth, ws, old_path_dir)
            working_dir_art = self._get_or_create_working_version(
                auth, project, ws, old_dir_id
            )
            content = self.depot.storage.get(working_dir_art.content_id)
            working_dir = self.depot.agents["directory"].decode_from_bytes(content)
            target = working_dir.get_binding(old_name)
            working_dir.remove_binding(old_name)
            working_dir.add_binding(new_name, target)
            self.depot.artifact_stash.update_working_version(
                auth=auth,
                project=ws.project,
                artifact_id=old_dir_id,
                version_id=working_dir_art.id,
                updated_content_id=self.depot.storage.put_transient(
                    self.depot.agents["directory"].encode_to_bytes(working_dir)
                ),
                updated_metadata=None,
                updated_parents=None,
            )
        else:
            print(f"Path !=: {new_path_dir}")
            src_dir_id = self._get_artifact_at_path(auth, ws, old_path_dir)
            tgt_dir_id = self._get_artifact_at_path(auth, ws, new_path_dir)
            working_src_dir_ver = self._get_or_create_working_version(
                auth, project, ws, src_dir_id
            )
            working_tgt_dir_ver = self._get_or_create_working_version(
                auth, project, ws, tgt_dir_id
            )
            src_dir_content = self.depot.storage.get(working_src_dir_ver.content_id)
            tgt_dir_content = self.depot.storage.get(working_tgt_dir_ver.content_id)
            src_dir: Directory = self.depot.agents["directory"].decode_from_bytes(
                src_dir_content
            )
            tgt_dir = self.depot.agents["directory"].decode_from_bytes(tgt_dir_content)
            moved_id = src_dir.get_binding(old_name)
            if moved_id is None:
                raise PtException(ErrorKind.NotFound, f"Path {old_path} not found")
            updated_src_dir = src_dir.copy()
            updated_src_dir.remove_binding(old_name)
            updated_tgt_dir = tgt_dir.copy()
            updated_tgt_dir.add_binding(new_name, moved_id)
            self.depot.artifact_stash.update_working_version(
                auth=auth,
                project=ws.project,
                artifact_id=src_dir_id,
                version_id=working_src_dir_ver.id,
                updated_content_id=self.depot.storage.put_transient(
                    self.depot.agents["directory"].encode_to_bytes(updated_src_dir)
                ),
                updated_parents=None,
                updated_metadata=None,
            )
            self.depot.artifact_stash.update_working_version(
                auth=auth,
                project=ws.project,
                artifact_id=tgt_dir_id,
                version_id=working_tgt_dir_ver.id,
                updated_content_id=self.depot.storage.put_transient(
                    self.depot.agents["directory"].encode_to_bytes(updated_tgt_dir)
                ),
                updated_metadata=None,
                updated_parents=None,
            )
            self._update_stored_workspace(ws)

    def _add_transitive_contents_to_list(
        self,
        auth: AuthenticatedUser,
        ws: Workspace,
        dir: Directory,
        result: Set[Id[Artifact]],
    ) -> None:
        baseline = self._current_baseline(auth, ws)
        for name, id in dir.entries.items():
            result.add(id)

            ver_id = baseline.get(id)
            if ver_id is None:
                raise PtException(
                    ErrorKind.Internal, f"Artifact {id} not found in baseline"
                )
            art = self.depot.artifact_stash.retrieve_version(
                auth, ws.project, id, ver_id
            )
            if art.artifact_type == "directory":
                dir_content = self.depot.storage.get(art.content_id)
                child_dir = self.depot.agents["directory"].decode_from_bytes(
                    dir_content
                )
                self._add_transitive_contents_to_list(auth, ws, child_dir, result)

    def _split_dir_and_name(self, path: str) -> Tuple[List[str], str]:
        parts = path.split("/")
        return (parts[:-1], parts[-1])

    def _assert_not_null[T](self, it: T | None) -> T:
        if it is None:
            raise PtException(ErrorKind.Internal, "Unexpected null value")
        return it

    def delete_file(
        self, auth: AuthenticatedUser, project: str, wsid: Id[Workspace], path: str
    ) -> Set[Id[Artifact]]:
        """
        Delete a file from a project.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the project containing the workspace
        wsid -- the workspace
        path -- the path to the object to be deleted.

        Returns a list of the artifact IDs of objects removed as a result of
        this operation. (If the artifact isn't a directory, this will be just
        a singleton list of the ID of the deleted abject; if it was a directory,
        then this will be a list of IDs of all artifacts transitively contained
        in that directory.)
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )

        (dir_parts, name) = self._split_dir_and_name(path)
        deleted_id = self._get_artifact_at_path(auth, ws, path.split("/"))
        dir_id = self._get_artifact_at_path(auth, ws, dir_parts)
        working_dir_ver = self._get_or_create_working_version(auth, project, ws, dir_id)
        dir = self.depot.agents["directory"].decode_from_bytes(
            self.depot.storage.get(working_dir_ver.content_id)
        )
        dir.remove_binding(name)
        dir_content_id = self.depot.storage.put_transient(
            self.depot.agents["directory"].encode_to_bytes(dir)
        )
        self.depot.artifact_stash.update_working_version(
            auth=auth,
            project=ws.project,
            artifact_id=working_dir_ver.artifact_id,
            version_id=working_dir_ver.id,
            updated_content_id=dir_content_id,
            updated_metadata=None,
            updated_parents=None,
        )
        baseline = self._current_baseline(auth, ws)
        deleted_ver = self.depot.artifact_stash.retrieve_version(
            auth,
            ws.project,
            deleted_id,
            self._assert_not_null(baseline.get(deleted_id)),
        )
        result = set([deleted_id])
        if deleted_ver.artifact_type == "directory":
            deleted_dir = self.depot.agents["directory"].decode_from_bytes(
                self.depot.storage.get(deleted_ver.content_id)
            )
            self._add_transitive_contents_to_list(auth, ws, deleted_dir, result)
        working_baseline_ver = self._get_or_create_working_version(
            auth, project, ws, ws.baseline_id
        )
        working_baseline = self.depot.agents["baseline"].decode_from_bytes(
            self.depot.storage.get(working_baseline_ver.content_id)
        )
        for id in result:
            working_baseline.remove(id)
        baseline_cid = self.depot.storage.put_transient(
            self.depot.agents["baseline"].encode_to_bytes(working_baseline)
        )
        self.depot.artifact_stash.update_working_version(
            auth=auth,
            project=ws.project,
            artifact_id=working_baseline_ver.artifact_id,
            version_id=working_baseline_ver.id,
            updated_content_id=baseline_cid,
            updated_metadata=None,
            updated_parents=None,
        )
        ws.modified_artifacts = ws.modified_artifacts.union(result)
        self._update_stored_workspace(ws)
        return result

    def modify_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        path: str,
        new_content: bytes,
    ) -> None:
        """ "
        Modify the file at a path in the workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        wsid -- the workspace ID
        path -- the path of the artifact
        new_content -- the updated content of the artifact, encoded to
           a string by the appropriate agent.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )

        id = self._get_artifact_at_path(auth, ws, path.split("/"))
        working = self._get_or_create_working_version(auth, project, ws, id)
        self.depot.artifact_stash.update_working_version(
            auth,
            ws.project,
            id,
            working.id,
            self.depot.storage.put(new_content),
            None,
            None,
        )

    def get_file_contents(
        self, auth: AuthenticatedUser, project: str, wsid: Id[Workspace], path: str
    ) -> WorkspaceFileContents:
        """
        Get the contents of the artifact at a path.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the project containin the workspace
        wsid -- the workspace ID.
        path -- the path

        Returns the contents of the artifact, in the format used by the appropriate
           agent.
        """
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        id = self._get_artifact_at_path(auth, ws, path.split("/"))
        ver = self.depot.artifact_stash.retrieve_version(
            auth,
            ws.project,
            id,
            self._assert_not_null(self._current_baseline(auth, ws).get(id)),
        )
        return WorkspaceFileContents(
            path, ver.artifact_type, self.depot.storage.get(ver.content_id)
        )

    def list_paths(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        dir_opt: Directory | None = None,
    ) -> List[str]:
        """
        List all the paths in a workspace.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the project containing the workspace
        wsid -- the workspace ID
        dir_opt -- either the root directory whose paths should be listed,
          or None to list all paths in the workspace.

        Returns the list of all paths
        """
        self.depot.user_stash.validate_permissions(auth, Action.read_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        baseline = self._current_baseline(auth, ws)
        result: List[str] = []
        dir: Directory
        if dir_opt is None:
            dir_art = self.depot.artifact_stash.retrieve_version(
                auth,
                ws.project,
                baseline.root_dir,
                self._assert_not_null(baseline.get(baseline.root_dir)),
            )
            dir = self.depot.agents["directory"].decode_from_bytes(
                self.depot.storage.get(dir_art.content_id)
            )
        else:
            dir = dir_opt

        print(f"Walking directory {dir}")
        for name, id in dir.entries.items():
            result.append(name)
            e_ver = self.depot.artifact_stash.retrieve_version(
                auth, ws.project, id, self._assert_not_null(baseline.get(id))
            )
            if e_ver.artifact_type == "directory":
                e_dir = self.depot.agents["directory"].decode_from_bytes(
                    self.depot.storage.get(e_ver.content_id)
                )
                paths = self.list_paths(auth, project, ws.id, e_dir)
                for p in paths:
                    result.append(f"{name}/{p}")
        return result

    # *************************************************
    # ** workspace state updates and merges
    # *************************************************

    def save(
        self,
        auth: AuthenticatedUser,
        project: str,
        wsid: Id[Workspace],
        description: str,
        resolved: List[Id[MergeConflict]],
    ) -> SavePoint:
        """
        Save the current changes in this workspace as a savepoint.

        Arguments:
        auth -- the authenticated user performing the action.
        project -- the name of the project containing the workspace
        wsid -- the workspace ID
        description -- the savepoint description
        resolved -- a list of the merge conflicts that were resolved in the saved changes.

        Returns the new savepoint.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if ws.change is None:
            raise PtException(
                ErrorKind.Constraint, "Artifacts can only be modified in an open change"
            )

        for art_id, ver_id in ws.working_versions.items():
            self.depot.artifact_stash.commit_working_version(
                auth, ws.project, art_id, ver_id
            )

        sp = self.depot.change_stash.create_save_point(
            auth,
            ws.project,
            ws.history,
            ws.change,
            list(ws.modified_artifacts),
            description,
            ws.basis,
            ws.baseline_version,
        )
        ws.basis = ProjectVersionSpecifier.make_change(
            ws.project, ws.history, ws.change, sp.idx
        )
        ws.modified_artifacts.clear()
        ws.working_versions.clear()
        ws.conflicts = list(c for c in ws.conflicts if c.id not in resolved)
        self._update_stored_workspace(ws)
        return sp

    def deliver(self, auth: AuthenticatedUser, ws: Workspace, description: str) -> None:
        """
        Deliver the current change to its history.

        Arguments:
        auth -- the authenticated user performing the action.
        ws -- the workspace
        description -- a description of the change.
        """
        self.depot.user_stash.validate_permissions(
            auth, Action.write_project(ws.project)
        )
        if len(ws.modified_artifacts) == 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling deliver, but you have unsaved changes",
            )

        if len(ws.conflicts) != 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling deliver, but you have unresolved conflicts",
            )
        if ws.change is None:
            raise PtException(ErrorKind.UserError, "No change in progress to deliver")
        if not self.up_to_date(auth, ws):
            raise PtException(
                ErrorKind.UserError,
                f"History {ws.history} has steps that haven't been merged into your workspace; "
                + "run update before delivering",
            )
        ch = self.depot.change_stash.retrieve_change_by_name(
            auth, ws.project, ws.history, ws.change
        )

        step = self.depot.history_stash.add_history_step(
            auth,
            ws.project,
            ws.history,
            ch.id,
            self._get_baseline_version(auth, ws, ws.baseline_version),
            description,
        )
        self.depot.change_stash.update_change_status(
            auth, ws.project, ws.history, ws.change, ChangeStatus.Closed
        )
        ws.change = None
        ws.basis = ProjectVersionSpecifier.make_history(
            step.project, step.history_name, step.idx
        )
        self._update_stored_workspace(ws)

    def up_to_date(self, auth: AuthenticatedUser, ws: Workspace) -> bool:
        top_of_history = self.depot.history_stash.retrieve_history_step(
            auth, ws.project, ws.history
        )
        return self.depot.artifact_stash.version_is_ancestor(
            auth,
            ws.project,
            ws.baseline_id,
            top_of_history.baseline_version_id,
            ws.baseline_version,
        )

    def update(
        self, auth: AuthenticatedUser, project: str, wsid: Id[Workspace]
    ) -> List[MergeConflict]:
        """
        Get up-to-date with the parent history.

        ARguments:
        auth -- the authenticated user performing the action.
        project -- the name of the project containing the workspace
        wsid -- the workspace ID

        Returns a list of any conflicts created by the merge.
        """
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        if len(ws.modified_artifacts) != 0:
            raise PtException(
                ErrorKind.Constraint,
                "Workspace must be clean before calling update, but you have unsaved changes",
            )

        if len(ws.conflicts) != 0:
            raise PtException(
                ErrorKind.Constraint,
                "Workspace must be clean before calling update, but you have unresolved conflicts",
            )
        latest = self.depot.history_stash.retrieve_history_step(
            auth, ws.project, ws.history
        )
        if self.depot.artifact_stash.version_is_ancestor(
            auth,
            ws.project,
            ws.baseline_id,
            latest.baseline_version_id,
            ws.baseline_version,
        ):
            # No merge necessary.
            return []
        latest_baseline_ver = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, latest.baseline_id, latest.baseline_version_id
        )
        ws_baseline_ver = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, ws.baseline_id, ws.baseline_version
        )
        ancestor_id = self.depot.artifact_stash.nearest_common_ancestor(
            auth,
            ws.project,
            ws.baseline_id,
            latest_baseline_ver.id,
            ws.baseline_version,
        )
        ancestor = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, latest.baseline_id, ancestor_id
        )
        baseline_merge_result = self.depot.agents["baseline"].merge(
            ancestor, latest_baseline_ver, ws_baseline_ver
        )
        conflicts: List[MergeConflict] = []
        for conflict in baseline_merge_result.conflicts:
            baseline_conflict = BaselineConflict.decode_from_bytes(conflict.details)
            match baseline_conflict.type:
                case BaselineConflictType.MOD_DEL:
                    # just add this to the conflicts list: there's already a baseline entry
                    # for the modified thing, so the user can choose whether to delete
                    # it or not in order to resolve.
                    conflicts.append(conflict)
                case BaselineConflictType.DEL_MOD:
                    # Same as above: just add this to the conflicts list: there's already a
                    # baseline entry for the modified thing, so the user can choose whether
                    # to delete it or not in order to resolve.
                    conflicts.append(conflict)

                case BaselineConflictType.MOD_MOD:
                    # Do a merge of the source and target versions.
                    art_merge = self.merge_single_artifact(
                        auth,
                        ws,
                        baseline_conflict.artifact_id,
                        self._assert_not_null(baseline_conflict.merge_source_version),
                        self._assert_not_null(baseline_conflict.merge_target_version),
                    )
                    proposed_cid = self.depot.storage.put_transient(
                        art_merge.proposed_merge
                    )
                    working = self._get_or_create_working_version(
                        auth, project, ws, baseline_conflict.artifact_id
                    )
                    self.depot.artifact_stash.update_working_version(
                        auth,
                        ws.project,
                        baseline_conflict.artifact_id,
                        working.id,
                        proposed_cid,
                        updated_parents=[
                            self._assert_not_null(
                                baseline_conflict.merge_source_version
                            ),
                            self._assert_not_null(
                                baseline_conflict.merge_target_version
                            ),
                        ],
                        updated_metadata=None,
                    )

                    if len(art_merge.conflicts) > 0:
                        conflicts += art_merge.conflicts
        return baseline_merge_result.conflicts

    def merge_single_artifact(
        self,
        auth: AuthenticatedUser,
        ws: Workspace,
        id: Id[Artifact],
        source_id: Id[ArtifactVersion],
        target_id: Id[ArtifactVersion],
    ) -> MergeResult:
        ancestor_id = self.depot.artifact_stash.nearest_common_ancestor(
            auth, ws.project, id, source_id, target_id
        )
        anc = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, id, ancestor_id
        )
        src = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, id, source_id
        )
        tgt = self.depot.artifact_stash.retrieve_version(
            auth, ws.project, id, target_id
        )
        agent = self.depot.agents[anc.artifact_type]
        return agent.merge(anc, src, tgt)

    def integrate_change(
        self,
        auth: AuthenticatedUser,
        ws: Workspace,
        change_source_history: str,
        change_name: str,
    ) -> List[MergeConflict]:
        """
        Integrate changes from some other location into a workspace. Useful
        for cherry-picking.

        Arguments:
        auth -- the authenticated user performing the action.
        ws -- the workspace
        from -- the start point of the changes to integrate to this workspace.
        to -- the target point of the changes to integrate to this workspace.

        Returns a list of any merge conflicts created by the integration.
        """
        self.depot.user_stash.validate_permissions(
            auth, Action.write_project(ws.project)
        )
        if len(ws.modified_artifacts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling integrate, but you have unsaved changes",
            )
        if len(ws.conflicts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling integrate, "
                f"but you have {len(ws.conflicts)} unresolved conflicts",
            )
        raise NotImplementedError()

    def integrate_diff(
        self,
        auth: AuthenticatedUser,
        ws: Workspace,
        from_version: ProjectVersionSpecifier,
        to_version: ProjectVersionSpecifier,
    ) -> List[MergeConflict]:
        self.depot.user_stash.validate_permissions(
            auth, Action.write_project(ws.project)
        )
        if len(ws.modified_artifacts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling integrate, but you have unsaved changes",
            )
        if len(ws.conflicts) > 0:
            raise PtException(
                ErrorKind.UserError,
                "Workspace must be clean before calling integrate, "
                + f"but you have {len(ws.conflicts)} unresolved conflicts",
            )
        raise NotImplementedError()

    def delete_workspace(self, auth: AuthenticatedUser, ws: Workspace) -> None:
        self.depot.user_stash.validate_permissions(
            auth, Action.write_project(ws.project)
        )
        self.workspaces.delete_one({"_id": str(ws.id)})

    def abandon_changes(
        self, auth: AuthenticatedUser, project: str, wsid: Id[Workspace], reason: str
    ) -> None:
        self.depot.user_stash.validate_permissions(auth, Action.write_project(project))
        ws = self.retrieve_workspace_by_id(auth, project, wsid)
        self.set_basis(
            auth,
            project,
            ws.id,
            ProjectVersionSpecifier.make_change(
                ws.project, ws.history, self._assert_not_null(ws.change)
            ),
        )
