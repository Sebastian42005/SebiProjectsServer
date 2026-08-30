package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.InstagramRedemption
import org.springframework.data.jpa.repository.JpaRepository

interface InstagramRedemptionRepository : JpaRepository<InstagramRedemption, Long> {
    fun findByRequestKey(requestKey: String): InstagramRedemption?
}
