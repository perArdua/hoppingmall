package com.hoppingmall.product.category.service

import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.dto.response.CategoryResponse
import com.hoppingmall.cache.NotFoundMarker
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryQueryServiceImpl(
    private val categoryRepository: CategoryRepository,
    private val cacheManager: CacheManager
) : CategoryQueryService {

    @Cacheable(cacheNames = ["category"], key = "#categoryId", sync = true)
    override fun getCategory(categoryId: Long): CategoryResponse? {
        val notFoundCache = cacheManager.getCache("category:notfound")
        val cached = notFoundCache?.get(categoryId)
        if (cached != null && NotFoundMarker.isNotFound(cached.get())) {
            return null
        }

        val category = categoryRepository.findNullableById(categoryId)
        if (category == null) {
            notFoundCache?.put(categoryId, NotFoundMarker.INSTANCE)
            return null
        }

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
