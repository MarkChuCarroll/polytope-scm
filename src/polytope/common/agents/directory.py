# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A directory is just a list of entries, each of which is a name/id pair.
from enum import Enum
import json
import pickle
from typing import List, NamedTuple, Dict

from polytope.common.agents.agents import Agent, MergeConflict, MergeResult
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.stashable import JDict


class DualMapping(NamedTuple):
    """
    For directory merges, we need both the primary mapping from
    name to artifact, and also the secondary mapping from artifact to name.
    """

    by_name: Dict[str, Id[Artifact]]
    by_artifact: Dict[Id[Artifact], str]

    @classmethod
    def from_directory(cls, dir: "Directory") -> "DualMapping":
        return DualMapping(
            {name: artifact for (name, artifact) in dir.entries.items()},
            {artifact: name for (name, artifact) in dir.entries.items()},
        )


class Directory(NamedTuple):
    entries: Dict[str, Id[Artifact]] = {}

    def remove_binding(self, name: str) -> None:
        del self.entries[name]

    def add_binding(self, name: str, artifact_id: Id[Artifact]) -> None:
        if name in self.entries:
            raise PtException(ErrorKind.Conflict, "Binding already exists for $name")
        else:
            self.entries[name] = artifact_id

    def contains_binding(self, name: str) -> bool:
        return name in self.entries

    def get_binding(self, name: str) -> Id[Artifact] | None:
        return self.entries.get(name)

    def get_name_for(self, id: Id[Artifact]) -> str | None:
        for name, entry_id in self.entries.items():
            if entry_id == id:
                return name
        return None

    def copy(self) -> "Directory":
        return Directory(self.entries.copy())

    def to_dict(self) -> JDict:
        return {"entries": {k: str(v) for k, v in self.entries.items()}}

    @classmethod
    def from_dict(cls, dict: JDict) -> "Directory":
        return Directory(
            entries={k: Id.from_string(v) for k, v in dict["entries"].items()}
        )


class DirectoryChangeKind(Enum):
    Rename = "Rename"
    Add = "Add"
    Remove = "Remove"


class DirectoryChange(NamedTuple):
    """
    For computing merges, a structure that describes the changes
    between a base version and a modified version of a directory.
    """

    type: DirectoryChangeKind
    artifact_id: Id[Artifact]
    name_before: str | None
    name_after: str | None

    def applyTo(self, dir: Directory) -> Directory:
        """
        Apply a change, produced from one directory comparison,
        to a different directory. This merges a change from one
        modified version to another.
        """
        match self.type:
            case DirectoryChangeKind.Rename:
                assert self.name_before is not None
                assert self.name_after is not None
                dir.remove_binding(self.name_before)
                dir.add_binding(self.name_after, self.artifact_id)

            case DirectoryChangeKind.Add:
                if self.name_after is None:
                    raise PtException(
                        ErrorKind.InvalidParameter,
                        "Add must have a non-null new binding name",
                    )
                if not dir.contains_binding(self.name_after):
                    dir.add_binding(self.name_after, self.artifact_id)
                else:
                    raise PtException(
                        ErrorKind.InvalidParameter,
                        f"Add must have a unique binding name: {dir.entries.keys()}",
                    )
            case DirectoryChangeKind.Remove:
                assert self.name_before is not None
                dir.remove_binding(self.name_before)
        return dir


class ConflictKind(Enum):
    ADD_ADD_NAME = "add_add_name"
    ADD_ADD_ID = "add_add_id"
    MOD_DEL = "mod_del"
    DEL_MOD = "del_mod"
    MOD_MOD = "mod_mod"


class DirectoryMergeConflict(NamedTuple):
    """
    Information about a marge conflict that's specific to directories.
    This will be the contents of the 'details' field of the conflict record.
    """

    kind: ConflictKind
    name_before: str | None
    name_in_merge_source: str | None
    name_in_merge_target: str | None

    def encode_to_bytes(self) -> bytes:
        return pickle.dumps(self)

    @classmethod
    def decode_from_bytes(cls, content: bytes) -> "DirectoryMergeConflict":
        return pickle.loads(content)


class DirectoryAgent(Agent[Directory]):
    instance: "DirectoryAgent | None" = None

    @classmethod
    def get(cls) -> "DirectoryAgent":
        if cls.instance is None:
            cls.instance = DirectoryAgent()
        return cls.instance

    @property
    def artifact_type(self) -> str:
        return "directory"

    def decode_from_bytes(self, content: bytes) -> Directory:
        return Directory.from_dict(json.loads(content))

    def encode_to_bytes(self, content: Directory) -> bytes:
        return json.dumps(content.to_dict()).encode()

    def _unmodified(
        self, id: Id[Artifact], baseVersion: DualMapping, modifiedVersion: DualMapping
    ) -> bool:
        return (
            id in baseVersion.by_artifact
            and id in modifiedVersion.by_artifact
            and baseVersion.by_artifact[id] == modifiedVersion.by_artifact[id]
        )

    def _added(
        self, id: Id[Artifact], baseVersion: DualMapping, modifiedVersion: DualMapping
    ) -> bool:
        return id in modifiedVersion.by_artifact and id not in baseVersion.by_artifact

    def _renamed(
        self, id: Id[Artifact], baseVersion: DualMapping, modifiedVersion: DualMapping
    ) -> bool:
        return (
            id in baseVersion.by_artifact
            and id in modifiedVersion.by_artifact
            and baseVersion.by_artifact[id] != modifiedVersion.by_artifact[id]
        )

    def _removed(
        self, id: Id[Artifact], baseVersion: DualMapping, modifiedVersion: DualMapping
    ) -> bool:
        return id in baseVersion.by_artifact and id not in modifiedVersion.by_artifact

    def compute_directory_changes(
        self, baseVersion: DualMapping, modifiedVersion: DualMapping
    ) -> List[DirectoryChange]:
        result: List[DirectoryChange] = []
        all_ids = set(baseVersion.by_artifact.keys()).union(
            set(modifiedVersion.by_artifact.keys())
        )
        for id in all_ids:
            if not self._unmodified(id, baseVersion, modifiedVersion):
                if self._added(id, baseVersion, modifiedVersion):
                    result.append(
                        DirectoryChange(
                            DirectoryChangeKind.Add,
                            id,
                            None,
                            modifiedVersion.by_artifact[id],
                        )
                    )

                elif self._removed(id, baseVersion, modifiedVersion):
                    result.append(
                        DirectoryChange(
                            DirectoryChangeKind.Remove,
                            id,
                            baseVersion.by_artifact[id],
                            None,
                        )
                    )
                elif self._renamed(id, baseVersion, modifiedVersion):
                    result.append(
                        DirectoryChange(
                            DirectoryChangeKind.Rename,
                            id,
                            baseVersion.by_artifact[id],
                            modifiedVersion.by_artifact[id],
                        )
                    )
        return result

    def merge(
        self,
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion,
    ) -> MergeResult:
        anc_dir = self.decode_from_bytes(ancestor.content)
        src_dir = self.decode_from_bytes(source.content)
        tgt_dir = self.decode_from_bytes(target.content)
        anc_bindings = DualMapping.from_directory(anc_dir)
        src_bindings = DualMapping.from_directory(src_dir)
        tgt_bindings = DualMapping.from_directory(tgt_dir)

        def create_conflict(detail: DirectoryMergeConflict) -> MergeConflict:
            return MergeConflict(
                id=Id[MergeConflict].new_id(IdKind.ID_CONFLICT),
                artifact_id=ancestor.artifact_id,
                artifact_type=self.artifact_type,
                source_version=source.id,
                target_version=target.id,
                details=detail.encode_to_bytes(),
            )

        # Step 1: compare each of the merge source and merge target to
        # the common ancestor, gathering the changes.
        changes_in_merge_source = self.compute_directory_changes(
            anc_bindings, src_bindings
        )

        changes_in_merge_target = self.compute_directory_changes(
            anc_bindings, tgt_bindings
        )

        # At this point, we've got a collection of the changes to the directory
        # in both the merge source and merge target. What we need to do now
        # is walk through those.
        #
        # For each directory change in the merge source, we need to check to see if there's
        # a conflicting change in the merge target, and vice versa. If we find
        # a conflict, then we need to put a best-guess into the merge result, and
        # add a conflict record.

        conflicts: List[MergeConflict] = []
        proposed_merge_result = tgt_dir.copy()

        for change_in_source in changes_in_merge_source:
            conflicted = False
            # First, look to see if there are two adds with the same name.
            if change_in_source.type == DirectoryChangeKind.Add:
                same_name_change_in_target = [
                    it
                    for it in changes_in_merge_target
                    if it.name_after == change_in_source.name_after
                    and it.artifact_id != change_in_source.artifact_id
                ]
                if len(same_name_change_in_target) > 0:
                    conflicted = True
                    conflict = create_conflict(
                        DirectoryMergeConflict(
                            ConflictKind.ADD_ADD_NAME,
                            name_before=None,
                            name_in_merge_source=change_in_source.name_after,
                            name_in_merge_target=same_name_change_in_target[
                                0
                            ].name_after,
                        )
                    )
                    conflicts.append(conflict)
                    proposed_merge_result.add_binding(
                        "${changeInSource.nameAfter}_${conflict.id}",
                        change_in_source.artifact_id,
                    )
            # check if there are two changes that affect the same artifact ID
            change_in_target = [
                change
                for change in changes_in_merge_target
                if change.artifact_id == change_in_source.artifact_id
            ]

            if len(change_in_target) > 0:
                if (
                    change_in_source.type == DirectoryChangeKind.Add
                    and change_in_target[0].type == DirectoryChangeKind.Add
                ):
                    conflicted = True
                    conflicts.append(
                        create_conflict(
                            DirectoryMergeConflict(
                                ConflictKind.ADD_ADD_ID,
                                name_before=None,
                                name_in_merge_source=change_in_source.name_after,
                                name_in_merge_target=change_in_target[0].name_after,
                            )
                        )
                    )
                    # Don't add anything to the proposed result: there's already an
                    # add for this artifact.

                elif (
                    change_in_source.type == DirectoryChangeKind.Add
                    and change_in_target[0].type == DirectoryChangeKind.Remove
                ):
                    # should be impossible - you can't both add a rename the same thing.
                    raise PtException(
                        ErrorKind.Internal,
                        "Impossible case in baseline merge: Add-Remove artifact",
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Add
                    and change_in_target[0].type == DirectoryChangeKind.Rename
                ):
                    # should be impossible - you can't both add a rename the same thing.
                    raise PtException(
                        ErrorKind.Internal,
                        "Impossible case in baseline merge: Add-Rename artifact",
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Remove
                    and change_in_target[0].type == DirectoryChangeKind.Add
                ):
                    # can't happen.
                    raise PtException(
                        ErrorKind.Internal,
                        "Impossible case in baseline merge: Rename-Add artifact",
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Rename
                    and change_in_target[0].type == DirectoryChangeKind.Remove
                ):
                    conflicted = True
                    conflicts.append(
                        create_conflict(
                            DirectoryMergeConflict(
                                ConflictKind.MOD_DEL,
                                name_before=change_in_source.name_before,
                                name_in_merge_source=change_in_source.name_after,
                                name_in_merge_target=change_in_target[0].name_after,
                            )
                        )
                    )
                    assert change_in_source.name_after is not None
                    proposed_merge_result.add_binding(
                        change_in_source.name_after, change_in_source.artifact_id
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Rename
                    and change_in_target[0].type == DirectoryChangeKind.Rename
                ):
                    if change_in_source.name_after != change_in_target[0].name_after:
                        conflicted = True
                        conflicts.append(
                            create_conflict(
                                DirectoryMergeConflict(
                                    ConflictKind.MOD_MOD,
                                    name_before=change_in_source.name_before,
                                    name_in_merge_source=change_in_source.name_after,
                                    name_in_merge_target=change_in_target[0].name_after,
                                )
                            )
                        )
                # Don't add any bindings: the artifact is already present.
                elif (
                    change_in_source.type == DirectoryChangeKind.Remove
                    and change_in_target[0].type == DirectoryChangeKind.Add
                ):
                    # can't happen.
                    raise PtException(
                        ErrorKind.Internal,
                        "Impossible case in baseline merge: Remove-Add artifact",
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Remove
                    and change_in_target[0].type == DirectoryChangeKind.Rename
                ):
                    conflicted = True
                    conflicts.append(
                        create_conflict(
                            DirectoryMergeConflict(
                                ConflictKind.DEL_MOD,
                                name_before=change_in_source.name_before,
                                name_in_merge_source=change_in_source.name_after,
                                name_in_merge_target=change_in_target[0].name_after,
                            )
                        )
                    )
                elif (
                    change_in_source.type == DirectoryChangeKind.Remove
                    and change_in_target[0].type == DirectoryChangeKind.Remove
                ):
                    # the source and target already match, so the remove is in
                    # the proposed.
                    pass

            if not conflicted:
                change_in_source.applyTo(proposed_merge_result)

        # assemble into a merge result.
        return MergeResult(
            artifact_type=self.artifact_type,
            artifact_id=ancestor.artifact_id,
            ancestor_version=ancestor.id,
            source_version=source.id,
            target_version=target.id,
            proposed_merge=self.encode_to_bytes(proposed_merge_result),
            conflicts=conflicts,
        )
