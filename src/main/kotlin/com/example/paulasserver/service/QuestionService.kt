package com.example.paulasserver.service

import com.example.paulasserver.dto.UpdateQuestionRequest
import com.example.paulasserver.entities.Question
import com.example.paulasserver.repositories.ProjectRepository
import com.example.paulasserver.repositories.QuestionRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val projectRepository: ProjectRepository
) {

    fun createQuestions(questions: List<Question>, projectId: Long): List<Question> {
        val project = projectRepository.findById(projectId).orElseThrow()
        questions.forEach { it.project = project }
        return questionRepository.saveAll(questions)
    }

    fun getQuestion(questionId: Long): Question {
        return questionRepository.findById(questionId).orElseThrow()
    }

    fun updateQuestionUserKnows(questionId: Long, userKnows: Boolean) {
        questionRepository.setQuestionUserKnows(questionId, userKnows)
    }

    fun deleteQuestion(questionId: Long) {
        return questionRepository.deleteById(questionId)
    }

    fun updateQuestion(questionId: Long, body: UpdateQuestionRequest): Question {
        val q = questionRepository.findById(questionId).orElseThrow()

        body.question?.let { q.question = it }
        body.answer?.let { q.answer = it }

        return questionRepository.save(q)
    }

    fun setQuestionImage(questionId: Long, file: MultipartFile): Question {
        val q = questionRepository.findById(questionId).orElseThrow()

        q.contentType = file.contentType
        q.content = file.bytes

        return questionRepository.save(q)
    }

    fun getQuestionImage(questionId: Long): ResponseEntity<ByteArray> {
        val question = questionRepository.findById(questionId).orElseThrow()
        if (question.contentType != null) {
            return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.valueOf(question.contentType!!))
                .body(question.content)
        } else {
            throw Exception("Question not found")
        }
    }
}