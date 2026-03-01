package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.dto.response.CategoryResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryQueryServiceImpl(
    private val categoryRepository: CategoryRepository
) : CategoryQueryService {

    @Cacheable(cacheNames = ["category"], key = "#categoryId", sync = true)
    override fun getCategory(categoryId: Long): CategoryResponse? {
        val category = categoryRepository.findNullableById(categoryId)
            ?: return null

        return CategoryResponse.from(category)
    }

    @Cacheable(cacheNames = ["categories:root"], sync = true)
    override fun getRootCategories(): List<CategoryResponse> {
        return categoryRepository.findByParentCategoryIdIsNull()
            .map { CategoryResponse.from(it) }
    }

    @Cacheable(cacheNames = ["categories:sub"], key = "#parentCategoryId", sync = true)
    override fun getSubCategories(parentCategoryId: Long): List<CategoryResponse> {
        return categoryRepository.findByParentCategoryId(parentCategoryId)
            .map { CategoryResponse.from(it) }
    }
}
