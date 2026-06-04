package com.example.paulasserver.repositories

import com.example.paulasserver.entities.AppRole
import com.example.paulasserver.entities.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun findAllByOrderByIdAsc(): List<AppUser>
    fun findFirstByRoleOrderByIdAsc(role: AppRole): AppUser?
}
