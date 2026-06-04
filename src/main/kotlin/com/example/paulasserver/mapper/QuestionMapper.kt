package com.example.paulasserver.mapper

import com.example.paulasserver.dto.QuestionDto
import com.example.paulasserver.entities.Question

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