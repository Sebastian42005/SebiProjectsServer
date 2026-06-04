package com.example.paulasserver.repositories

import com.example.paulasserver.entities.Question
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface QuestionRepository : JpaRepository<Question, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Question q SET q.userKnows = :userKnows WHERE q.id = :id")
    fun setQuestionUserKnows(
        @Param("id") id: Long,
        @Param("userKnows") userKnows: Boolean
    ): Int
}