package org.goodmath.polytope.depot.stashes

import org.goodmath.polytope.Config


interface Stash {
    fun initStorage(config: Config)
}
