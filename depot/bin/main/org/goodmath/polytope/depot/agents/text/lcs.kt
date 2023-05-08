/*
 * Copyright 2023 Mark C. Chu-Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.goodmath.polytope.depot.agents.text

data class CrossVersionLineMapping(
    val lineNumberInLeft: Int,
    val lineNumberInRight: Int)

fun<T> List<T>.prepend(x: T): List<T> =
    listOf(x) + this

/**
 * An implementation of the dynamic programming algorithm for the longest common subsequence.
 * * if last(left) == last(right): the result is lcs(left[:-1], right[:-1]) + last(left)
 * * if last(left) != last(right): lcs is the longer of lcs(left[0:-1], right) and
 * lcs(left, right[]0:-1]).
 *
 * Instead of doing the classic dynamic programming table, I think
 * it's clearer to just use memoization. Same thing in the end.
 *
 * This returns an indexed representation of the LCS, where each element
 * of the result is a pair containing the position of the
 * line in the left and right inputs.
 */


fun indexedLcs(left: List<String>, right: List<String>): List<CrossVersionLineMapping> =
    computeMemoized(left, left.size, right, right.size, HashMap()).reversed()


fun computeMemoized(
    left: List<String>, leftLength: Int,
    right: List<String>, rightLength: Int,
    memoTable: MutableMap<Pair<Int, Int>, List<CrossVersionLineMapping>>): List<CrossVersionLineMapping> {
    val memoizedResult = memoTable[Pair(leftLength, rightLength)]
    return if (memoizedResult == null) {
        val newResult = computeLcs(
            left, leftLength,
            right, rightLength,
            memoTable)
        memoTable[Pair(leftLength, rightLength)] = newResult
        newResult
    } else {
        memoizedResult
    }
}

private fun computeLcs(
    left: List<String>,
    leftLength: Int,
    right: List<String>,
    rightLength: Int,
    memoTable: MutableMap<Pair<Int, Int>, List<CrossVersionLineMapping>>): List<CrossVersionLineMapping> {
    if (leftLength == 0 || rightLength == 0) {
        return emptyList()
    }
    return if (left[leftLength - 1] == right[rightLength - 1]) {
        val subSolution: List<CrossVersionLineMapping> = computeMemoized(left, leftLength - 1,
            right, rightLength - 1, memoTable)
        subSolution.prepend(CrossVersionLineMapping(leftLength - 1, rightLength - 1))
    } else {
        val rightBiased = computeMemoized(left, leftLength - 1, right, rightLength, memoTable)
        val leftBiased = computeMemoized(left, leftLength, right, rightLength - 1, memoTable)
        if (rightBiased.size > leftBiased.size) {
            rightBiased
        } else {
            leftBiased
        }
    }
}

