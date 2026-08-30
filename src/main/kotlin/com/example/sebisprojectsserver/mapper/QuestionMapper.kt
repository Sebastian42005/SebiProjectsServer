package com.example.sebisprojectsserver.mapper

import com.example.sebisprojectsserver.dto.QuestionDto
import com.example.sebisprojectsserver.entities.Question

class QuestionMapper {
    fun toDto(question: Question): QuestionDto {
        return QuestionDto(
            id = question.id,
            question = question.question,
            category = question.category,
            answer = question.answer,
            userKnows = question.userKnows,
            hasImage = question.contentType != null,
        )
    }
}