package com.example.paulasserver.repositories

import com.example.paulasserver.entities.VoiceMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VoiceMessageRepository : JpaRepository<VoiceMessage, Long> {

    @Query("""
    SELECT v 
    FROM VoiceMessage v 
    ORDER BY v.createdAt DESC
""")
    fun getLatestVoiceMessage(pageable: Pageable): List<VoiceMessage>

    @Query("""
    SELECT v.seconds 
    FROM VoiceMessage v 
    ORDER BY v.createdAt DESC
""")
    fun getLatestVoiceMessageDuration(pageable: Pageable): List<Double>
}
