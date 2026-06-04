package com.example.paulasserver.admin

data class AdminTableDefinition(
    val name: String,
    val label: String,
    val fields: List<AdminFieldDefinition>,
)

data class AdminFieldDefinition(
    val name: String,
    val label: String,
    val kind: AdminFieldKind,
    val required: Boolean,
    val editable: Boolean,
    val visible: Boolean,
    val relationTable: String? = null,
    val enumValues: List<String> = emptyList(),
)

enum class AdminFieldKind {
    TEXT,
    PASSWORD,
    NUMBER,
    BOOLEAN,
    JSON,
    RELATION,
    ENUM,
    BINARY,
}

data class AdminReferenceOption(
    val id: Any,
    val label: String,
)

data class AdminSaveRequest(
    val values: Map<String, Any?> = emptyMap(),
)
