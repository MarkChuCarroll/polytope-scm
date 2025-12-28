# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from datetime import datetime
import json
import tempfile
from typing import List, Tuple

from pytest import fixture

from polytope.common.agents.text import (
    LabeledLine,
    LineLabel,
    MergeBlock,
    TextContent,
    TextAgent,
)
from polytope.common.stashable.artifact import Artifact, ArtifactVersion, VersionStatus
from polytope.common.stashable.ids import Id, IdKind
from polytope.depot.storage.storage import Content, FileStorage, Storage


class TestTextAgent:

    @fixture
    def storage(self):
        with tempfile.TemporaryDirectory(dir="tmp") as dir:
            storage = FileStorage(dir)
            yield storage

    @fixture
    def text_agent(self, storage) -> TextAgent:
        return TextAgent(storage)

    def build_text_versions(
        self,
        agent: TextAgent,
        ancestor_content: List[str],
        source_content: List[str],
        target_content: List[str],
    ) -> Tuple[ArtifactVersion, ArtifactVersion, ArtifactVersion]:
        art_id: Id[Artifact] = Id.new_id(IdKind.ID_ARTIFACT)
        ancestor_cid = agent.storage.put(
            agent.encode_to_bytes(TextContent(ancestor_content)))
        source_cid = agent.storage.put(
            agent.encode_to_bytes(TextContent(source_content)))
        target_cid = agent.storage.put(
            agent.encode_to_bytes(TextContent(target_content)))
        ancestor_ver = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=art_id,
            artifact_type="text",
            timestamp=datetime.now(),
            creator="me",
            content_id=ancestor_cid,
            parents=[],
            metadata={},
            status=VersionStatus.Committed,
        )
        sourceVer = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=art_id,
            artifact_type="text",
            timestamp=datetime.now(),
            creator="me",
            content_id=source_cid,
            parents=[ancestor_ver.id],
            metadata={},
            status=VersionStatus.Committed,
        )
        targetVer = ArtifactVersion(
            id=Id.new_id(IdKind.ID_VERSION),
            artifact_id=art_id,
            artifact_type="text",
            timestamp=datetime.now(),
            creator="me",
            content_id=target_cid,
            parents=[ancestor_ver.id],
            metadata={},
            status=VersionStatus.Committed,
        )
        return (ancestor_ver, sourceVer, targetVer)

    def test_encode_decode_text_content(self, text_agent: TextAgent) -> None:
        content = ["aaa\n", "bbb\n", "ccc\n", "ddd\n", "eeee\n"]
        encoded = text_agent.encode_to_bytes(TextContent(content))
        decoded = text_agent.decode_from_bytes(encoded)
        assert content == decoded.lines

    def test_line_labeled_diff(self, storage: Storage, text_agent: TextAgent) -> None:
        ancestor_text = ["a\n", "b\n", "c\n", "d\n", "e\n"]
        source_text = ["a\n", "c\n", "q\n", "d\n", "e\n"]

        lab = text_agent.create_labeled_list(ancestor_text, source_text)
        assert 6 == len(lab)
        assert LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1) == lab[0]
        assert LabeledLine(LineLabel.Deleted, "b\n", 1, None, 2) == lab[1]
        assert LabeledLine(LineLabel.Unmodified, "c\n", 2, 1, 3) == lab[2]
        assert LabeledLine(LineLabel.Inserted, "q\n", None, 2, 3) == lab[3]
        assert LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4) == lab[4]
        assert LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5) == lab[5]

    def test_coalesce_lines_into_blocks(self, storage: Storage, text_agent: TextAgent) -> None:
        ancestor_text = ["a\n", "b\n", "c\n", "d\n", "e\n"]
        source_text = ["a\n", "c\n", "q\n", "d\n", "e\n"]
        target_text = ["a\n", "b\n", "c\n", "d\n", "e\n"]

        labeled_src = text_agent.create_labeled_list(
            ancestor_text, source_text
        )
        labeled_tgt = text_agent.create_labeled_list(
            ancestor_text, target_text
        )
        coalesced = text_agent.coalesce_lines_to_blocks(
            labeled_src, labeled_tgt
        )
        ct = json.dumps(
            [j.to_dict() for j in coalesced], separators=(",", ":"), indent=2
        )
        expected = [
            MergeBlock(
                1,
                [LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1)],
                [LabeledLine(LineLabel.Unmodified, "a\n", 0, 0, 1)],
            ),
            MergeBlock(
                2,
                [LabeledLine(LineLabel.Deleted, "b\n", 1, None, 2)],
                [LabeledLine(LineLabel.Unmodified, "b\n", 1, 1, 2)],
            ),
            MergeBlock(
                3,
                [
                    LabeledLine(LineLabel.Unmodified, "c\n", 2, 1, 3),
                    LabeledLine(LineLabel.Inserted, "q\n", None, 2, 3),
                ],
                [LabeledLine(LineLabel.Unmodified, "c\n", 2, 2, 3)],
            ),
            MergeBlock(
                4,
                [LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4)],
                [LabeledLine(LineLabel.Unmodified, "d\n", 3, 3, 4)],
            ),
            MergeBlock(
                5,
                [LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5)],
                [LabeledLine(LineLabel.Unmodified, "e\n", 4, 4, 5)],
            ),
        ]
        et = json.dumps(
            [j.to_dict() for j in expected], separators=(",", ":"), indent=2
        )
        assert ct == et

    def test_merge_no_conflicts(self, storage: Storage, text_agent: TextAgent) -> None:
        ancestor_text = ["a\n", "b\n", "c\n", "d\n", "e\n"]
        source_text = ["a\n", "c\n", "q\n", "d\n", "e\n"]
        target_text = ["a\n", "b\n", "c\n", "d\n", "e\n"]

        (anc, src, tgt) = self.build_text_versions(text_agent,
                                                   ancestor_text, source_text, target_text
                                                   )

        merge = text_agent.merge(anc, src, tgt)
        proposed = text_agent.decode_from_bytes(merge.proposed_merge)
        assert source_text == proposed.lines
        assert 0 == len(merge.conflicts)
