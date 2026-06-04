package com.example.paulasserver.entities

import jakarta.persistence.*
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

@Table(name = "project")
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null
    var name: String? = null

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true)
    var questions: MutableList<Question> = mutableListOf()

}