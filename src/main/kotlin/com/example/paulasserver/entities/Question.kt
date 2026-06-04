package com.example.paulasserver.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import lombok.Data

@Table(name = "questions")
@Entity
@Data
class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null
    var question: String? = null
    var answer: String? = null
    var userKnows: Boolean = false
    var category: String? = null

    @JsonIgnore
    var contentType: String? = null
    @JsonIgnore
    var content: ByteArray? = null

    @ManyToOne
    @JsonIgnore
    var project: Project? = null
}