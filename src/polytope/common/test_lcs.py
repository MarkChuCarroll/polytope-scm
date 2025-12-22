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

from polytope.common.util.lcs import CrossVersionLineMapping, lcs

class TestLCS:
    simple_list_one = [1, 2, 3, 4, 5, 6, 7, 8, 9]
    simple_list_two = [2, 3, 7, 5, 6]

    complicated_list_one = [
        5,
        1,
        0,
        8,
        3,
        4,
        6,
        2,
        1,
        5,
        3,
        7,
        2,
        2,
        3,
        5,
        9,
        3,
        4,
        5,
        9,
        6,
        2,
        2,
        1,
    ]
    complicated_list_two = [
        6,
        2,
        3,
        3,
        4,
        7,
        4,
        4,
        7,
        6,
        4,
        4,
        1,
        0,
        3,
        9,
        1,
        6,
        6,
        8,
        0,
        5,
        1,
        5,
        2,
    ]

    def test_compute_lcs(self):
        one = [
            self.simple_list_one[x.line_number_in_left]
            for x in lcs(self.simple_list_one, self.simple_list_two)
        ]

        assert one == [2, 3, 5, 6]
        two = [
            self.complicated_list_one[x.line_number_in_left]
            for x in lcs(self.complicated_list_one, self.complicated_list_two)
        ]
        assert two == [6, 2, 3, 3, 4, 9, 6, 1]

    def test_position_mappings_in_lcs(self):
        mapped_lcs = lcs(self.simple_list_one, self.simple_list_two)
        assert mapped_lcs == [
            CrossVersionLineMapping(1, 0),
            CrossVersionLineMapping(2, 1),
            CrossVersionLineMapping(4, 3),
            CrossVersionLineMapping(5, 4),
        ]

        complex_mapped_lcs = lcs(self.complicated_list_one, self.complicated_list_two)
        assert complex_mapped_lcs == [
            CrossVersionLineMapping(6, 0),
            CrossVersionLineMapping(13, 1),
            CrossVersionLineMapping(14, 2),
            CrossVersionLineMapping(17, 3),
            CrossVersionLineMapping(18, 4),
            CrossVersionLineMapping(20, 15),
            CrossVersionLineMapping(21, 17),
            CrossVersionLineMapping(24, 22),
        ]

    def test_empty_lcs(self):
        assert lcs([1, 2, 3, 4, 5], [6, 7, 8, 9]) == []
