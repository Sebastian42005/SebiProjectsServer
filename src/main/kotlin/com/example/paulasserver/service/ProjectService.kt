package com.example.paulasserver.service

import com.example.paulasserver.entities.Project
import com.example.paulasserver.entities.Question
import com.example.paulasserver.repositories.ProjectRepository
import com.example.paulasserver.repositories.QuestionRepository
import org.springframework.stereotype.Service

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val questionRepository: QuestionRepository
) {

    fun createProject(project: Project): Project {
        return projectRepository.save(project)
    }

    fun getAllProjects(): List<Project> {
        return projectRepository.findAll()
            .sortedBy { it.id }
    }

    fun getProjectQuestions(projectId: Long): List<Question> {
        return projectRepository.findById(projectId).orElseThrow().questions
            .sortedBy { it.id }
    }

    fun deleteProject(projectId: Long) {
        return projectRepository.deleteById(projectId)
    }

    fun resetQuestions(projectId: Long) {
        return projectRepository.resetQuestions(projectId)
    }

    fun autoAssignCategories(projectId: Long) {
        val project = projectRepository.findById(projectId).orElseThrow()
        val questions = ArrayList<Question>()
        var currentCategory: String? = null
        project.questions.sortedBy { it.id }.forEach {
            if (it.answer != null && it.answer!!.isNotEmpty()) {
                it.category = currentCategory
            } else {
                currentCategory = it.question
            }
            questions.add(it)
        }
        questionRepository.saveAll(questions)
    }
}