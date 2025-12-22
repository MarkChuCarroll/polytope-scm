
from datetime import datetime
import json
from typing import Dict

from polytope.common.agents.directory import ConflictKind, Directory, DirectoryAgent, DirectoryMergeConflict
from polytope.common.stashable.artifact import Artifact, ArtifactVersion, VersionStatus
from polytope.common.stashable.ids import Id, IdKind

artifactA = Id.new_id(IdKind.ID_ARTIFACT)
artifactB = Id.new_id(IdKind.ID_ARTIFACT)
artifactC = Id.new_id(IdKind.ID_ARTIFACT)
artifactD = Id.new_id(IdKind.ID_ARTIFACT)
artifactE = Id.new_id(IdKind.ID_ARTIFACT)
artifactF = Id.new_id(IdKind.ID_ARTIFACT)

class TestDirectoryAgent:

    def setup_test_directories(self,
        anc_bindings: Dict[str, Id[Artifact]],
        src_bindings:  Dict[str, Id[Artifact]],
        tgt_bindings:  Dict[str, Id[Artifact]]
        )-> (ArtifactVersion, ArtifactVersion, ArtifactVersion): # type: ignore
        art_id: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        ancestor_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        source_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        target_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        arc_ver = ArtifactVersion(
            id = ancestor_id,
            artifact_id = art_id,
            artifact_type = DirectoryAgent.get().artifact_type,
            timestamp = datetime.now(),
            creator = "me",
            content = DirectoryAgent.get().encode_to_bytes(Directory(anc_bindings.copy())),
            parents = [],
            metadata = {},
            status = VersionStatus.Committed
        )
        src_ver = ArtifactVersion(
            id = source_id,
            artifact_id = art_id,
            artifact_type = DirectoryAgent.get().artifact_type,
            timestamp = datetime.now(),
            creator = "me",
            content = DirectoryAgent.get().encode_to_bytes(Directory(src_bindings.copy())),
            parents = [arc_ver.id],
            metadata = {},
            status = VersionStatus.Committed
        )
        tgt_ver = ArtifactVersion(
            id = target_id,
            artifact_id = art_id,
            artifact_type = DirectoryAgent.get().artifact_type,
            timestamp = datetime.now(),
            creator = "me",
            content = DirectoryAgent.get().encode_to_bytes(Directory(tgt_bindings.copy())),
            parents = [arc_ver.id],
            metadata = {},
            status = VersionStatus.Committed
        )
        return (arc_ver, src_ver, tgt_ver)

    def test_non_conflicting_merge(self):
        anc = { "a": artifactA,"b": artifactB,      "c": artifactC,       "d": artifactD }

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
            anc,
            src,
            tgt
        )

        result = DirectoryAgent.get().merge(ancestor, source, target)

        assert 0 == len(result.conflicts)
        proposed = DirectoryAgent.get().decode_from_bytes(result.proposed_merge)
        assert "a" == proposed.get_name_for(artifactA)
        assert "bb" == proposed.get_name_for(artifactB)
        assert "c"== proposed.get_name_for(artifactC)
        assert "dd"== proposed.get_name_for(artifactD)


    def test_non_conflicting_adds(self):
        anc_bindings = {
            "a": artifactA,
            "b": artifactB,
            "c": artifactC,
            "d": artifactD }
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
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = DirectoryAgent.get().merge(ancestor, source, target)

        assert 0 == len(result.conflicts)
        proposed = DirectoryAgent.get().decode_from_bytes(result.proposed_merge)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)
        assert "c" == proposed.get_name_for(artifactC)
        assert "d" == proposed.get_name_for(artifactD)
        assert "e" == proposed.get_name_for(artifactE)
        assert "f" == proposed.get_name_for(artifactF)

    def test_rename_rename_conflicts(self):
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
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = DirectoryAgent.get().merge(ancestorVer, sourceVer, targetVer)

        assert 1 == len(result.conflicts)
        assert sourceVer.id == result.conflicts[0].source_version
        assert targetVer.id == result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.MOD_MOD == detail.kind
        proposed = DirectoryAgent.get().decode_from_bytes(result.proposed_merge)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)

    def test_mod_del_conflicts(self):
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
            anc_bindings,
            src_bindings,
            tgt_bindings
        )

        result = DirectoryAgent.get().merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        assert src_ver.id == result.conflicts[0].source_version
        assert tgt_ver.id, result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.DEL_MOD == detail.kind
        proposed = DirectoryAgent.get().decode_from_bytes(result.proposed_merge)
        assert 4 == len(proposed.entries)
        assert "a" == proposed.get_name_for(artifactA)
        assert "b" == proposed.get_name_for(artifactB)
        assert "c" == proposed.get_name_for(artifactC)
        assert "e" == proposed.get_name_for(artifactD)

    def test_del_mod_conflicts(self):
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
            anc_bindings,
            src_bindings,
            tgt_bindings
        )
        result = DirectoryAgent.get().merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        assert src_ver.id == result.conflicts[0].source_version
        assert tgt_ver.id == result.conflicts[0].target_version
        detail = DirectoryMergeConflict.decode_from_bytes(result.conflicts[0].details)
        assert ConflictKind.MOD_DEL == detail.kind
        proposed = DirectoryAgent.get().decode_from_bytes(result.proposed_merge)
        assert 4 == len(proposed.entries)
        assert "a", proposed.get_name_for(artifactA)
        assert "b", proposed.get_name_for(artifactB)
        assert "c", proposed.get_name_for(artifactC)
        assert "e", proposed.get_name_for(artifactD)
