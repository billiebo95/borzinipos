package com.borzini.pos.data.repository

import java.util.UUID

/** Central place for generating new row ids so every table uses the same UUID string style. */
object IdGenerator {
    fun newId(): String = UUID.randomUUID().toString()
}
