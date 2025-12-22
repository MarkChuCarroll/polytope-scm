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

from typing import Dict, List, NamedTuple, Tuple

class CrossVersionLineMapping(NamedTuple):
    line_number_in_left: int
    line_number_in_right: int

# An implementation of the dynamic programming algorithm for the longest common subsequence.
# * if last(left) == last(right): the result is lcs(left[:-1], right[:-1]) + last(left)
# * if last(left) != last(right): lcs is the longer of lcs(left[0:-1], right) and
# lcs(left, right[]0:-1]).
#
# Instead of doing the classic dynamic programming table, I think
# it's clearer to just use memoization. Same thing in the end.
#
# This returns an indexed representation of the LCS, where each element
# of the result is a pair containing the position of the
# line in the left and right inputs.


def indexed_lcs[T](left: List[T], right: List[T]) -> List[CrossVersionLineMapping]:
    result = compute_memoized(left, len(left), right, len(right), {})
    result.reverse()
    return result


def compute_memoized[T](
    left: List[T],
    left_length: int,
    right: List[T],
    right_length: int,
    memo_table: Dict[Tuple[int, int], List[CrossVersionLineMapping]],
) -> List[CrossVersionLineMapping]:
    memoized_result = memo_table.get((left_length, right_length))
    if memoized_result is None:
        new_result = compute_lcs(left, left_length, right, right_length, memo_table)
        memo_table[(left_length, right_length)] = new_result
        return new_result
    else:
        return memoized_result


def lcs[T](left: List[T], right: List[T]) -> List[CrossVersionLineMapping]:
    result = compute_memoized(left, len(left), right, len(right), {})
    result.reverse()
    return result


def compute_lcs[T](
    left: List[T],
    left_length: int,
    right: List[T],
    right_length: int,
    memo_table: Dict[Tuple[int, int], List[CrossVersionLineMapping]],
) -> List[CrossVersionLineMapping]:
    if left_length == 0 or right_length == 0:
        return []
    if left[left_length - 1] == right[right_length - 1]:
        subsolution = compute_memoized(
            left, left_length - 1, right, right_length - 1, memo_table
        )
        subsolution = [
            CrossVersionLineMapping(left_length - 1, right_length - 1)
        ] + subsolution
        return subsolution
    else:
        right_biased = compute_memoized(
            left, left_length - 1, right, right_length, memo_table
        )
        left_biased = compute_memoized(
            left, left_length, right, right_length - 1, memo_table
        )
        if len(right_biased) > len(left_biased):
            return right_biased
        else:
            return left_biased
