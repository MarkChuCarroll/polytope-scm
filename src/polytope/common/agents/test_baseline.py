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
# See the License for the specific language governing permissions and * limitations under the License.

from datetime import datetime
import tempfile
from typing import Dict, NamedTuple

from pytest import fixture

from polytope.common.agents.baseline import Baseline, BaselineAgent, BaselineConflict, BaselineConflictType
from polytope.common.stashable.artifact import Artifact, ArtifactVersion, VersionStatus
from polytope.common.stashable.ids import Id, IdKind
from polytope.server.depot.storage import FileStorage


class BaselineFamily(NamedTuple):
    ancestor: ArtifactVersion
    source: ArtifactVersion
    target: ArtifactVersion


class TestBaselineAgent:
    @fixture
    def storage(self):
        with tempfile.TemporaryDirectory(dir="tmp") as dir:
            storage = FileStorage(dir)
            yield storage

    @fixture
    def baseline_agent(self, storage) -> BaselineAgent:
        return BaselineAgent(storage)

    def setup_baselines(self,
                        baseline_agent: BaselineAgent,
                        ancestor_mapping: Dict[Id[Artifact], Id[ArtifactVersion]],
                        source_mapping: Dict[Id[Artifact], Id[ArtifactVersion]],
                        tgt_map: Dict[Id[Artifact], Id[ArtifactVersion]]
                        ) -> BaselineFamily:
        artifact = Artifact(
            id=Id.new_id(IdKind.ID_ARTIFACT),
            artifact_type="baseline",
            timestamp=datetime.now(),
            creator="markcc",
            project="testing",
            metadata={},
            versions=[]
        )
        ancestor_baseline = Baseline(
            root_dir=artifact.id,
            entries=ancestor_mapping.copy()
        )

        ancestor_id: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        ancestor_cid = baseline_agent.storage.put(
            baseline_agent.encode_to_bytes(ancestor_baseline))

        ancestor_ver = ArtifactVersion(
            id=ancestor_id,
            artifact_id=artifact.id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="markcc",
            parents=[],
            metadata={},
            status=VersionStatus.Committed,
            content_id=ancestor_cid
        )

        src_baseline = Baseline(
            root_dir=artifact.id,
            entries=source_mapping.copy()
        )

        source_cid = baseline_agent.storage.put(
            baseline_agent.encode_to_bytes(src_baseline))

        src_ver = ArtifactVersion(
            id=ancestor_id,
            artifact_id=artifact.id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="markcc",
            parents=[ancestor_ver.id],
            metadata={},
            status=VersionStatus.Committed,
            content_id=source_cid
        )

        tgt_baseline = Baseline(
            root_dir=artifact.id,
            entries=tgt_map.copy()
        )
        target_cid = baseline_agent.storage.put(
            baseline_agent.encode_to_bytes(tgt_baseline))

        tgt_ver = ArtifactVersion(
            id=ancestor_id,
            artifact_id=artifact.id,
            artifact_type="directory",
            timestamp=datetime.now(),
            creator="markcc",
            parents=[ancestor_ver.id],
            metadata={},
            status=VersionStatus.Committed,
            content_id=target_cid
        )

        return BaselineFamily(ancestor_ver, src_ver, tgt_ver)

    def test_non_conflicting_merge(self, baseline_agent: BaselineAgent):
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)

        anc_map = {
            a: a1, b: b1,
            c: c1,
            d: d1
        }
        src_map = {
            a: a2,
            b: b1,
            c: c1,
            d: d2
        }
        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d1
        }
        (anc_ver, src_ver, targetVer) = self.setup_baselines(baseline_agent,
                                                             anc_map, src_map, tgt_map
                                                             )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))
        result = baseline_agent.merge(anc_ver, src_ver, targetVer)

        assert 0 == len(result.conflicts)

        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 4 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d2 == proposed.get(d)

    def test_non_conflicting_merge_with_add(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        e: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        e1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)

        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }

        src_map = {
            a: a2,
            b: b1,
            c: c1,
            d: d2,
            e: e1
        }
        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d1
        }

        (ancestor, source, target) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map,
            tgt_map
        )

        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(ancestor.content_id))

        result = baseline_agent.merge(ancestor, source, target)

        assert 0 == len(result.conflicts)
        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 5 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d2 == proposed.get(d)
        assert e1 == proposed.get(e)

    def test_non_conflicting_add_add(self, baseline_agent: BaselineAgent):
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        e: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        e1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        f: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        f1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)

        anc_map = {

            a: a1,
            b: b1,
            c: c1,
            d: d1
        }
        src_map = {
            a: a2,
            b: b1,
            c: c1,
            d: d2,
            e: e1
        }

        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d1,
            f: f1
        }

        (ancestor, source, target) = self.setup_baselines(baseline_agent,
                                                          anc_map,
                                                          src_map,
                                                          tgt_map
                                                          )

        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(ancestor.content_id))

        result = baseline_agent.merge(ancestor, source, target)

        assert 0 == len(result.conflicts)
        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 6 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d2 == proposed.get(d)
        assert e1 == proposed.get(e)
        assert f1 == proposed.get(f)

    def test_non_conflicting_merge_with_deletes(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }

        src_map = {
            a: a2,
            b: b1,
            c: c1
        }

        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d1
        }

        (anc_ver, src_ver, target_ver) = self.setup_baselines(baseline_agent,
                                                              anc_map,
                                                              src_map,
                                                              tgt_map
                                                              )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, target_ver)

        assert 0 == len(result.conflicts)
        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 3 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)

    def test_non_conflicting_del_del(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }
        src_map = {
            a: a2,
            b: b1,
            c: c1,
        }
        tgt_map = {
            a: a1,
            b: b2,
            d: d1
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map,
            tgt_map
        )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 0 == len(result.conflicts)
        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 2 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)

    def test_mod_mod_merge(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d3: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }

        src_map = {
            a: a2,
            b: b1,
            c: c1,
            d: d2
        }
        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d3
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map,
            tgt_map
        )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        conflict = result.conflicts[0]
        cDetail = BaselineConflict.decode_from_bytes(conflict.details)
        assert BaselineConflictType.MOD_MOD == cDetail.type
        assert d == cDetail.artifact_id
        assert d2 == cDetail.merge_source_version
        assert d3 == cDetail.merge_target_version

        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 4 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d3 == proposed.get(d)

    def test_mod_del_merge(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }
        src_map = {
            a: a2,
            b: b1,
            c: c1,
            d: d2
        }
        tgt_map = {
            a: a1,
            b: b2,
            c: c2
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map,
            tgt_map
        )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        conflict = result.conflicts[0]
        cDetail = BaselineConflict.decode_from_bytes(conflict.details)
        assert BaselineConflictType.MOD_DEL == cDetail.type
        assert d == cDetail.artifact_id
        assert d2 == cDetail.merge_source_version
        assert None == cDetail.merge_target_version

        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 4 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d2 == proposed.get(d)

    def test_del_mod_merge(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }

        src_map = {
            a: a2,
            b: b1,
            c: c1
        }

        tgt_map = {
            a: a1,
            b: b2,
            c: c2,
            d: d2
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map,
            tgt_map
        )

        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 1 == len(result.conflicts)
        conflict = result.conflicts[0]
        cDetail = BaselineConflict.decode_from_bytes(conflict.details)
        assert BaselineConflictType.DEL_MOD == cDetail.type
        assert d == cDetail.artifact_id
        assert cDetail.merge_source_version is None
        assert d2 == cDetail.merge_target_version

        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 4 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
        assert d2 == proposed.get(d)

    def test_del_del(self, baseline_agent: BaselineAgent) -> None:
        a: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        a1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        a2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        b1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        b2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        c1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        c2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        d1: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        d2: Id[ArtifactVersion] = Id.new_id(IdKind.ID_VERSION)
        anc_map = {
            a: a1,
            b: b1,
            c: c1,
            d: d1
        }

        src_map = {
            a: a2,
            b: b1,
            c: c1
        }

        tgt_map = {
            a: a1,
            b: b2,
            c: c2
        }

        (anc_ver, src_ver, tgt_ver) = self.setup_baselines(
            baseline_agent,
            anc_map,
            src_map, tgt_map
        )
        anc_base = baseline_agent.decode_from_bytes(
            baseline_agent.storage.get(anc_ver.content_id))

        result = baseline_agent.merge(anc_ver, src_ver, tgt_ver)

        assert 0 == len(result.conflicts)

        proposed = baseline_agent.decode_from_bytes(result.proposed_merge)
        assert anc_base.root_dir == proposed.root_dir
        assert 3 == len(proposed.entries)
        assert a2 == proposed.get(a)
        assert b2 == proposed.get(b)
        assert c2 == proposed.get(c)
