package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.QuestionDto
import com.example.sebisprojectsserver.entities.Project
import com.example.sebisprojectsserver.mapper.QuestionMapper
import com.example.sebisprojectsserver.service.ProjectService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects")
class ProjectController(
    private val projectService: ProjectService,
    private val questionMapper: QuestionMapper = QuestionMapper(),
) {

    @PostMapping
    fun createProject(@RequestBody project: Project): Project {
        return projectService.createProject(project)
    }

    @PatchMapping("/{id}/resetQuestions")
    fun resetQuestions(@PathVariable id: Long) {
        return projectService.resetQuestions(id)
    }

    @GetMapping
    fun getAllProjects(): List<Project> {
        return projectService.getAllProjects()
    }

    @GetMapping("/{projectId}/questions")
    fun getProjectQuestions(@PathVariable projectId: Long): List<QuestionDto> {
        return projectService.getProjectQuestions(projectId)
            .map { questionMapper.toDto(it) }
            .sortedBy { it.id }
    }

    @DeleteMapping("/{projectId}")
    fun deleteProject(@PathVariable projectId: Long) {
        return projectService.deleteProject(projectId)
    }

    @PatchMapping("/{projectId}/auto-assign-categories")
    fun autoAssignCategories(@PathVariable projectId: Long) {
        return projectService.autoAssignCategories(projectId)
    }
}
