package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.InstagramAccount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface InstagramAccountRepository : JpaRepository<InstagramAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from InstagramAccount account where account.id = 1")
    fun findSingletonForUpdate(): InstagramAccount?
}
