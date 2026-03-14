package com.hoppingmall.mall.category.service

import com.hoppingmall.mall.category.domain.Category
import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.dto.request.CategoryCreateRequest
import com.hoppingmall.mall.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.mall.category.dto.response.CategoryResponse
import com.hoppingmall.mall.category.exception.CategoryAlreadyExistsException
import com.hoppingmall.mall.category.exception.CategoryCircularReferenceException
import com.hoppingmall.mall.category.exception.CategoryHasChildrenException
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryCommandServiceImpl(
    private val categoryRepository: CategoryRepository
) : CategoryCommandService {

    companion object {
        private const val MAX_DEPTH = 10
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["categories:root"], allEntries = true),
        CacheEvict(cacheNames = ["categories:sub"], allEntries = true)
    ])
    override fun createCategory(request: CategoryCreateRequest): CategoryResponse {
        if (categoryRepository.existsByName(request.name)) {
            throw CategoryAlreadyExistsException()
        }

        val depth = if (request.parentCategoryId != null) {
            val parent = categoryRepository.findNullableById(request.parentCategoryId)
                ?: throw CategoryNotFoundException()
            validateNoCircularReference(request.parentCategoryId)
            parent.depth + 1
        } else {
            0
        }

        val category = Category.create(
            name = request.name,
            parentCategoryId = request.parentCategoryId,
            depth = depth
        )
        val savedCategory = categoryRepository.save(category)
        return CategoryResponse.from(savedCategory)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["category"], key = "#categoryId"),
        CacheEvict(cacheNames = ["categories:root"], allEntries = true),
        CacheEvict(cacheNames = ["categories:sub"], allEntries = true)
    ])
    override fun updateCategory(categoryId: Long, request: CategoryUpdateRequest): CategoryResponse {
        val category = categoryRepository.findNullableById(categoryId)
            ?: throw CategoryNotFoundException()

        if (categoryRepository.existsByNameAndIdNot(request.name, categoryId)) {
            throw CategoryAlreadyExistsException()
        }

        category.update(request.name)
        return CategoryResponse.from(category)
    }

    @Caching(evict = [
        CacheEvict(cacheNames = ["category"], key = "#categoryId"),
        CacheEvict(cacheNames = ["categories:root"], allEntries = true),
        CacheEvict(cacheNames = ["categories:sub"], allEntries = true)
    ])
    override fun deleteCategory(categoryId: Long) {
        val category = categoryRepository.findNullableById(categoryId)
            ?: throw CategoryNotFoundException()

        if (categoryRepository.existsByParentCategoryId(category.id!!)) {
            throw CategoryHasChildrenException()
        }

        category.softDelete()
    }

    private fun validateNoCircularReference(parentCategoryId: Long) {
        val visited = mutableSetOf<Long>()
        var currentId: Long? = parentCategoryId

        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw CategoryCircularReferenceException()
            }
            if (visited.size > MAX_DEPTH) {
                throw CategoryCircularReferenceException()
            }
            val current = categoryRepository.findNullableById(currentId)
            currentId = current?.parentCategoryId
        }
    }
}
