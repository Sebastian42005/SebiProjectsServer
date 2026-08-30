package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.QuestionDto
import com.example.sebisprojectsserver.dto.UpdateQuestionRequest
import com.example.sebisprojectsserver.dto.UpdateUserKnowsRequest
import com.example.sebisprojectsserver.entities.Question
import com.example.sebisprojectsserver.mapper.QuestionMapper
import com.example.sebisprojectsserver.service.QuestionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
    private val questionMapper: QuestionMapper = QuestionMapper()
) {

    @PostMapping("/project/{projectId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun createQuestions(
        @PathVariable projectId: Long,
        @RequestBody questions: List<Question>
    ): List<QuestionDto> {
        return questionService.createQuestions(questions, projectId).map { questionMapper.toDto(it) }
    }

    @GetMapping("/{questionId}")
    fun getQuestion(
        @PathVariable questionId: Long
    ): QuestionDto {
        return questionMapper.toDto(questionService.getQuestion(questionId))
    }

    @PatchMapping("/{questionId}/user-knows")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateQuestionUserKnows(
        @PathVariable questionId: Long,
        @RequestBody body: UpdateUserKnowsRequest
    ) {
        questionService.updateQuestionUserKnows(questionId, body.userKnows)
    }

    @DeleteMapping("/{questionId}")
    fun deleteProject(@PathVariable questionId: Long) {
        return questionService.deleteQuestion(questionId)
    }

    @PatchMapping("/{questionId}")
    fun updateQuestion(
        @PathVariable questionId: Long,
        @RequestBody body: UpdateQuestionRequest
    ): QuestionDto {
        return questionMapper.toDto(questionService.updateQuestion(questionId, body))
    }

    @PutMapping("/{questionId}/image")
    fun setQuestionImage(@RequestParam("file") file: MultipartFile, @PathVariable questionId: Long): ResponseEntity<QuestionDto> {
        return ResponseEntity.ok(questionMapper.toDto(questionService.setQuestionImage(questionId, file)))
    }

    @GetMapping("/{questionId}/image")
    fun getQuestionImage(@PathVariable questionId: Long): ResponseEntity<ByteArray> {
        return questionService.getQuestionImage(questionId)
    }
}
