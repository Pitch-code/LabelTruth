package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.Grade

/**
 * Summary figures for the home screen.
 *
 * Kept out of the UI so it can be tested, and deliberately narrow: it reports
 * only what was actually stored. There is no attempt here to count "safe" or
 * "risky" *ingredients* across scans, because per-ingredient verdicts are not
 * persisted — only the overall score of each scan is. Inventing that breakdown
 * would mean presenting a number we cannot substantiate.
 */
object ScanStats {

    /**
     * How many scans fall into each score band.
     *
     * Every band is present, including empty ones, so the row does not change
     * shape as scans accumulate.
     */
    fun distribution(scores: List<Int>): Map<Grade, Int> =
        Grade.entries.associateWith { grade -> scores.count { Grade.of(it) == grade } }

    /**
     * The mean score, or null when there is nothing to average.
     *
     * Null rather than zero: no scans is not the same as scoring zero, and the
     * home screen must not imply otherwise.
     */
    fun averageScore(scores: List<Int>): Int? =
        if (scores.isEmpty()) null else scores.sum() / scores.size
}
