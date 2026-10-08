package com.acove.blog.article.service.impl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.acove.blog.article.dto.TagVO;
import com.acove.blog.article.entity.Tag;
import com.acove.blog.article.mapper.ArticleTagMapper;
import com.acove.blog.article.mapper.TagMapper;
import com.acove.blog.article.service.AdminTagService;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTagServiceImpl implements AdminTagService {

    /** tag.name 是 VARCHAR(50)，超长直接给 400，避免数据库截断异常变成 500。 */
    private static final int NAME_MAX_LENGTH = 50;

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    public Page<TagVO> listTags(long page, long size, String keyword) {
        long pageSize = PageParam.requireValid(page, size);

        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Tag::getName, keyword.trim());
        }

        Page<Tag> tagPage = tagMapper.selectPage(new Page<>(page, pageSize), wrapper);
        Map<Long, Long> countByTagId = countByTagIds(tagPage.getRecords().stream()
                .map(Tag::getId)
                .collect(Collectors.toCollection(HashSet::new)));

        Page<TagVO> voPage = new Page<>(
                tagPage.getCurrent(),
                tagPage.getSize(),
                tagPage.getTotal());
        voPage.setRecords(tagPage.getRecords().stream()
                .map(tag -> toTagVO(tag, countByTagId.getOrDefault(tag.getId(), 0L)))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public TagVO createTag(String tagName) {
        String name = normalizeName(tagName);
        if (existsName(name, null)) {
            throw new BusinessException("标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);

        // 新标签还没有文章引用，直接返回 0，省一次统计查询。
        return toTagVO(tag, 0L);
    }

    @Override
    public TagVO updateTag(Long tagId, String tagName) {
        if (tagId == null) {
            throw new BusinessException("标签ID不能为空");
        }
        Tag existing = tagMapper.selectById(tagId);
        if (existing == null) {
            throw new BusinessException(404, "标签不存在");
        }

        String name = normalizeName(tagName);
        if (existsName(name, existing.getId())) {
            throw new BusinessException("标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setId(existing.getId());
        tag.setName(name);
        tagMapper.updateById(tag);

        return toTagVO(tagMapper.selectById(existing.getId()), countArticles(existing.getId()));
    }

    @Override
    public void deleteTag(Long tagId) {
        if (tagId == null) {
            throw new BusinessException("标签ID不能为空");
        }
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException(404, "标签不存在");
        }

        // article_tag.tag_id 是 CASCADE 外键，标签被文章引用时也能删，
        // 数据库会一并清掉这些文章上的该标签关联（前端删除前会提示影响面）。
        tagMapper.deleteById(tagId);
    }

    /**
     * 统一校验并整理标签名：去空格、非空、不超过列长度。
     */
    private String normalizeName(String tagName) {
        if (!StringUtils.hasText(tagName)) {
            throw new BusinessException("标签名称不能为空");
        }
        String name = tagName.trim();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BusinessException("标签名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return name;
    }

    /**
     * 判断标签名是否已被其它标签占用，excludeId 用于更新时排除自身。
     * 表使用 utf8mb4_unicode_ci 排序规则，这里能顺带挡住大小写不同的重名。
     */
    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name);
        if (excludeId != null) {
            wrapper.ne(Tag::getId, excludeId);
        }
        return tagMapper.selectCount(wrapper) > 0;
    }

    /**
     * 一次查出这批标签各自的引用文章数，避免逐条 count。
     */
    private Map<Long, Long> countByTagIds(Collection<Long> tagIds) {
        Set<Long> ids = tagIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : articleTagMapper.countArticlesByTagIds(ids)) {
            Object tagId = row.get("tag_id");
            Object articleCount = row.get("article_count");
            if (tagId instanceof Number && articleCount instanceof Number) {
                counts.put(
                        ((Number) tagId).longValue(),
                        ((Number) articleCount).longValue());
            }
        }
        return counts;
    }

    private Long countArticles(Long tagId) {
        return countByTagIds(List.of(tagId)).getOrDefault(tagId, 0L);
    }

    private TagVO toTagVO(Tag tag, Long articleCount) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setArticleCount(articleCount);
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }
}
