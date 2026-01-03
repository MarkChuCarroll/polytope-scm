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
from typing import Dict, List, Set, Tuple

from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion, VersionStatus
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.user import Action, AuthenticatedUser
from polytope.server.depot import Depot
from polytope.server.depot.stashes.stash import Stash
from polytope.server.depot.stashes.user_stash import UserStash
from polytope.server.depot.storage import Content


class ArtifactStash(Stash):
    """
    The stash that manages storage of artifacts and versions for the depot.

    """

    def __init__(self, depot: Depot) -> None:
        self.depot = depot
        self.artifacts = self.depot.db.get_collection("artifacts")
        self.versions = self.depot.db.get_collection("versions")
        self.storage = self.depot.storage

    def init_storage(self, config):
        pass

    @property
    def user_stash(self) -> UserStash:
        return self.depot.user_stash

    def retrieve_artifact(
        self, auth: AuthenticatedUser, project: str, id: Id[Artifact]
    ) -> Artifact:
        """
        Retrieves an artifact from the database

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact.
        id -- the artifact ID.

        Returns  the full Artifact record.
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        art_dict = self.artifacts.find_one({"_id": str(id), "project": project})
        if art_dict is None:
            raise PtException(ErrorKind.NotFound, f"Artifact {id} not found")
        return Artifact.from_dict(art_dict)

    def retrieve_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_id: Id[Artifact],
        ver_id: Id[ArtifactVersion],
    ) -> ArtifactVersion:
        """
        Retrieve a version of an artifact from the database.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the project containing the artifact.
        art_id -- the artifact ID
        ver_id -- the version ID.

        Returns the full ArtifactVersion record.
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        ver_dict = self.versions.find_one({"_id": str(ver_id)})
        if ver_dict is None:
            raise PtException(
                ErrorKind.NotFound, f"Artifact Version {ver_id} not found"
            )

        result = ArtifactVersion.from_dict(ver_dict)
        if result.artifact_id != art_id:
            raise PtException(ErrorKind.Internal, "Whazza?")
        return result

    def create_artifact(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_type: str,
        initial_content_id: Id[Content],
        metadata: Dict[str, str],
    ) -> Tuple[Artifact, ArtifactVersion]:
        """
        Store a new artifact from the database.

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact.
        artifact_type -- the type of the new artifact.
        initial_content_id -- the content of the new artifact.
        metadata -- metadata to apply to the new artifact.

        Throws PtException if the artifact already exists, if the user
        doesn't have permission to write it, or if there's some internal
        error storing it.

        Returns a tuple containing the new artifact, and its initial version.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        artId: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        now = datetime.now()
        initial_version = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=artId,
            artifact_type=art_type,
            timestamp=now,
            creator=auth.user_id,
            content_id=initial_content_id,
            parents=[],
            metadata=metadata,
            status=VersionStatus.Committed,
        )

        artifact = Artifact(
            id=artId,
            artifact_type=art_type,
            timestamp=now,
            creator=auth.user_id,
            project=project,
            metadata=metadata,
            versions=[initial_version.id],
        )
        self.artifacts.insert_one(artifact.to_dict())
        self.versions.insert_one(initial_version.to_dict())
        return (artifact, initial_version)

    def create_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_id: Id[Artifact],
        art_type: str,
        content_id: Id[Content],
        parents: List[Id[ArtifactVersion]],
        metadata: Dict[str, str],
    ) -> ArtifactVersion:
        """
        Create a new version af an artifact

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact.
        artifact_id -- the ID of the artifact
        content -- the content of the new artifact version.
        parents -- a list of versions that will be parents of the new version.
        metadata -- a map of metadata for the new version.

        Returns the new version
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        ver = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=art_id,
            creator=auth.user_id,
            content_id=content_id,
            timestamp=datetime.now(),
            parents=parents,
            metadata=metadata,
            artifact_type=art_type,
            status=VersionStatus.Committed,
        )
        versions = self.retrieve_artifact(auth, project, art_id).versions
        versions.append(ver.id)
        self.versions.insert_one(ver.to_dict())
        self.artifacts.update_one(
            {"_id": str(art_id)}, {"$set": {"versions": [str(v) for v in versions]}}
        )

        return ver

    def create_working_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        base_ver: Id[ArtifactVersion],
    ) -> ArtifactVersion:
        """
        Creates a working version of an artifact for an in-progress change.

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact
        artifact_id -- the ID of the artifact.
        base_ver -- the ID of the version that's going to be edited into a new version.

        Returns a new working version.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        base = self.retrieve_version(auth, project, artifact_id, base_ver)
        working = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=base.artifact_id,
            artifact_type=base.artifact_type,
            content_id=base.content_id,
            creator=auth.user_id,
            timestamp=datetime.now(),
            metadata=base.metadata,
            parents=[base.id],
            status=VersionStatus.Working,
        )
        self.versions.insert_one(working.to_dict())
        self.artifacts.update_one(
            {"_id": str(artifact_id)}, {"$push": {"versions": str(working.id)}}
        )
        return working

    def update_working_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        version_id: Id[ArtifactVersion],
        updated_content_id: Id[Content] | None,
        updated_metadata: Dict[str, str] | None,
        updated_parents: List[Id[ArtifactVersion]] | None,
    ) -> ArtifactVersion:
        """
        Update a working version

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact
        artifact_id -- the artifact ID
        version_id -- the version ID
        updated_content_id -- the updated content of the artifact, or null if the content is
             unchanged.
        updated_metadata -- the updated metadata of the artifact, or null if the metadata
           is unchanged
        updated_parents

        Returns the updated version.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        if (
            updated_content_id is None
            and updated_metadata is None
            and updated_parents is None
        ):
            raise PtException(ErrorKind.Constraint, "Update must update something")

        ver = self.retrieve_version(auth, project, artifact_id, version_id)
        if ver.status != VersionStatus.Working:
            raise PtException(ErrorKind.Constraint, "Can only update a working version")

        if updated_content_id is not None:
            self.versions.update_one(
                {"_id": str(version_id)},
                {"$set": {"content_id": str(updated_content_id)}},
            )
        if updated_metadata is not None:
            self.versions.update_one(
                {"_id": version_id}, {"$set": {"metadata": updated_metadata}}
            )
        if updated_parents is not None:
            self.versions.update_one(
                {"_id": str(version_id)},
                {"$set": {"parents": list(str(p) for p in updated_parents)}},
            )
        return self.retrieve_version(auth, project, artifact_id, version_id)

    def commit_working_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        version_id: Id[ArtifactVersion],
    ) -> None:
        """
        Commit a working version as a final, immutable version in
        the artifact history.

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the name of the project containing the artifact.
        artifact_id -- the ID of the artifact
        version_id -- the ID of the working version to be committed.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        version = self.retrieve_version(auth, project, artifact_id, version_id)
        if version.status != VersionStatus.Working:
            raise PtException(ErrorKind.Constraint, "Can only commit a working version")
        if version.content_id.kind == IdKind.ID_TRANSIENT:
            new_id = self.depot.storage.commit_transient(version.content_id)
        else:
            new_id = version.content_id
        version = copy.replace(version, content_id=new_id)

        self.versions.update_one(
            {"_id": str(version_id)},
            {
                "$set": {
                    "content_id": str(new_id),
                    "status": VersionStatus.Committed.value,
                }
            },
        )

    def abort_working_version(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_id: Id[Artifact],
        ver_id: Id[ArtifactVersion],
    ) -> None:
        """
        Abort an in-progress version, discarding its content
        and removing it from the depot

        Arguments:
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact
        art_id -- the artifact ID
        ver_id -- the version_id of the working version.
        """
        self.user_stash.validate_permissions(auth, Action.write_project(project))
        version = self.retrieve_version(auth, project, art_id, ver_id)
        if version.status != VersionStatus.Working:
            raise PtException(ErrorKind.Constraint, "Can only abort a working version")
        self.depot.storage.delete_transient(version.content_id)
        self.versions.update_one(
            {"_id": str(version.id)},
            {"$set": {"content_id": None, "status": VersionStatus.Aborted}},
        )

    def retrieve_version_status(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        version_id: Id[ArtifactVersion],
    ) -> VersionStatus:
        """
        Check the status of a version.
        auth -- the authenticated user performing the operation
        project -- the project containing the artifact.
        artifact_id -- the ID of the artifact
        version_id -- the ID of the version

        Returns the version status
        """
        return self.retrieve_version(auth, project, artifact_id, version_id).status

    def fetch_parents(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_id: Id[Artifact],
        ver_id: Id[ArtifactVersion],
    ) -> List[Id[ArtifactVersion]]:
        """
        Retrieve the list of immediate parents of a version.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the artifact.
        art_id -- the Id of the artifact
        ver_id -- the Id of the artifact version.

        Returns a list of the version IDs of the parents of the version.
        """
        art = self.retrieve_version(auth, project, art_id, ver_id)
        return art.parents

    def all_ancestors(
        self,
        auth: AuthenticatedUser,
        project: str,
        art_id: Id[Artifact],
        ver_id: Id[ArtifactVersion],
    ) -> Set[Id[ArtifactVersion]]:
        """
        Compute the set of all transitive ancestors of a version.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the artifact.
        art_id -- the Id of the artifact
        ver_id -- the id of the version.

        Returns a set of all versions that are in the ancestry of the
        target version.
        """
        queue: List[Id[ArtifactVersion]] = []
        queue.append(ver_id)
        all_ancestors: Set[Id[ArtifactVersion]] = set()
        while len(queue) > 0:
            nxt = queue.pop(0)
            if nxt not in all_ancestors:
                all_ancestors.add(nxt)
                for anc in self.fetch_parents(auth, project, art_id, nxt):
                    queue.append(anc)
        return all_ancestors

    def version_is_ancestor(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        maybe_ancestor: Id[ArtifactVersion],
        maybe_descendant: Id[ArtifactVersion],
    ) -> bool:
        """
        Determine whether a given artifact is an ancestor of a target artifact.

        Arguments:
        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the artifact.
        artifact_id -- the ID of the artifact containing the versions.
        maybe_ancestor -- the ID of the potential ancestor version.
        maybe_descendant -- the ID of the target version.

        Returns True if maybe_ancestor is a ancestor of maybe_descendant
        """
        all_ancestors = self.all_ancestors(auth, project, artifact_id, maybe_descendant)
        return maybe_ancestor in all_ancestors

    def nearest_common_ancestor(
        self,
        auth: AuthenticatedUser,
        project: str,
        artifact_id: Id[Artifact],
        src: Id[ArtifactVersion],
        tgt: Id[ArtifactVersion],
    ) -> Id[ArtifactVersion]:
        """
        Get the nearest common ancestor of two versions.

        This algorithm is based on frontier expansion. We start with the artifacts
        and their immediate parents. If there's no common ancestor in the intersection of
        those two sets, then we expand the frontier of the ancestry sets by adding some parents
        of items in the histories. We keep expanding that frontier until eventually,
        we find a common element.

        If there's more than one thing in the intersections, then the elements of
        the intersection are equally distance from the two versions, so we can arbitrarily
        choose any of them as a NCA.

        auth -- the authenticated user performing the operation.
        project -- the name of the project containing the artifact
        artifact_id -- the id of the artifact
        src -- one of the two target versions.
        tgt -- the other target version.

        Returns a nearest common ancestor of the source and target version. If there are
        multiple ancestors at the same distance, then the result will be an arbitrary selection
        from those common ancestors.
        """
        self.user_stash.validate_permissions(auth, Action.read_project(project))
        source_hist: Set[Id[ArtifactVersion]] = set()
        target_hist: Set[Id[ArtifactVersion]] = set()
        src_queue: List[Id[ArtifactVersion]] = []
        src_queue.append(src)
        tgt_queue: List[Id[ArtifactVersion]] = []
        tgt_queue.append(tgt)
        while (
            len(source_hist & target_hist) == 0
            and len(src_queue) > 0
            and len(tgt_queue) > 0
        ):
            if len(src_queue) > 0:
                nxt = src_queue.pop(0)
                if nxt not in source_hist:
                    source_hist.add(nxt)
                    for p in self.fetch_parents(auth, project, artifact_id, nxt):
                        src_queue.append(p)
            if len(source_hist & target_hist) == 0 and len(tgt_queue) > 0:
                nxt = tgt_queue.pop(0)
                if nxt not in target_hist:
                    target_hist.add(nxt)
                    for p in self.fetch_parents(auth, project, artifact_id, nxt):
                        tgt_queue.append(p)
        ancestors = source_hist & target_hist
        if len(ancestors) == 0:
            raise PtException(
                ErrorKind.Internal,
                "Two versions of an artifact have no common ancestor. This should be impossible",
            )
        return ancestors.pop()
