
from datetime import datetime
import json
import tempfile
from typing import Dict

from pytest import fixture

from polytope.common.agents.baseline import BaselineAgent
from polytope.common.agents.directory import ConflictKind, Directory, DirectoryAgent, DirectoryMergeConflict
from polytope.common.stashable.artifact import Artifact, ArtifactVersion, VersionStatus
from polytope.common.stashable.ids import Id, IdKind
from polytope.depot.storage.storage import Content, FileStorage

artifactA: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
artifactB: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
artifactC: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
artifactD: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
artifactE: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
artifactF: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)


class TestDirectoryAgent:

    @fixture
    def storage(self):
        with tempfile.TemporaryDirectory(dir="tmp") as dir:
            storage = FileStorage(dir)
            yield storage

    @fixture
    def dir_agent(self, storage) -> DirectoryAgent:
        return DirectoryAgent(storage)

    def setup_test_directories(self,
                               dir_agent: DirectoryAgent,
                               anc_bindings: Dict[str, Id[Artifact]],
                               src_bindings:  Dict[str, Id[Artifact]],
                               tgt_bindings:  Dict[str, Id[Artifact]]
                               ) -> (ArtifactVersion, ArtifactVersion, ArtifactVersion):  # type: ignore
        art_id: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        ancestor_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        source_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        target_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)

        anc_cid = dir_agent.storage.put(dir_agent.encode_to_bytes(
            Directory(anc_bindings)))
        anc_ver = ArtifactVersion(
            id=ancestor_id,
            artifact_id=art_id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="me",
            content_id=anc_cid,
            parents=[],
            metadata={},
            status=VersionStatus.Committed
        )

        src_cid = dir_agent.storage.put(dir_agent.encode_to_bytes(
            Directory(src_bindings)))
        src_ver = ArtifactVersion(
            id=source_id,
            artifact_id=art_id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="me",
            content_id=src_cid,
            parents=[anc_ver.id],
            metadata={},
            status=VersionStatus.Committed
        )

        tgt_cid = dir_agent.storage.put(dir_agent.encode_to_bytes(
            Directory(tgt_bindings)))
        tgt_ver = ArtifactVersion(
            id=target_id,
            artifact_id=art_id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="me",
            content_id=tgt_cid,
            parents=[anc_ver.id],
            metadata={},
            status=VersionStatus.Committed
        )
        return (anc_ver, src_ver, tgt_ver)

    def test_non_conflicting_merge(self, dir_agent: DirectoryAgent) -> None:
        anc = {"a": artifactA,
               "b": artifactB,
               "c": artifactC,
               "d": artifactD}
        src = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "dd": artifactD
        }

        tgt = {
            "a": artifactA,
            "bb": artifactB,
            "c": artifactC,
            "d": artifactD
        }

        (ancestor, source, target) = self.setup_test_directories(
            dir_agent,
            anc,
            src,
            tgt
        )

        result = dir_agent.merge(
            ancestor, source, target)

        assert 0 == len(
            result.conflicts)
        proposed = dir_agent.decode_from_bytes(
            result.proposed_merge)
        assert "a" == proposed.get_name_for(
            artifactA)
        assert "bb" == proposed.get_name_for(
            artifactB)
        assert "c" == proposed.get_name_for(
            artifactC)
        assert "dd" == proposed.get_name_for(
            artifactD)

    def test_non_conflicting_adds(self, dir_agent: DirectoryAgent) -> None:
        anc_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD}
        src_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD,
            "e": artifactE}

        tgt_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD,
            "f": artifactF}

        (ancestor, source, target) = self.setup_test_directories(
            dir_agent,
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = dir_agent.merge(ancestor, source, target)

        assert 0 == len(result.conflicts)
        proposed = dir_agent.decode_from_bytes(result.proposed_merge)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)
        assert "c" == proposed.get_name_for(artifactC)
        assert "d" == proposed.get_name_for(artifactD)
        assert "e" == proposed.get_name_for(artifactE)
        assert "f" == proposed.get_name_for(artifactF)

    def test_rename_rename_conflicts(self, dir_agent: DirectoryAgent) -> None:
        anc_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD
        }
        src_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "dd": artifactD
        }
        tgt_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "e": artifactD
        }
        (ancestorVer, sourceVer, targetVer) = self.setup_test_directories(
            dir_agent,
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = dir_agent.merge(ancestorVer, sourceVer, targetVer)

        assert 1 == len(result.conflicts)
        assert sourceVer.id == result.conflicts[0].source_version
        assert targetVer.id == result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.MOD_MOD == detail.kind
        proposed = dir_agent.decode_from_bytes(result.proposed_merge)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)

    def test_mod_del_conflicts(self, dir_agent: DirectoryAgent) -> None:
        anc_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD
        }
        src_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC
        }
        tgt_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "e": artifactD
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_test_directories(
            dir_agent,
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = dir_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        assert src_ver.id == result.conflicts[0].source_version
        assert tgt_ver.id, result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.DEL_MOD == detail.kind
        proposed = dir_agent.decode_from_bytes(result.proposed_merge)
        assert 4 == len(proposed.entries)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)
        assert "c" == proposed.get_name_for(artifactC)
        assert "e" == proposed.get_name_for(artifactD)

        def test_del_mod_conflicts(self, dir_agent: DirectoryAgent) -> None:
            anc_bindings = {
                "a": artifactA,
                "b": artifactB,
                "c": artifactC,
                "d": artifactD
            }
        src_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "e": artifactD
        }
        tgt_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_test_directories(
            dir_agent,
            anc_bindings,
            src_bindings,
            tgt_bindings
        )
        result = dir_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        assert src_ver.id == result.conflicts[0].source_version
        assert tgt_ver.id == result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.MOD_DEL == detail.kind
        proposed = dir_agent.decode_from_bytes(result.proposed_merge)
        assert 4 == len(proposed.entries)
        assert "a", proposed.get_name_for(artifactA)
        assert "b", proposed.get_name_for(artifactB)
        assert "c", proposed.get_name_for(artifactC)
        assert "e", proposed.get_name_for(artifactD)
