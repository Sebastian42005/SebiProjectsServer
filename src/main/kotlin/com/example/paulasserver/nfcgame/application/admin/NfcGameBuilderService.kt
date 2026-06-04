package com.example.paulasserver.nfcgame.application.admin

import com.example.paulasserver.nfcgame.api.dto.CardAssignRequest
import com.example.paulasserver.nfcgame.api.dto.FlowEdgeResponse
import com.example.paulasserver.nfcgame.api.dto.FlowNodeResponse
import com.example.paulasserver.nfcgame.api.dto.FlowValidationIssue
import com.example.paulasserver.nfcgame.api.dto.FlowValidationResponse
import com.example.paulasserver.nfcgame.api.dto.GameBasicRequest
import com.example.paulasserver.nfcgame.api.dto.GameFlowRequest
import com.example.paulasserver.nfcgame.api.dto.GameFlowResponse
import com.example.paulasserver.nfcgame.api.dto.GameTemplateResponse
import com.example.paulasserver.nfcgame.application.NfcGameMapper
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.GamePublicationStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowEdge
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowNode
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowEdgeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowNodeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameTemplateRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class NfcGameBuilderService(
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val cardRepository: NfcCardRepository,
    private val nodeRepository: NfcFlowNodeRepository,
    private val edgeRepository: NfcFlowEdgeRepository,
    private val mapper: NfcGameMapper,
    private val adminService: NfcAdminService,
    private val objectMapper: ObjectMapper,
) {
    fun listGames(): List<GameTemplateResponse> =
        gameTemplateRepository.findAllByAccountIdOrderByUpdatedAtDesc(adminService.currentAccountId())
            .filter { it.active }
            .map(::toGameResponse)

    fun getGame(id: UUID): GameTemplateResponse =
        toGameResponse(ownedGame(id))

    @Transactional
    fun setGameImage(id: UUID, file: MultipartFile): GameTemplateResponse {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty")
        }
        val contentType = file.contentType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Image content type is required")
        if (!contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are supported")
        }
        val game = ownedGame(id)
        game.imageContent = file.bytes
        game.imageContentType = contentType
        game.imageFileName = file.originalFilename ?: file.name
        game.imageUrl = null
        return toGameResponse(gameTemplateRepository.save(game))
    }

    fun getGameImage(id: UUID): ResponseEntity<ByteArray> {
        val game = ownedGame(id)
        val content = game.imageContent ?: throw notFound("Game image not found")
        val contentType = game.imageContentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.valueOf(contentType))
            .body(content)
    }

    @Transactional
    fun createGame(request: GameBasicRequest): GameTemplateResponse {
        val game = gameTemplateRepository.save(
            NfcGameTemplate().applyBasicRequest(request).apply {
                accountId = adminService.currentAccountId()
            },
        )
        assignCardIfPresent(request.cardUid, requireNotNull(game.id))
        return toGameResponse(gameTemplateRepository.findById(requireNotNull(game.id)).orElseThrow())
    }

    @Transactional
    fun updateGame(id: UUID, request: GameBasicRequest): GameTemplateResponse {
        val game = ownedGame(id)
        game.applyBasicRequest(request)
        assignCardIfPresent(request.cardUid, id)
        return toGameResponse(gameTemplateRepository.save(game))
    }

    @Transactional
    fun deleteGame(id: UUID) {
        val game = ownedGame(id)
        game.active = false
        game.publicationStatus = GamePublicationStatus.DRAFT
        gameTemplateRepository.save(game)
    }

    @Transactional
    fun duplicateGame(id: UUID): GameTemplateResponse {
        val source = ownedGame(id)
        val copy = gameTemplateRepository.save(
            NfcGameTemplate().apply {
                name = "${source.name} Kopie"
                description = source.description
                imageUrl = source.imageUrl
                imageContent = source.imageContent
                imageContentType = source.imageContentType
                imageFileName = source.imageFileName
                active = true
                publicationStatus = GamePublicationStatus.DRAFT
                flowVersion = 1
                accountId = source.accountId
                dashboardMetricSource = source.dashboardMetricSource
                dashboardMetricLabel = source.dashboardMetricLabel
                dashboardMetricSuffix = source.dashboardMetricSuffix
                dashboardMetricSortDirection = source.dashboardMetricSortDirection
            },
        )
        val copyId = requireNotNull(copy.id)
        val sourceNodes = nodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(id)
        val copiedNodes = sourceNodes.map { sourceNode ->
            NfcFlowNode().apply {
                gameTemplateId = copyId
                type = sourceNode.type
                title = sourceNode.title
                x = sourceNode.x + 40
                y = sourceNode.y + 40
                configJson = sourceNode.configJson
                uiConfigJson = sourceNode.uiConfigJson
                sortOrder = sourceNode.sortOrder
            }
        }
        val savedNodes = nodeRepository.saveAll(copiedNodes)
        val idMap = sourceNodes.zip(savedNodes).associate { (sourceNode, savedNode) ->
            requireNotNull(sourceNode.id) to requireNotNull(savedNode.id)
        }
        edgeRepository.saveAll(
            edgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(id).mapNotNull { sourceEdge ->
                val newSource = idMap[sourceEdge.sourceNodeId] ?: return@mapNotNull null
                val newTarget = idMap[sourceEdge.targetNodeId] ?: return@mapNotNull null
                NfcFlowEdge().apply {
                    gameTemplateId = copyId
                    sourceNodeId = newSource
                    targetNodeId = newTarget
                    eventType = sourceEdge.eventType
                    conditionType = sourceEdge.conditionType
                    conditionConfigJson = sourceEdge.conditionConfigJson
                    priority = sourceEdge.priority
                }
            },
        )
        copy.startNodeId = source.startNodeId?.let { idMap[it] }
        return toGameResponse(gameTemplateRepository.save(copy))
    }

    fun getFlow(gameId: UUID): GameFlowResponse {
        ownedGame(gameId)
        return toFlowResponse(
            gameId,
            gameTemplateRepository.findById(gameId).orElseThrow().startNodeId,
            nodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameId),
            edgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameId),
        )
    }

    @Transactional
    fun saveFlow(gameId: UUID, request: GameFlowRequest): GameFlowResponse {
        val game = ownedGame(gameId)
        val validation = validateRequest(request)
        if (validation.issues.any { it.severity == "ERROR" }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, validation.issues.joinToString("; ") { it.message })
        }

        edgeRepository.deleteAllByGameTemplateId(gameId)
        nodeRepository.deleteAllByGameTemplateId(gameId)

        val savedNodes = nodeRepository.saveAll(
            request.nodes.map { node ->
                NfcFlowNode().apply {
                    gameTemplateId = gameId
                    type = node.type
                    title = node.title
                    x = node.x
                    y = node.y
                    configJson = objectMapper.writeValueAsString(node.config)
                    uiConfigJson = objectMapper.writeValueAsString(node.uiConfig)
                    sortOrder = node.order
                }
            },
        )
        val clientToPersistedNodeIds = request.nodes.zip(savedNodes)
            .mapNotNull { (requestNode, savedNode) -> requestNode.id?.let { it to requireNotNull(savedNode.id) } }
            .toMap()
        val persistedNodeIds = savedNodes.mapNotNull { it.id }.toSet()
        edgeRepository.saveAll(
            request.edges.filter {
                it.sourceNodeId in clientToPersistedNodeIds.keys && it.targetNodeId in clientToPersistedNodeIds.keys
            }.map { edge ->
                NfcFlowEdge().apply {
                    gameTemplateId = gameId
                    sourceNodeId = clientToPersistedNodeIds.getValue(edge.sourceNodeId)
                    targetNodeId = clientToPersistedNodeIds.getValue(edge.targetNodeId)
                    eventType = edge.eventType
                    conditionType = edge.conditionType
                    conditionConfigJson = objectMapper.writeValueAsString(edge.conditionConfig)
                    priority = edge.priority
                }
            },
        )
        game.startNodeId = request.startNodeId?.let { clientToPersistedNodeIds[it] }
            ?: savedNodes.firstOrNull { it.type == "START" }?.id
            ?: persistedNodeIds.firstOrNull()
        game.publicationStatus = GamePublicationStatus.DRAFT
        gameTemplateRepository.save(game)
        return getFlow(gameId)
    }

    fun validateFlow(gameId: UUID): FlowValidationResponse =
        validateGraph(
            ownedGame(gameId).startNodeId,
            nodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameId),
            edgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameId),
        )

    @Transactional
    fun publishGame(gameId: UUID): GameTemplateResponse {
        val game = ownedGame(gameId)
        val validation = validateFlow(gameId)
        if (!validation.valid) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, validation.issues.joinToString("; ") { it.message })
        }
        game.publicationStatus = GamePublicationStatus.PUBLISHED
        game.active = true
        game.flowVersion += 1
        return toGameResponse(gameTemplateRepository.save(game))
    }

    private fun validateRequest(request: GameFlowRequest): FlowValidationResponse {
        val nodes = request.nodes.map {
            NfcFlowNode().apply {
                id = it.id ?: UUID.randomUUID()
                type = it.type
                title = it.title
                x = it.x
                y = it.y
                configJson = objectMapper.writeValueAsString(it.config)
            }
        }
        val nodeIds = nodes.mapNotNull { it.id }.toSet()
        val edges = request.edges.map {
            NfcFlowEdge().apply {
                id = it.id ?: UUID.randomUUID()
                sourceNodeId = it.sourceNodeId
                targetNodeId = it.targetNodeId
                eventType = it.eventType
            }
        }
        return validateGraph(request.startNodeId ?: nodes.firstOrNull { it.type == "START" }?.id, nodes, edges, nodeIds)
    }

    private fun validateGraph(
        startNodeId: UUID?,
        nodes: List<NfcFlowNode>,
        edges: List<NfcFlowEdge>,
        knownNodeIds: Set<UUID> = nodes.mapNotNull { it.id }.toSet(),
    ): FlowValidationResponse {
        val issues = mutableListOf<FlowValidationIssue>()
        val startNodes = nodes.filter { it.type == "START" }
        if (startNodeId == null && startNodes.size != 1) {
            issues += FlowValidationIssue("ERROR", "Exactly one start node or explicit startNodeId is required")
        }
        if (startNodeId != null && startNodeId !in knownNodeIds) {
            issues += FlowValidationIssue("ERROR", "startNodeId does not reference an existing node", nodeId = startNodeId)
        }
        if (nodes.isEmpty()) {
            issues += FlowValidationIssue("ERROR", "Flow needs at least one node")
        }
        edges.forEach { edge ->
            if (edge.sourceNodeId !in knownNodeIds) {
                issues += FlowValidationIssue("ERROR", "Edge source node does not exist", edgeId = edge.id)
            }
            if (edge.targetNodeId !in knownNodeIds) {
                issues += FlowValidationIssue("ERROR", "Edge target node does not exist", edgeId = edge.id)
            }
            if (edge.eventType.isBlank()) {
                issues += FlowValidationIssue("ERROR", "Edge eventType is required", edgeId = edge.id)
            }
        }
        nodes.forEach { node ->
            if (node.title.isBlank()) {
                issues += FlowValidationIssue("ERROR", "Node title is required", nodeId = node.id)
            }
            if (node.type.isBlank()) {
                issues += FlowValidationIssue("ERROR", "Node type is required", nodeId = node.id)
            }
            val config = runCatching { readMap(node.configJson) }.getOrDefault(emptyMap())
            if (node.type in setOf("SHOW_MESSAGE", "MENU", "CONFIRMATION") && config["text"].toString().isBlank()) {
                issues += FlowValidationIssue("WARNING", "${node.type} should define config.text", nodeId = node.id)
            }
        }
        val start = startNodeId ?: startNodes.firstOrNull()?.id
        if (start != null && issues.none { it.severity == "ERROR" && it.nodeId == start }) {
            val reachable = reachableNodes(start, edges)
            nodes.mapNotNull { it.id }.filter { it !in reachable }.forEach {
                issues += FlowValidationIssue("WARNING", "Node is not reachable from start", nodeId = it)
            }
        }
        return FlowValidationResponse(valid = issues.none { it.severity == "ERROR" }, issues = issues)
    }

    private fun reachableNodes(startNodeId: UUID, edges: List<NfcFlowEdge>): Set<UUID> {
        val bySource = edges.groupBy { it.sourceNodeId }
        val seen = mutableSetOf(startNodeId)
        val queue = ArrayDeque<UUID>()
        queue.add(startNodeId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            bySource[current].orEmpty().mapNotNull { it.targetNodeId }.forEach {
                if (seen.add(it)) queue.add(it)
            }
        }
        return seen
    }

    private fun NfcGameTemplate.applyBasicRequest(request: GameBasicRequest) = apply {
        name = request.name.trim()
        description = request.description
        imageUrl = request.imageUrl?.takeIf { it.isNotBlank() }
        if (!imageUrl.isNullOrBlank()) {
            imageContent = null
            imageContentType = null
            imageFileName = null
        }
        active = request.active
        dashboardMetricSource = request.dashboardMetricSource?.takeIf { it.isNotBlank() } ?: "points"
        dashboardMetricLabel = request.dashboardMetricLabel?.takeIf { it.isNotBlank() } ?: "Punkte"
        dashboardMetricSuffix = request.dashboardMetricSuffix?.takeIf { it.isNotBlank() }
        dashboardMetricSortDirection = request.dashboardMetricSortDirection?.takeIf { it.equals("ASC", true) || it.equals("DESC", true) }?.uppercase() ?: "DESC"
        if (publicationStatus != GamePublicationStatus.PUBLISHED) {
            publicationStatus = GamePublicationStatus.DRAFT
        }
    }

    private fun assignCardIfPresent(cardUid: String?, gameTemplateId: UUID) {
        if (cardUid.isNullOrBlank()) return
        adminService.assignCard(
            CardAssignRequest(
                cardUid = cardUid,
                cardType = CardType.GAME,
                playerId = null,
                gameTemplateId = gameTemplateId,
            ),
        )
    }

    private fun toGameResponse(game: NfcGameTemplate): GameTemplateResponse {
        val cardUid = game.id?.let {
            cardRepository.findFirstByGameTemplateIdAndStatus(it, CardStatus.ASSIGNED)?.cardUid
        }
        return mapper.toGameTemplateResponse(game, cardUid)
    }

    private fun toFlowResponse(
        gameId: UUID,
        startNodeId: UUID?,
        nodes: List<NfcFlowNode>,
        edges: List<NfcFlowEdge>,
    ) = GameFlowResponse(
        gameTemplateId = gameId,
        startNodeId = startNodeId,
        nodes = nodes.map {
            FlowNodeResponse(
                id = requireNotNull(it.id),
                type = it.type,
                title = it.title,
                x = it.x,
                y = it.y,
                config = readMap(it.configJson),
                uiConfig = readMap(it.uiConfigJson),
                order = it.sortOrder,
            )
        },
        edges = edges.map {
            FlowEdgeResponse(
                id = requireNotNull(it.id),
                sourceNodeId = requireNotNull(it.sourceNodeId),
                targetNodeId = requireNotNull(it.targetNodeId),
                eventType = it.eventType,
                conditionType = it.conditionType,
                conditionConfig = readMap(it.conditionConfigJson),
                priority = it.priority,
            )
        },
    )

    private fun readMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

    private fun notFound(message: String) = ResponseStatusException(HttpStatus.NOT_FOUND, message)

    private fun ownedGame(id: UUID): NfcGameTemplate =
        try {
            adminService.ownedGameTemplate(id)
        } catch (_: ResponseStatusException) {
            throw notFound("Game not found")
        }
}
