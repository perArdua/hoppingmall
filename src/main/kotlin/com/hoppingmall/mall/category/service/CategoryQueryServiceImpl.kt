package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.dto.response.CategoryResponse
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryQueryServiceImpl(
    private val categoryRepository: CategoryRepository
) : CategoryQueryService {

    override fun getCategory(categoryId: Long): CategoryResponse {
        val category = categoryRepository.findNullableById(categoryId)
            ?: throw CategoryNotFoundException()

        return CategoryResponse.from(category)
    }

    override fun getRootCategories(): List<CategoryResponse> {
        return categoryRepository.findByParentCategoryIdIsNull()
            .map { CategoryResponse.from(it) }
    }

    override fun getSubCategories(parentCategoryId: Long): List<CategoryResponse> {
        return categoryRepository.findByParentCategoryId(parentCategoryId)
            .map { CategoryResponse.from(it) }
    }
}
