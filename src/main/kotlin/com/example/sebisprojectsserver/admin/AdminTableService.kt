package com.example.sebisprojectsserver.admin

import com.example.sebisprojectsserver.entities.AppUser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.EntityManager
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.PluralAttribute
import jakarta.transaction.Transactional
import org.springframework.beans.BeanWrapperImpl
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Modifier
import java.util.Base64

@Service
class AdminTableService(
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
    private val passwordEncoder: PasswordEncoder,
) {

    private val persistenceUnitUtil = entityManager.entityManagerFactory.persistenceUnitUtil
    private val mapTypeReference = object : TypeReference<Map<String, Any?>>() {}

    fun getTables(): List<AdminTableDefinition> {
        return manageableEntities()
            .map(::buildTableDefinition)
            .sortedBy { it.name }
    }

    fun getRows(table: String): List<Map<String, Any?>> {
        val entityType = resolveEntityType(table)
        val rows = entityManager
            .createQuery("select entity from ${entityType.name} entity", entityType.javaType)
            .resultList

        return rows
            .sortedWith(compareBy { sortableIdentifier(persistenceUnitUtil.getIdentifier(it)) })
            .map { serializeEntity(entityType, it) }
    }

    fun getReferenceOptions(table: String, fieldName: String): List<AdminReferenceOption> {
        val entityType = resolveEntityType(table)
        val attribute = resolveAttribute(entityType, fieldName)
        requireRelationAttribute(attribute, fieldName)

        val targetEntityType = entityManager.metamodel.entity(attribute.javaType)
        val rows = entityManager
            .createQuery("select entity from ${targetEntityType.name} entity", targetEntityType.javaType)
            .resultList

        return rows
            .sortedBy { displayValue(it) }
            .map {
                AdminReferenceOption(
                    id = persistenceUnitUtil.getIdentifier(it),
                    label = displayValue(it),
                )
            }
    }

    @Transactional
    fun createRow(table: String, values: Map<String, Any?>): Map<String, Any?> {
        val entityType = resolveEntityType(table)
        val entity = entityType.javaType.getDeclaredConstructor().newInstance()
        applyValues(entityType, entity, values)
        if (entity is AppUser && entity.passwordHash.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required for new users")
        }
        entityManager.persist(entity)
        entityManager.flush()
        return serializeEntity(entityType, entity)
    }

    @Transactional
    fun updateRow(table: String, id: Long, values: Map<String, Any?>): Map<String, Any?> {
        val entityType = resolveEntityType(table)
        val entity = entityManager.find(entityType.javaType, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No row found for id=$id in table $table")

        applyValues(entityType, entity, values)
        entityManager.flush()
        return serializeEntity(entityType, entity)
    }

    private fun manageableEntities(): List<EntityType<*>> {
        return entityManager.metamodel.entities
            .filterNot { it.javaType.isAnnotationPresent(AdminInternalEntity::class.java) }
    }

    private fun resolveEntityType(table: String): EntityType<*> {
        return manageableEntities()
            .firstOrNull { tableName(it).equals(table, ignoreCase = true) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown admin table: $table")
    }

    private fun resolveAttribute(entityType: EntityType<*>, fieldName: String): Attribute<*, *> {
        return entityType.attributes.firstOrNull { it.name == fieldName }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown field '$fieldName' in ${tableName(entityType)}")
    }

    private fun buildTableDefinition(entityType: EntityType<*>): AdminTableDefinition {
        return AdminTableDefinition(
            name = tableName(entityType),
            label = labelize(tableName(entityType)),
            fields = entityType.attributes
                .filterNot { it is PluralAttribute<*, *, *> }
                .map { attribute ->
                    val field = fieldFor(entityType, attribute.name)
                    val kind = attributeKind(entityType, attribute)
                    AdminFieldDefinition(
                        name = attribute.name,
                        label = if (isPasswordField(entityType, attribute)) "Passwort" else labelize(attribute.name),
                        kind = kind,
                        required = isRequired(entityType, field, attribute),
                        editable = isEditable(field, attribute),
                        visible = kind != AdminFieldKind.PASSWORD,
                        relationTable = if (kind == AdminFieldKind.RELATION) {
                            tableName(entityManager.metamodel.entity(attribute.javaType))
                        } else {
                            null
                        },
                        enumValues = if (kind == AdminFieldKind.ENUM) {
                            attribute.javaType.enumConstants.map { it.toString() }
                        } else {
                            emptyList()
                        },
                    )
                }
                .sortedWith(compareBy<AdminFieldDefinition> { if (it.name == "id") 0 else 1 }.thenBy { it.name }),
        )
    }

    private fun serializeEntity(entityType: EntityType<*>, entity: Any): Map<String, Any?> {
        val wrapper = BeanWrapperImpl(entity)
        val fieldDefinitions = buildTableDefinition(entityType).fields
        val serialized = linkedMapOf<String, Any?>()

        for (field in fieldDefinitions) {
            val value = wrapper.getPropertyValue(field.name)
            serialized[field.name] = when (field.kind) {
                AdminFieldKind.RELATION -> serializeRelation(value)
                AdminFieldKind.BINARY -> serializeBinary(value)
                AdminFieldKind.PASSWORD -> null
                else -> value
            }
        }

        return serialized
    }

    private fun serializeRelation(value: Any?): Any? {
        if (value == null) {
            return null
        }

        return mapOf(
            "id" to persistenceUnitUtil.getIdentifier(value),
            "label" to displayValue(value),
        )
    }

    private fun serializeBinary(value: Any?): Any {
        val bytes = value as? ByteArray ?: ByteArray(0)
        return mapOf(
            "present" to bytes.isNotEmpty(),
            "size" to bytes.size,
        )
    }

    private fun applyValues(entityType: EntityType<*>, entity: Any, values: Map<String, Any?>) {
        val wrapper = BeanWrapperImpl(entity)
        val isNewEntity = persistenceUnitUtil.getIdentifier(entity) == null

        for (attribute in entityType.attributes.filterNot { it is PluralAttribute<*, *, *> }) {
            val field = fieldFor(entityType, attribute.name)
            if (!isEditable(field, attribute) || !values.containsKey(attribute.name)) {
                continue
            }

            if (isPasswordField(entityType, attribute)) {
                val rawPassword = values[attribute.name]?.toString()
                if (rawPassword.isNullOrBlank()) {
                    if (isNewEntity) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required for new users")
                    }
                    continue
                }
            }

            val convertedValue = convertValue(entityType, attribute, values[attribute.name])
            wrapper.setPropertyValue(attribute.name, convertedValue)
        }
    }

    private fun convertValue(entityType: EntityType<*>, attribute: Attribute<*, *>, rawValue: Any?): Any? {
        if (rawValue == null) {
            return null
        }

        return when (attributeKind(entityType, attribute)) {
            AdminFieldKind.RELATION -> convertRelation(attribute, rawValue)
            AdminFieldKind.BOOLEAN -> when (rawValue) {
                is Boolean -> rawValue
                else -> rawValue.toString().toBooleanStrictOrNull()
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid boolean value for ${attribute.name}")
            }

            AdminFieldKind.NUMBER -> convertNumber(attribute.javaType, rawValue)
            AdminFieldKind.JSON -> convertJsonValue(rawValue)
            AdminFieldKind.ENUM -> convertEnumValue(attribute.javaType, rawValue)
            AdminFieldKind.BINARY -> convertBinaryValue(rawValue)
            AdminFieldKind.PASSWORD -> passwordEncoder.encode(rawValue.toString())
            AdminFieldKind.TEXT -> rawValue.toString()
        }
    }

    private fun convertRelation(attribute: Attribute<*, *>, rawValue: Any): Any? {
        val relationId = when (rawValue) {
            is Map<*, *> -> rawValue["id"]
            else -> rawValue
        } ?: return null

        val targetEntityType = entityManager.metamodel.entity(attribute.javaType)
        val targetId = coerceIdentifier(targetEntityType.idType.javaType, relationId)
        return entityManager.getReference(attribute.javaType, targetId)
    }

    private fun convertNumber(targetType: Class<*>, rawValue: Any): Any {
        val number = when (rawValue) {
            is Number -> rawValue
            else -> rawValue.toString().toDoubleOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number value for $targetType")
        }

        return when (targetType) {
            java.lang.Long::class.java, Long::class.javaPrimitiveType -> number.toLong()
            java.lang.Integer::class.java, Int::class.javaPrimitiveType -> number.toInt()
            java.lang.Double::class.java, Double::class.javaPrimitiveType -> number.toDouble()
            java.lang.Float::class.java, Float::class.javaPrimitiveType -> number.toFloat()
            java.lang.Short::class.java, Short::class.javaPrimitiveType -> number.toShort()
            java.lang.Byte::class.java, Byte::class.javaPrimitiveType -> number.toByte()
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported numeric type: $targetType")
        }
    }

    private fun convertJsonValue(rawValue: Any): Map<String, String> {
        return when (rawValue) {
            is Map<*, *> -> rawValue.entries.associate { (key, value) ->
                key.toString() to (value?.toString() ?: "")
            }

            is String -> {
                if (rawValue.isBlank()) {
                    emptyMap()
                } else {
                    objectMapper.readValue(rawValue, mapTypeReference).entries.associate { (key, value) ->
                        key to (value?.toString() ?: "")
                    }
                }
            }

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported JSON value for admin field")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertEnumValue(enumType: Class<*>, rawValue: Any): Any {
        return java.lang.Enum.valueOf(enumType as Class<out Enum<*>>, rawValue.toString())
    }

    private fun convertBinaryValue(rawValue: Any): ByteArray {
        val base64 = rawValue.toString()
        if (base64.isBlank()) {
            return ByteArray(0)
        }

        return try {
            Base64.getDecoder().decode(base64)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid binary payload for admin field")
        }
    }

    private fun coerceIdentifier(targetType: Class<*>, rawValue: Any): Any {
        return when (targetType) {
            java.lang.Long::class.java, Long::class.javaPrimitiveType -> rawValue.toString().toLong()
            java.lang.Integer::class.java, Int::class.javaPrimitiveType -> rawValue.toString().toInt()
            String::class.java -> rawValue.toString()
            else -> rawValue
        }
    }

    private fun isEditable(field: java.lang.reflect.Field?, attribute: Attribute<*, *>): Boolean {
        if (field == null || Modifier.isFinal(field.modifiers)) {
            return false
        }

        return field.getAnnotation(jakarta.persistence.GeneratedValue::class.java) == null
    }

    private fun isRequired(entityType: EntityType<*>, field: java.lang.reflect.Field?, attribute: Attribute<*, *>): Boolean {
        if (isPasswordField(entityType, attribute)) {
            return false
        }

        val column = field?.getAnnotation(Column::class.java)
        if (column != null) {
            return !column.nullable
        }

        val manyToOne = field?.getAnnotation(ManyToOne::class.java)
        if (manyToOne != null) {
            return !manyToOne.optional
        }

        return when (attributeKind(entityType, attribute)) {
            AdminFieldKind.BOOLEAN,
            AdminFieldKind.NUMBER,
            AdminFieldKind.BINARY,
            AdminFieldKind.ENUM,
            AdminFieldKind.PASSWORD,
            -> false

            else -> false
        }
    }

    private fun attributeKind(entityType: EntityType<*>, attribute: Attribute<*, *>): AdminFieldKind {
        if (isPasswordField(entityType, attribute)) {
            return AdminFieldKind.PASSWORD
        }

        if (attribute.isAssociation) {
            return AdminFieldKind.RELATION
        }

        return when {
            attribute.javaType == ByteArray::class.java -> AdminFieldKind.BINARY
            attribute.javaType == java.lang.Boolean::class.java || attribute.javaType == Boolean::class.javaPrimitiveType -> AdminFieldKind.BOOLEAN
            Number::class.java.isAssignableFrom(attribute.javaType) -> AdminFieldKind.NUMBER
            attribute.javaType.isEnum -> AdminFieldKind.ENUM
            Map::class.java.isAssignableFrom(attribute.javaType) -> AdminFieldKind.JSON
            else -> AdminFieldKind.TEXT
        }
    }

    private fun isPasswordField(entityType: EntityType<*>, attribute: Attribute<*, *>): Boolean {
        return entityType.javaType == AppUser::class.java && attribute.name == "passwordHash"
    }

    private fun sortableIdentifier(value: Any?): String {
        return when (value) {
            null -> ""
            is Number -> value.toLong().toString().padStart(20, '0')
            else -> value.toString()
        }
    }

    private fun requireRelationAttribute(attribute: Attribute<*, *>, fieldName: String) {
        if (!attribute.isAssociation) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Field '$fieldName' is not a relation")
        }
    }

    private fun displayValue(entity: Any): String {
        val wrapper = BeanWrapperImpl(entity)
        val candidates = listOf("name", "question", "title", "username")
        for (candidate in candidates) {
            val value = runCatching { wrapper.getPropertyValue(candidate) as? String }.getOrNull()
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        val id = persistenceUnitUtil.getIdentifier(entity)
        return "${labelize(tableName(entityManager.metamodel.entity(entity.javaClass)))} #$id"
    }

    private fun fieldFor(entityType: EntityType<*>, fieldName: String): java.lang.reflect.Field? {
        return runCatching {
            entityType.javaType.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun tableName(entityType: EntityType<*>): String {
        return entityType.javaType.getAnnotation(Table::class.java)?.name
            ?.takeIf { it.isNotBlank() }
            ?: entityType.name
    }

    private fun labelize(value: String): String {
        return value
            .replace('_', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
