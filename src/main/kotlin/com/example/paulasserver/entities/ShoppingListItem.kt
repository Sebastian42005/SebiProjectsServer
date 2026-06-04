package com.example.paulasserver.entities

import jakarta.persistence.*
import lombok.Data
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Table(name = "shopping_list_item")
@Entity
@Data
class ShoppingListItem {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null

    var name: String? = null

    @Column(nullable = false)
    val createdAt: Long = System.currentTimeMillis()

    var iconUrl: String? = null

    var isChecked: Boolean = false

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var additionalInfo: Map<String, String>? = null
}