package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.Project
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProjectRepository: JpaRepository<Project, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Question q SET q.userKnows = false WHERE q.project.id = :projectId ")
    fun resetQuestions(projectId: Long)
}