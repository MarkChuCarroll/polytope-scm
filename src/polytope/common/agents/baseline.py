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

from enum import Enum
import json
import pickle
from typing import Dict, List, NamedTuple

from polytope.common.agents.agents import Agent, MergeConflict, MergeResult
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.stashable.stashable import JDict


class Baseline(NamedTuple):
    root_dir: Id[Artifact]
    entries: Dict[Id[Artifact], Id[ArtifactVersion]]

    def to_dict(self) -> JDict:
        return {
            "root_dir": str(self.root_dir),
            "entries": {str(k): str(v) for k, v in self.entries.items()},
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "Baseline":
        return Baseline(
            root_dir=Id.from_string(dict["root_dir"]),
            entries={
                Id.from_string(k): Id.from_string(v) for k, v in dict["entries"].items()
            },
        )

    def contains(self, artifact_id: Id[Artifact]) -> bool:
        return artifact_id in self.entries

    def get(self, artifact_id: Id[Artifact]) -> Id[ArtifactVersion] | None:
        return self.entries.get(artifact_id)

    def add(self, artifact_id: Id[Artifact], version_id: Id[ArtifactVersion]) -> None:
        if self.contains(artifact_id):
            raise PtException(
                ErrorKind.Conflict,
                "Baseline already contains a mapping for $artifactId",
            )
        else:
            self.entries[artifact_id] = version_id

    def remove(self, artifact_id: Id[Artifact]) -> None:
        if artifact_id not in self.entries:
            raise PtException(
                ErrorKind.NotFound, "Baseline doesn't contain a mapping for $artifactId"
            )
        else:
            del self.entries[artifact_id]

    def change(
        self, artifact_id: Id[Artifact], version_id: Id[ArtifactVersion]
    ) -> None:
        self.remove(artifact_id)
        self.add(artifact_id, version_id)


class BaselineConflictType(Enum):
    MOD_DEL = "mod_del"
    DEL_MOD = "del_mod"
    MOD_MOD = "mod_mod"


class BaselineConflict(NamedTuple):
    type: BaselineConflictType
    artifact_id: Id[Artifact]
    merge_source_version: Id[ArtifactVersion] | None
    merge_target_version: Id[ArtifactVersion] | None

    def encode_to_bytes(self) -> bytes:
        return pickle.dumps(self)

    @classmethod
    def decode_from_bytes(cls, details: bytes) -> "BaselineConflict":
        return pickle.loads(details)


class BaselineAgent(Agent[Baseline]):

    @property
    def artifact_type(self) -> str:
        return "baseline"

    instance: "BaselineAgent | None" = None

    @classmethod
    def get(cls) -> "BaselineAgent":
        if cls.instance is None:
            cls.instance = BaselineAgent()
        return cls.instance

    def decode_from_bytes(self, content: bytes) -> Baseline:
        return Baseline.from_dict(json.loads(content))

    def encode_to_bytes(self, content: Baseline) -> bytes:
        return json.dumps(content.to_dict()).encode()

    def merge(
        self,
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion,
    ) -> MergeResult:
        ancestor_baseline: Baseline = self.decode_from_bytes(ancestor.content)
        source_baseline: Baseline = self.decode_from_bytes(source.content)
        target_baseline: Baseline = self.decode_from_bytes(target.content)

        target_version_map = target_baseline.entries
        targetArtifacts = set(target_version_map.keys())
        ancestor_versions = ancestor_baseline.entries
        ancestor_artifacts = set(ancestor_versions.keys())
        source_versions = source_baseline.entries
        source_artifacts = set(source_versions.keys())

        removed_in_target = ancestor_artifacts.difference(targetArtifacts)
        removed_in_source = ancestor_artifacts.difference(source_artifacts)

        added_in_target = targetArtifacts.difference(ancestor_artifacts)
        added_in_source = source_artifacts.difference(ancestor_artifacts)

        modified_in_target = [
            artId
            for artId in targetArtifacts
            if artId in ancestor_artifacts
            and ancestor_versions[artId] != target_version_map[artId]
        ]

        modified_in_source = [
            artId
            for artId in source_artifacts
            if artId in ancestor_artifacts
            and ancestor_versions[artId] != source_versions[artId]
        ]

        all_artifacts = source_artifacts.union(targetArtifacts).union(
            ancestor_artifacts
        )

        merged_version_mappings: Dict[Id[Artifact], Id[ArtifactVersion]] = {}
        conflicts: List[MergeConflict] = []
        for art_id in all_artifacts:
            target_art_version = target_version_map.get(art_id)
            source_art_version = source_versions.get(art_id)
            mod_source = art_id in modified_in_source
            mod_current = art_id in modified_in_target

            if art_id in removed_in_target:
                if art_id in modified_in_source:
                    conflicts.append(
                        MergeConflict(
                            id=Id.new_id(IdKind.ID_CONFLICT),
                            artifact_id=ancestor.artifact_id,
                            artifact_type=self.artifact_type,
                            source_version=source.id,
                            target_version=target.id,
                            details=BaselineConflict.encode_to_bytes(
                                BaselineConflict(
                                    BaselineConflictType.MOD_DEL,
                                    art_id,
                                    source_art_version,
                                    None,
                                )
                            ),
                        )
                    )
                    assert source_art_version is not None
                    merged_version_mappings[art_id] = source_art_version
            elif art_id in removed_in_source:
                if art_id in modified_in_target:
                    conflicts.append(
                        MergeConflict(
                            id=Id.new_id(IdKind.ID_CONFLICT),
                            artifact_id=ancestor.artifact_id,
                            artifact_type=self.artifact_type,
                            source_version=source.id,
                            target_version=target.id,
                            details=BaselineConflict.encode_to_bytes(
                                BaselineConflict(
                                    BaselineConflictType.DEL_MOD,
                                    art_id,
                                    None,
                                    target_art_version,
                                )
                            ),
                        )
                    )
                    assert target_art_version is not None
                    merged_version_mappings[art_id] = target_art_version
            elif art_id in added_in_target:
                assert target_art_version is not None
                merged_version_mappings[art_id] = target_art_version
            elif art_id in added_in_source:
                assert source_art_version is not None
                merged_version_mappings[art_id] = source_art_version
            elif mod_current and not mod_source:
                assert target_art_version is not None
                merged_version_mappings[art_id] = target_art_version
            elif not mod_current and mod_source:
                assert source_art_version is not None
                merged_version_mappings[art_id] = source_art_version
            elif not mod_current:  # modSource must be false here
                assert source_art_version is not None
                merged_version_mappings[art_id] = source_art_version
            else:  # modified in both
                assert target_art_version is not None
                merged_version_mappings[art_id] = target_art_version
                conflicts.append(
                    MergeConflict(
                        id=Id.new_id(IdKind.ID_CONFLICT),
                        artifact_id=ancestor.artifact_id,
                        artifact_type=self.artifact_type,
                        source_version=source.id,
                        target_version=target.id,
                        details=BaselineConflict.encode_to_bytes(
                            BaselineConflict(
                                BaselineConflictType.MOD_MOD,
                                art_id,
                                source_art_version,
                                target_art_version,
                            )
                        ),
                    )
                )
        return MergeResult(
            artifact_type=self.artifact_type,
            artifact_id=ancestor.artifact_id,
            ancestor_version=ancestor.id,
            source_version=source.id,
            target_version=target.id,
            proposed_merge=self.encode_to_bytes(
                Baseline(target_baseline.root_dir, merged_version_mappings)
            ),
            conflicts=conflicts,
        )
