package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.AppRole
import com.example.sebisprojectsserver.entities.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun findAllByOrderByIdAsc(): List<AppUser>
    fun findFirstByRoleOrderByIdAsc(role: AppRole): AppUser?
}
