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

import base64
from enum import Enum
from io import BytesIO
import json
import os
import pickle

from polytope.common.stashable.stashable import JDict
from polytope.common.util import filetype
from polytope.common.util.lcs import CrossVersionLineMapping, indexed_lcs
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id, IdKind
from polytope.common.util.filetype import FileType
from polytope.common.agents.agents import FileAgent, MergeConflict, MergeResult
from typing import Any, Dict, List, NamedTuple

from polytope.depot.storage.storage import Storage


class TextContent:
    """
    The representation of the content of text artifacts in Polytope.
    """
    lines: List[str]

    def __init__(self, lines: List[str]) -> None:
        self.lines = lines


class TextMergeConflict(NamedTuple):
    conflict_start: int
    conflict_end: int


class TextAgent(FileAgent[TextContent]):
    """An implementation of an agent for text artifacts that uses
    a (to my knowledge) new 3-way merge algorithm.

    The idea is, you convert a modified version into a list of labelled
    lines, where each line has a label that indicates whether it's
    unmodified; deleted (present in the ancestor but not in the modified),
    inserted (present in the modified, but not in the ancestor).

    Once the lines are labeled, then they're coalesced into blocks, by anchoring
    them against the first unmodified line from the ancestor that follows them.

    Finally, we do the merge by iterating over the lines from the common
    ancestor, which are also the anchors for any modifications. If there's
    one block anchored on a line, then that edit will be part of the merge
    result; if there's more than one, then we check for compatibility.

    In practice, this seems to avoid conflicts and generate good results in many places
    that would have been conflicts in the traditional diff-transform approach.
    """

    def __init__(self, storage: Storage):
        super().__init__(storage)
        # A set of file extensions that this agent can handle.
        self.extensions = set([".txt", ".java", ".kt", ".js", ".ts", ".json",
                               ".rs", ".py", ".rb", ".ltx", ".tex", ".md",
                               ".yaml", ".c", ".cc", "h", ".cpp"])

    def can_handle(self, path: str) -> bool:
        (_, ext) = os.path.splitext(path)
        return ext in self.extensions or filetype.of(path) == FileType.text

    def read_from_disk(self, path: str) -> TextContent:
        with open(path, "r", encoding="utf-8") as inp:
            text = inp.readlines()
            return TextContent(text)

    def write_to_disk(self, path: str, value: TextContent) -> None:
        with open(path, "w") as out:
            out.writelines(value.lines)

    def artifact_type(self) -> str:
        return "text"

     # Convert a text content to an array of bytes.

    def encode_to_bytes(self, content: TextContent) -> bytes:
        return "".join(content.lines).encode()

    def decode_from_bytes(self, content: bytes) -> TextContent:
        inp = BytesIO(content)
        return TextContent([l.decode() for l in inp.readlines()])

    def merge(self,
              ancestor: ArtifactVersion,
              source: ArtifactVersion,
              target: ArtifactVersion
              ) -> MergeResult:
        anc_content = self.storage.get(ancestor.content_id)
        src_content = self.storage.get(source.content_id)
        tgt_content = self.storage.get(target.content_id)
        return self.do_merge(
            ancestor.artifact_id,
            ancestor.id,
            source.id,
            target.id,
            self.decode_from_bytes(anc_content),
            self.decode_from_bytes(src_content),
            self.decode_from_bytes(tgt_content)
        )

    def coalesce_lines_to_blocks(self,
                                 src_labeled_lines: List[LabeledLine],
                                 tgt_labeled_lines: List[LabeledLine]) -> List[MergeBlock]:

        # A map from a line number in the base to a collection of lines that occur
        # before that line number in one of the mods. The anchor line of a block
        # is the line number of that block in the merge ancestor.
        line_map: Dict[int, MergeBlock] = {}
        for src_line in src_labeled_lines:
            if src_line.anchor_line in line_map:
                block = line_map[src_line.anchor_line]
            else:
                block = MergeBlock(src_line.anchor_line)
            block.src_lines.append(src_line)
            line_map[src_line.anchor_line] = block

        for tgt_line in tgt_labeled_lines:
            if tgt_line.anchor_line in line_map:
                block = line_map[tgt_line.anchor_line]
            else:
                block = MergeBlock(tgt_line.anchor_line)
            block.tgt_lines.append(tgt_line)
            line_map[tgt_line.anchor_line] = block

        result: List[MergeBlock] = []
        maxIdx = max(line_map.keys())
        for i in range(0, maxIdx + 1):
            x = line_map.get(i)
            if x is not None:
                result.append(x)
        return result

     # To label a modified version of a file, we'll walk through
     # the lines in the base and the modified.
     #
     # * If a line is in the LCS(base, mod), then that line is Unmodified.
     # * If a line is in the base, and _not_ in the modified then it's Deleted.
     # * If a line is in the modified but not the base, then it's Inserted.
    def create_labeled_list(self, base: List[str], modified: List[str]) -> List[LabeledLine]:
        lcs: List[CrossVersionLineMapping] = indexed_lcs(base, modified)
        result: List[LabeledLine] = []
        first_unprocessed_in_base = 0
        first_unprocessed_in_target = 0
        for line in lcs:
            if line.line_number_in_left > first_unprocessed_in_base:
                # Lines between firstUnprocessedInBase and line.first (the start of the LCS
                # segment's position in the base) were deleted before the start of the segment.
                for l in range(first_unprocessed_in_base, line.line_number_in_left):
                    result.append(
                        LabeledLine(
                            LineLabel.Deleted, base[l], l, None,
                            line.line_number_in_left
                        )
                    )
            if line.line_number_in_right > first_unprocessed_in_target:
                # Any lines from the target between firstUnprocessedInTarget and line.second (the start of the
                # next LCS segment in mod) should be labeled as inserted
                # before line.first
                for l in range(first_unprocessed_in_target, line.line_number_in_right):
                    result.append(
                        LabeledLine(
                            LineLabel.Inserted, modified[l], None, l,
                            line.line_number_in_left
                        )
                    )
            # Line from LCS should be labeled as unmodified.
            result.append(
                LabeledLine(
                    LineLabel.Unmodified, base[line.line_number_in_left],
                    line.line_number_in_left, line.line_number_in_right,
                    line.line_number_in_left + 1
                )
            )
            first_unprocessed_in_base = line.line_number_in_left + 1
            first_unprocessed_in_target = line.line_number_in_right + 1

        # Anything left over in base is a deleted line;
        for baseline in range(first_unprocessed_in_base, len(base)):
            result.append(LabeledLine(LineLabel.Deleted,
                          base[baseline], baseline, None, baseline + 1))
        # anything left over in the mod is an insert.
        for tgtline in range(first_unprocessed_in_target, len(modified)):
            result.append(LabeledLine(LineLabel.Inserted,
                          modified[tgtline], None, tgtline, tgtline + 1))
        return result

    def do_merge(
        self,
        artifact_id: Id[Artifact],
        ancestor_version_id: Id[ArtifactVersion],
        source_version_id: Id[ArtifactVersion],
        target_version_id: Id[ArtifactVersion],
        base: TextContent,
        merge_src: TextContent,
        merge_tgt: TextContent
    ) -> MergeResult:
        lab_src: List[LabeledLine] = self.create_labeled_list(base.lines, merge_src.lines)
        lab_tgt: List[LabeledLine] = self.create_labeled_list(base.lines, merge_tgt.lines)
        blocks: List[MergeBlock] = self.coalesce_lines_to_blocks(lab_src, lab_tgt)

        result: List[str] = []
        all_conflicts: List[MergeConflict] = []
        for block in blocks:
            block.render(
                source_label="merge source ($sourceVersionId)",
                target_label="merge target $(targetVersionId)",
                artifact_id=artifact_id,
                source_version_id=source_version_id,
                target_version_id=target_version_id,
                result=result
            )

        return MergeResult(
            artifact_type=self.artifact_type(),
            artifact_id=artifact_id,
            ancestor_version=ancestor_version_id,
            source_version=source_version_id,
            target_version=target_version_id,
            proposed_merge=self.encode_to_bytes(TextContent(result)),
            conflicts=all_conflicts)


class LineLabel(Enum):
    Deleted = "d"
    Inserted = "i"
    Unmodified = "u"

 # Lines labelled with information about how they differ from a base version.


class LabeledLine(NamedTuple):
    label: LineLabel
    content: str
    base_line: int | None
    target_line: int | None
    anchor_line: int

    def to_dict(self) -> Dict[str, Any]:
        return {
            "label": self.label.value,
            "content": self.content,
            "base_line": self.base_line,
            "target_line": self.target_line,
            "anchor_line": self.anchor_line
        }

    def __repr__(self) -> str:
        return f"LabeledLine({self.label}, {self.content}, {self.base_line}, {self.target_line}, {self.anchor_line})"

 # Check if two labeled lines match.
 # Matching means that the two correspond to an equivalent edit:
 # * deleting the same line;
 # * inserting the same text in the same position;
 # * leaving the same text unmodified.


def lines_match(first: LabeledLine, second: LabeledLine) -> bool:
    return (second.label == first.label and
            second.base_line == first.base_line and
            second.anchor_line == first.anchor_line and
            second.content == first.content)

 # A representation of a block of modified text from two different edits.
 # A block is anchored by a line of text from the original document which comes before
 # the edits. (This has the somewhat confusing effect that a file with 10 lines will have 11
 # indices - index[10] means "before the invisible line at the end of the file")
 # /


class MergeBlock:
    anchor_line: int
    src_lines: List[LabeledLine]
    tgt_lines: List[LabeledLine]

    def __init__(self, l: int, src_lines: List[LabeledLine] | None = None,
                 tgt_lines: List[LabeledLine] | None = None) -> None:
        self.anchor_line = l
        if src_lines is None:
            self.src_lines = []
        else:
            self.src_lines = src_lines
        if tgt_lines is None:
            self.tgt_lines = []
        else:
            self.tgt_lines = tgt_lines

    def to_dict(self) -> JDict:
        return {
            "anchor_line": self.anchor_line,
            "src_lines": [j.to_dict() for j in self.src_lines],
            "tgt_lines": [j.to_dict() for j in self.tgt_lines]
        }

    def __repr__(self) -> str:
        return f"MergeBlock({self.anchor_line}, src_lines={self.src_lines}, tgt_lines={self.tgt_lines})"

     # Checks if the two branches of a merge block correspond to the same edit.
    def matches(self) -> bool:
        return (len(self.src_lines) == len(self.tgt_lines) and
                all([lines_match(a, b) for (a, b) in zip(self.src_lines, self.tgt_lines)]))

     # Generate the merge result of the labeled lines anchored at this point.
    def render(
        self,
        source_label: str,
        target_label: str,
        artifact_id: Id[Artifact],
        source_version_id: Id[ArtifactVersion],
        target_version_id: Id[ArtifactVersion],
        result: List[str]
    ) -> List[MergeConflict]:
        conflicts: List[MergeConflict] = []
        if self.matches():
            # If the two blocks match - that is, they generate to the same edit -
            # then we just return either one of them.
            for l in self.src_lines:
                if l.label == LineLabel.Inserted or l.label == LineLabel.Unmodified:
                    result.append(l.content)
        elif all([l.label == LineLabel.Unmodified for l in self.tgt_lines]):
            # If all the target lines are unmodified, then the merge result is
            # the lines from the merge source.
            for l in self.src_lines:
                if l.label == LineLabel.Inserted or l.label == LineLabel.Unmodified:
                    result.append(l.content)
        elif all([l.label == LineLabel.Unmodified for l in self.src_lines]):
            #  Similarly, if the source lines are unmodified, then the merge result
            # is the lines from the target.
            for l in self.tgt_lines:
                if l.label == LineLabel.Inserted or l.label == LineLabel.Unmodified:
                    result.append(l.content)
        else:
            # Otherwise, we have a conflict between the source and target.
            conflict_block_start = len(result)
            result.append(f"[[[[[[ VERSION FROM {source_label}\n")
            for l in self.src_lines:
                if l.label == LineLabel.Inserted or l.label == LineLabel.Unmodified:
                    result.append(l.content)
                result.append(f"====== VERSION FROM {target_label}\n")
                for l in self.tgt_lines:
                    if l.label == LineLabel.Inserted or l.label == LineLabel.Unmodified:
                        result.append(l.content)
                result.append("]]]]]]\n")
                conflict_block_end = len(result)
                conflicts.append(
                    MergeConflict(
                        id=Id.new_id(IdKind.ID_CONFLICT),
                        artifact_id=artifact_id,
                        artifact_type="text",
                        source_version=source_version_id,
                        target_version=target_version_id,
                        details=base64.b64encode(pickle.dumps(json.dumps(TextMergeConflict(conflict_block_start, conflict_block_end))))))

        return conflicts
